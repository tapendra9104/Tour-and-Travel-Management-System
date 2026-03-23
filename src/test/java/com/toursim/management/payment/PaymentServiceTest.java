package com.toursim.management.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.AppUserService;
import com.toursim.management.auth.UserRole;
import com.toursim.management.booking.Booking;
import com.toursim.management.booking.BookingActivityService;
import com.toursim.management.booking.BookingRepository;
import com.toursim.management.booking.BookingStatus;
import com.toursim.management.notification.NotificationService;
import com.toursim.management.payment.dto.AdminRefundRequest;
import com.toursim.management.payment.dto.BookingPaymentRequest;
import com.toursim.management.tour.Tour;
import com.toursim.management.tour.TourCatalogService;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingActivityService bookingActivityService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AppUserService appUserService;

    @Mock
    private TourCatalogService tourCatalogService;

    @Mock
    private PaymentGateway paymentGateway;

    private PaymentService paymentService;
    private final List<PaymentTransaction> ledger = new ArrayList<>();
    private final AtomicLong paymentIds = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
            paymentTransactionRepository,
            bookingRepository,
            paymentGateway,
            bookingActivityService,
            notificationService,
            appUserService,
            tourCatalogService,
            "USD",
            new BigDecimal("0.30"),
            45,
            21
        );

        when(paymentTransactionRepository.findAllByBookingIdOrderByCreatedAtDesc(any())).thenAnswer(invocation -> {
            Long bookingId = invocation.getArgument(0);
            return ledger.stream()
                .filter(transaction -> transaction.getBookingId().equals(bookingId))
                .sorted(java.util.Comparator.comparing(PaymentTransaction::getCreatedAt).reversed())
                .toList();
        });
        lenient().when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            PaymentTransaction transaction = invocation.getArgument(0);
            if (transaction.getId() == null) {
                transaction.setId(paymentIds.getAndIncrement());
            }
            if (transaction.getCreatedAt() == null) {
                transaction.setCreatedAt(LocalDateTime.now());
            }
            if (transaction.getTransactionReference() == null || transaction.getTransactionReference().isBlank()) {
                transaction.setTransactionReference("PAY-TEST-" + transaction.getId());
            }
            if (transaction.getReceiptNumber() == null || transaction.getReceiptNumber().isBlank()) {
                transaction.setReceiptNumber("RCT-TEST-" + transaction.getId());
            }
            ledger.removeIf(existing -> existing.getId().equals(transaction.getId()));
            ledger.add(transaction);
            return transaction;
        });
    }

    @Test
    void summarizeShowsDepositDueForFutureDeparture() {
        Booking booking = booking("BK-1001", BookingStatus.PENDING, LocalDate.now().plusDays(90), new BigDecimal("1050.00"));

        PaymentSummary summary = paymentService.summarize(booking);

        assertThat(summary.status()).isEqualTo(PaymentStatus.DEPOSIT_DUE);
        assertThat(summary.depositAmount()).isEqualByComparingTo("315.00");
        assertThat(summary.outstandingAmount()).isEqualByComparingTo("1050.00");
        assertThat(summary.dueNowAmount()).isEqualByComparingTo("315.00");
        assertThat(summary.nextStage()).isEqualTo(PaymentStage.DEPOSIT);
    }

    @Test
    void collectTravelerPaymentCapturesDepositAndLeavesBalanceDue() {
        Booking booking = booking("BK-1002", BookingStatus.PENDING, LocalDate.now().plusDays(70), new BigDecimal("1050.00"));
        Tour tour = tour("1", "Swiss Escape");
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(paymentGateway.charge(any())).thenReturn(new PaymentGatewayResult("Test Gateway", "AUTH-1", "RCT-1002"));
        when(tourCatalogService.findById("1")).thenReturn(Optional.of(tour));

        PaymentActionResult result = paymentService.collectTravelerPayment(
            booking.getId(),
            new BookingPaymentRequest(booking.getBookingReference(), booking.getEmail(), "upi", new BigDecimal("315.00"), "Deposit payment"),
            Optional.empty()
        );

        assertThat(result.transaction().getStage()).isEqualTo(PaymentStage.DEPOSIT);
        assertThat(result.transaction().getMethod()).isEqualTo(PaymentMethod.UPI);
        assertThat(result.summary().status()).isEqualTo(PaymentStatus.BALANCE_DUE);
        assertThat(result.summary().paidAmount()).isEqualByComparingTo("315.00");
        assertThat(result.summary().outstandingAmount()).isEqualByComparingTo("735.00");
        assertThat(result.message()).contains("Remaining balance");
        verify(notificationService).notifyAdmins(any(), any(), any(), any(), any());
    }

    @Test
    void collectTravelerPaymentAcceptsExpandedDailyLifePaymentMethods() {
        Booking booking = booking("BK-1004", BookingStatus.PENDING, LocalDate.now().plusDays(70), new BigDecimal("1050.00"));
        Tour tour = tour("1", "City Escape");
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(paymentGateway.charge(any())).thenReturn(new PaymentGatewayResult("Test Gateway", "AUTH-2", "GPAY-1004"));
        when(tourCatalogService.findById("1")).thenReturn(Optional.of(tour));

        PaymentActionResult result = paymentService.collectTravelerPayment(
            booking.getId(),
            new BookingPaymentRequest(booking.getBookingReference(), booking.getEmail(), "google pay", new BigDecimal("315.00"), "Deposit via Google Pay"),
            Optional.empty()
        );

        assertThat(result.transaction().getMethod()).isEqualTo(PaymentMethod.GOOGLE_PAY);
        assertThat(result.summary().status()).isEqualTo(PaymentStatus.BALANCE_DUE);
    }

    @Test
    void refundPaymentProcessesRemainingRefundForCancelledBooking() {
        Booking booking = booking("BK-1003", BookingStatus.CANCELLED, LocalDate.now().plusDays(45), new BigDecimal("1000.00"));
        Tour tour = tour("1", "Italian Coast");
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(tourCatalogService.findById("1")).thenReturn(Optional.of(tour));
        when(paymentGateway.refund(any())).thenReturn(new PaymentGatewayResult("Test Gateway", "RFD-1", "RFD-1003"));

        PaymentTransaction payment = new PaymentTransaction();
        payment.setBookingId(booking.getId());
        payment.setStage(PaymentStage.FULL);
        payment.setMethod(PaymentMethod.CARD);
        payment.setAmount(new BigDecimal("1000.00"));
        payment.setCurrency("USD");
        payment.setProviderName("Test Gateway");
        payment.setProviderReference("AUTH-1003");
        payment.setReceiptNumber("RCT-1003");
        payment.setActorName("Traveler");
        payment.setActorRole("GUEST");
        payment.setCreatedAt(LocalDateTime.now().minusDays(1));
        paymentTransactionRepository.save(payment);

        AppUser admin = new AppUser();
        admin.setId(10L);
        admin.setEmail("admin@wanderlust.com");
        admin.setFullName("Admin User");
        admin.setRole(UserRole.ADMIN);

        PaymentActionResult result = paymentService.refundPayment(
            booking.getId(),
            new AdminRefundRequest(null, "Refund approved"),
            admin
        );

        assertThat(result.transaction().getStage()).isEqualTo(PaymentStage.REFUND);
        assertThat(result.summary().status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(result.summary().refundedAmount()).isEqualByComparingTo("900.00");
        assertThat(result.summary().refundableAmount()).isEqualByComparingTo("0.00");
        assertThat(result.message()).contains("Refund processed");
    }

    private Booking booking(String reference, BookingStatus status, LocalDate date, BigDecimal totalPrice) {
        Booking booking = new Booking();
        booking.setId(1L);
        booking.setBookingReference(reference);
        booking.setTourId("1");
        booking.setCustomerName("Test Traveler");
        booking.setEmail("traveler@example.com");
        booking.setPhone("+123456789");
        booking.setGuests(2);
        booking.setDate(date);
        booking.setCreatedAt(LocalDateTime.now());
        booking.setTotalPrice(totalPrice);
        booking.setServiceFee(new BigDecimal("50.00"));
        booking.setStatus(status);
        booking.setStatusReason("Test status");
        return booking;
    }

    private Tour tour(String id, String title) {
        Tour tour = new Tour();
        tour.setId(id);
        tour.setTitle(title);
        return tour;
    }
}
