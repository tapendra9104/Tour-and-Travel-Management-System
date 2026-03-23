package com.toursim.management.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.toursim.management.auth.AppUserService;
import com.toursim.management.booking.Booking;
import com.toursim.management.booking.BookingService;
import com.toursim.management.booking.BookingStatus;
import com.toursim.management.payment.PaymentService;
import com.toursim.management.payment.PaymentStage;
import com.toursim.management.payment.PaymentStatus;
import com.toursim.management.payment.PaymentSummary;
import com.toursim.management.tour.Tour;
import com.toursim.management.tour.TourCatalogService;

@ExtendWith(MockitoExtension.class)
class NotificationAutomationServiceTest {

    @Mock
    private BookingService bookingService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private TourCatalogService tourCatalogService;

    @Mock
    private AppUserService appUserService;

    private NotificationAutomationService notificationAutomationService;

    @BeforeEach
    void setUp() {
        notificationAutomationService = new NotificationAutomationService(
            bookingService,
            paymentService,
            notificationService,
            tourCatalogService,
            appUserService,
            2
        );
    }

    @Test
    void processAutomatedNotificationsSendsPaymentDueAndTripReminderEmails() {
        Booking booking = booking("BK-1001", BookingStatus.CONFIRMED, LocalDate.now().plusDays(1));
        Tour tour = tour("1", "Majestic Swiss Alps Adventure");
        PaymentSummary paymentSummary = new PaymentSummary(
            "USD",
            PaymentStatus.BALANCE_DUE,
            PaymentStage.BALANCE,
            new BigDecimal("2623.95"),
            new BigDecimal("1000.00"),
            BigDecimal.ZERO,
            new BigDecimal("1000.00"),
            new BigDecimal("1623.95"),
            new BigDecimal("787.19"),
            BigDecimal.ZERO,
            new BigDecimal("1623.95"),
            new BigDecimal("1623.95"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            LocalDate.now().plusDays(1),
            false,
            null,
            null,
            null,
            null
        );

        when(bookingService.findAll()).thenReturn(List.of(booking));
        when(paymentService.summarize(List.of(booking))).thenReturn(Map.of(booking.getId(), paymentSummary));
        when(tourCatalogService.findById("1")).thenReturn(Optional.of(tour));
        when(notificationService.hasRecentBookingEmail(eq(NotificationCategory.PAYMENT_DUE), eq(booking.getId()), eq(booking.getEmail()), any())).thenReturn(false);
        when(notificationService.hasRecentBookingEmail(eq(NotificationCategory.TRIP_REMINDER), eq(booking.getId()), eq(booking.getEmail()), any())).thenReturn(false);

        notificationAutomationService.processAutomatedNotifications();

        verify(notificationService).notifyGuest(
            eq("traveler@example.com"),
            eq("Traveler One"),
            eq(NotificationCategory.PAYMENT_DUE),
            eq("Payment due for Majestic Swiss Alps Adventure"),
            org.mockito.ArgumentMatchers.contains("outstanding amount"),
            eq(booking.getId()),
            isNull()
        );
        verify(notificationService).notifyGuest(
            eq("traveler@example.com"),
            eq("Traveler One"),
            eq(NotificationCategory.TRIP_REMINDER),
            eq("Trip reminder for Majestic Swiss Alps Adventure"),
            org.mockito.ArgumentMatchers.contains("departs on"),
            eq(booking.getId()),
            isNull()
        );
    }

    @Test
    void processAutomatedNotificationsSkipsDuplicateDailyEmails() {
        Booking booking = booking("BK-1002", BookingStatus.CONFIRMED, LocalDate.now().plusDays(3));
        Tour tour = tour("1", "Majestic Swiss Alps Adventure");
        PaymentSummary paymentSummary = new PaymentSummary(
            "USD",
            PaymentStatus.DEPOSIT_DUE,
            PaymentStage.DEPOSIT,
            new BigDecimal("2623.95"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("2623.95"),
            new BigDecimal("787.19"),
            new BigDecimal("787.19"),
            new BigDecimal("1836.76"),
            new BigDecimal("787.19"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            LocalDate.now().plusDays(1),
            false,
            null,
            null,
            null,
            null
        );

        when(bookingService.findAll()).thenReturn(List.of(booking));
        when(paymentService.summarize(List.of(booking))).thenReturn(Map.of(booking.getId(), paymentSummary));
        when(tourCatalogService.findById("1")).thenReturn(Optional.of(tour));
        when(notificationService.hasRecentBookingEmail(eq(NotificationCategory.PAYMENT_DUE), eq(booking.getId()), eq(booking.getEmail()), any())).thenReturn(true);
        when(notificationService.hasRecentBookingEmail(eq(NotificationCategory.TRIP_REMINDER), eq(booking.getId()), eq(booking.getEmail()), any())).thenReturn(true);

        notificationAutomationService.processAutomatedNotifications();

        verify(notificationService, never()).notifyGuest(
            eq("traveler@example.com"),
            eq("Traveler One"),
            any(),
            any(),
            any(),
            eq(booking.getId()),
            isNull()
        );
    }

    private Booking booking(String reference, BookingStatus status, LocalDate travelDate) {
        Booking booking = new Booking();
        booking.setId(101L);
        booking.setTourId("1");
        booking.setBookingReference(reference);
        booking.setCustomerName("Traveler One");
        booking.setEmail("traveler@example.com");
        booking.setStatus(status);
        booking.setDate(travelDate);
        return booking;
    }

    private Tour tour(String id, String title) {
        Tour tour = new Tour();
        tour.setId(id);
        tour.setTitle(title);
        return tour;
    }
}
