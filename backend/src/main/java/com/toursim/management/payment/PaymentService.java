package com.toursim.management.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.AppUserService;
import com.toursim.management.auth.UserRole;
import com.toursim.management.booking.Booking;
import com.toursim.management.booking.BookingActivityService;
import com.toursim.management.booking.BookingRepository;
import com.toursim.management.booking.BookingStatus;
import com.toursim.management.notification.NotificationCategory;
import com.toursim.management.notification.NotificationService;
import com.toursim.management.payment.dto.AdminPaymentRequest;
import com.toursim.management.payment.dto.AdminRefundRequest;
import com.toursim.management.payment.dto.BookingPaymentRequest;
import com.toursim.management.tour.Tour;
import com.toursim.management.tour.TourCatalogService;

@Service
public class PaymentService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final BookingRepository bookingRepository;
    private final PaymentGateway paymentGateway;
    private final BookingActivityService bookingActivityService;
    private final NotificationService notificationService;
    private final AppUserService appUserService;
    private final TourCatalogService tourCatalogService;
    private final String currency;
    private final BigDecimal depositRate;
    private final int fullPaymentWindowDays;
    private final int balanceDueDaysBeforeDeparture;

    public PaymentService(
        PaymentTransactionRepository paymentTransactionRepository,
        BookingRepository bookingRepository,
        PaymentGateway paymentGateway,
        BookingActivityService bookingActivityService,
        NotificationService notificationService,
        AppUserService appUserService,
        TourCatalogService tourCatalogService,
        @Value("${app.payments.currency:USD}") String currency,
        @Value("${app.payments.deposit-rate:0.30}") BigDecimal depositRate,
        @Value("${app.payments.full-payment-window-days:45}") int fullPaymentWindowDays,
        @Value("${app.payments.balance-due-days-before-departure:21}") int balanceDueDaysBeforeDeparture
    ) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.bookingRepository = bookingRepository;
        this.paymentGateway = paymentGateway;
        this.bookingActivityService = bookingActivityService;
        this.notificationService = notificationService;
        this.appUserService = appUserService;
        this.tourCatalogService = tourCatalogService;
        this.currency = currency;
        this.depositRate = depositRate;
        this.fullPaymentWindowDays = fullPaymentWindowDays;
        this.balanceDueDaysBeforeDeparture = balanceDueDaysBeforeDeparture;
    }

    @Transactional(readOnly = true)
    public PaymentSummary summarize(Booking booking) {
        return buildSummary(booking, paymentTransactionRepository.findAllByBookingIdOrderByCreatedAtDesc(booking.getId()));
    }

    @Transactional(readOnly = true)
    public Map<Long, PaymentSummary> summarize(Collection<Booking> bookings) {
        if (bookings.isEmpty()) {
            return Map.of();
        }

        List<Long> bookingIds = bookings.stream().map(Booking::getId).toList();
        Map<Long, List<PaymentTransaction>> transactionsByBooking = paymentTransactionRepository
            .findAllByBookingIdInOrderByBookingIdAscCreatedAtDesc(bookingIds)
            .stream()
            .collect(Collectors.groupingBy(PaymentTransaction::getBookingId, LinkedHashMap::new, Collectors.toList()));

        return bookings.stream().collect(Collectors.toMap(
            Booking::getId,
            booking -> buildSummary(booking, transactionsByBooking.getOrDefault(booking.getId(), List.of())),
            (left, right) -> left,
            LinkedHashMap::new
        ));
    }

    @Transactional(readOnly = true)
    public BigDecimal totalCollected(Collection<Booking> bookings) {
        return summarize(bookings).values().stream()
            .map(PaymentSummary::netPaidAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<PaymentTransaction> findAllTransactions() {
        return paymentTransactionRepository.findAll().stream()
            .sorted(java.util.Comparator.comparing(PaymentTransaction::getCreatedAt).reversed())
            .toList();
    }

    @Transactional
    public PaymentActionResult collectTravelerPayment(Long bookingId, BookingPaymentRequest request, Optional<AppUser> actor) {
        Booking booking = requireAuthorizedBooking(bookingId, request.bookingReference(), request.email(), actor);
        return collectPayment(
            booking,
            request.amount(),
            parseMethod(request.method()),
            request.note(),
            actor.map(AppUser::getId).orElse(null),
            actor.map(AppUser::getFullName).orElse(booking.getCustomerName()),
            actor.map(appUser -> appUser.getRole().name()).orElse("GUEST")
        );
    }

    @Transactional
    public PaymentActionResult collectAdminPayment(Long bookingId, AdminPaymentRequest request, AppUser actor) {
        requireAdmin(actor);
        Booking booking = requireBooking(bookingId);
        return collectPayment(
            booking,
            request.amount(),
            parseMethod(request.method()),
            request.note(),
            actor.getId(),
            actor.getFullName(),
            actor.getRole().name()
        );
    }

    @Transactional
    public PaymentActionResult refundPayment(Long bookingId, AdminRefundRequest request, AppUser actor) {
        requireAdmin(actor);
        Booking booking = requireBooking(bookingId);
        PaymentSummary currentSummary = summarize(booking);
        BigDecimal refundableAmount = currentSummary.refundableAmount();
        if (refundableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No refundable balance is available for this booking");
        }

        BigDecimal refundAmount = scale(request.amount() == null ? refundableAmount : request.amount());
        if (refundAmount.compareTo(refundableAmount) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount exceeds the refundable balance");
        }

        PaymentMethod method = currentSummary.lastMethod() == null ? PaymentMethod.BANK_TRANSFER : currentSummary.lastMethod();
        PaymentGatewayResult gatewayResult = paymentGateway.refund(new PaymentGatewayRefundRequest(
            booking.getBookingReference(),
            booking.getCustomerName(),
            refundAmount,
            currentSummary.currency(),
            method,
            currentSummary.lastTransactionReference()
        ));

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBookingId(booking.getId());
        transaction.setProviderName(gatewayResult.providerName());
        transaction.setProviderReference(gatewayResult.providerReference());
        transaction.setReceiptNumber(gatewayResult.receiptNumber());
        transaction.setStage(PaymentStage.REFUND);
        transaction.setMethod(method);
        transaction.setAmount(refundAmount);
        transaction.setCurrency(currentSummary.currency());
        transaction.setNote(cleanNote(request.note()));
        transaction.setActorUserId(actor.getId());
        transaction.setActorName(actor.getFullName());
        transaction.setActorRole(actor.getRole().name());
        transaction = paymentTransactionRepository.save(transaction);

        PaymentSummary updatedSummary = summarize(booking);
        String activityNote = "Refund processed: " + amountText(refundAmount, updatedSummary.currency())
            + " via " + method.label()
            + ". Receipt " + transaction.getReceiptNumber()
            + (hasText(transaction.getNote()) ? " | " + transaction.getNote() : "");
        bookingActivityService.record(
            booking,
            actor.getId(),
            actor.getFullName(),
            actor.getRole().name(),
            "PAYMENT_REFUNDED",
            booking.getStatus(),
            booking.getStatus(),
            activityNote
        );

        Tour tour = requireTour(booking.getTourId());
        String subject = "Refund update for " + tour.getTitle();
        String message = "Hi " + booking.getCustomerName() + ",\n\nWe processed a refund of "
            + amountText(refundAmount, updatedSummary.currency()) + " for booking "
            + booking.getBookingReference() + ". Receipt: " + transaction.getReceiptNumber() + ".\n\nWanderlust Travels";
        notifyBookingRecipient(booking, NotificationCategory.REFUND_PROCESSED, subject, message);

        return new PaymentActionResult(transaction, updatedSummary, "Refund processed successfully.");
    }

    private PaymentActionResult collectPayment(
        Booking booking,
        BigDecimal requestedAmount,
        PaymentMethod method,
        String note,
        Long actorUserId,
        String actorName,
        String actorRole
    ) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled bookings cannot accept new payments");
        }

        PaymentSummary currentSummary = summarize(booking);
        if (currentSummary.outstandingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking is already fully paid");
        }

        BigDecimal amount = scale(requestedAmount);
        if (amount.compareTo(currentSummary.outstandingAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment amount exceeds the outstanding balance");
        }

        PaymentStage stage = resolveStage(currentSummary, amount);
        PaymentGatewayResult gatewayResult = paymentGateway.charge(new PaymentGatewayChargeRequest(
            booking.getBookingReference(),
            booking.getCustomerName(),
            booking.getEmail(),
            amount,
            currentSummary.currency(),
            method,
            stage
        ));

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setBookingId(booking.getId());
        transaction.setProviderName(gatewayResult.providerName());
        transaction.setProviderReference(gatewayResult.providerReference());
        transaction.setReceiptNumber(gatewayResult.receiptNumber());
        transaction.setStage(stage);
        transaction.setMethod(method);
        transaction.setAmount(amount);
        transaction.setCurrency(currentSummary.currency());
        transaction.setNote(cleanNote(note));
        transaction.setActorUserId(actorUserId);
        transaction.setActorName(actorName);
        transaction.setActorRole(actorRole);
        transaction = paymentTransactionRepository.save(transaction);

        PaymentSummary updatedSummary = summarize(booking);
        String stageLabel = stage.label().toLowerCase();
        String activityNote = stageLabel.substring(0, 1).toUpperCase() + stageLabel.substring(1)
            + " captured: " + amountText(amount, updatedSummary.currency())
            + " via " + method.label()
            + ". Receipt " + transaction.getReceiptNumber()
            + (hasText(transaction.getNote()) ? " | " + transaction.getNote() : "");
        bookingActivityService.record(
            booking,
            actorUserId,
            actorName,
            actorRole,
            "PAYMENT_COLLECTED",
            booking.getStatus(),
            booking.getStatus(),
            activityNote
        );

        Tour tour = requireTour(booking.getTourId());
        String subject = "Payment received for " + tour.getTitle();
        String message = "Hi " + booking.getCustomerName() + ",\n\nWe received "
            + amountText(amount, updatedSummary.currency()) + " for booking "
            + booking.getBookingReference() + " using " + method.label() + ". Receipt: "
            + transaction.getReceiptNumber() + ".";
        if (updatedSummary.outstandingAmount().compareTo(BigDecimal.ZERO) > 0 && updatedSummary.dueDate() != null) {
            message += "\nRemaining balance: " + amountText(updatedSummary.outstandingAmount(), updatedSummary.currency())
                + " due by " + updatedSummary.dueDate() + ".";
        } else {
            message += "\nYour booking is now paid in full.";
        }
        message += "\n\nWanderlust Travels";
        notifyBookingRecipient(booking, NotificationCategory.PAYMENT_RECEIVED, subject, message);

        notificationService.notifyAdmins(
            NotificationCategory.ADMIN_ALERT,
            "Payment received",
            booking.getCustomerName() + " paid " + amountText(amount, updatedSummary.currency())
                + " for booking " + booking.getBookingReference() + " via " + method.label() + ".",
            booking.getId(),
            null
        );

        String responseMessage = updatedSummary.outstandingAmount().compareTo(BigDecimal.ZERO) > 0
            ? "Payment captured. Remaining balance: " + amountText(updatedSummary.outstandingAmount(), updatedSummary.currency()) + "."
            : "Payment captured. The booking is now paid in full.";
        return new PaymentActionResult(transaction, updatedSummary, responseMessage);
    }

    private PaymentSummary buildSummary(Booking booking, List<PaymentTransaction> transactions) {
        PaymentPlan plan = planFor(booking);
        BigDecimal collectedAmount = transactions.stream()
            .filter(transaction -> transaction.getStage() != PaymentStage.REFUND)
            .map(PaymentTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundedAmount = transactions.stream()
            .filter(transaction -> transaction.getStage() == PaymentStage.REFUND)
            .map(PaymentTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netPaidAmount = positive(collectedAmount.subtract(refundedAmount));
        BigDecimal depositOutstandingAmount = positive(plan.depositAmount().subtract(netPaidAmount.min(plan.depositAmount())));
        BigDecimal balanceOutstandingAmount = positive(plan.balanceAmount().subtract(positive(netPaidAmount.subtract(plan.depositAmount()))));
        BigDecimal outstandingAmount;
        BigDecimal dueNowAmount;
        BigDecimal refundableAmount = BigDecimal.ZERO;
        BigDecimal cancellationFeeAmount = BigDecimal.ZERO;
        LocalDate dueDate = null;
        boolean overdue = false;
        PaymentStage nextStage = null;
        PaymentStatus status;

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            cancellationFeeAmount = scale(collectedAmount.multiply(cancellationFeeRate(booking)));
            refundableAmount = positive(collectedAmount.subtract(cancellationFeeAmount).subtract(refundedAmount));
            outstandingAmount = BigDecimal.ZERO;
            dueNowAmount = BigDecimal.ZERO;

            if (collectedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                status = PaymentStatus.CANCELLED;
            } else if (refundableAmount.compareTo(BigDecimal.ZERO) > 0) {
                status = refundedAmount.compareTo(BigDecimal.ZERO) > 0
                    ? PaymentStatus.PARTIALLY_REFUNDED
                    : PaymentStatus.REFUND_DUE;
            } else if (refundedAmount.compareTo(BigDecimal.ZERO) > 0) {
                status = PaymentStatus.REFUNDED;
            } else {
                status = PaymentStatus.CANCELLED;
            }
        } else {
            outstandingAmount = positive(booking.getTotalPrice().subtract(netPaidAmount));
            if (outstandingAmount.compareTo(BigDecimal.ZERO) == 0) {
                dueNowAmount = BigDecimal.ZERO;
                status = refundedAmount.compareTo(BigDecimal.ZERO) > 0
                    ? PaymentStatus.PARTIALLY_REFUNDED
                    : PaymentStatus.PAID_IN_FULL;
            } else {
                if (depositOutstandingAmount.compareTo(BigDecimal.ZERO) > 0) {
                    nextStage = plan.balanceAmount().compareTo(BigDecimal.ZERO) > 0 ? PaymentStage.DEPOSIT : PaymentStage.FULL;
                    dueDate = plan.depositDueDate();
                    dueNowAmount = depositOutstandingAmount;
                } else {
                    nextStage = PaymentStage.BALANCE;
                    dueDate = plan.balanceDueDate();
                    dueNowAmount = balanceOutstandingAmount;
                }

                overdue = dueDate != null && dueNowAmount.compareTo(BigDecimal.ZERO) > 0 && dueDate.isBefore(LocalDate.now());
                if (overdue) {
                    status = PaymentStatus.OVERDUE;
                } else if (plan.balanceAmount().compareTo(BigDecimal.ZERO) > 0 && depositOutstandingAmount.compareTo(BigDecimal.ZERO) > 0) {
                    status = collectedAmount.compareTo(BigDecimal.ZERO) > 0
                        ? PaymentStatus.PARTIALLY_PAID
                        : PaymentStatus.DEPOSIT_DUE;
                } else if (balanceOutstandingAmount.compareTo(BigDecimal.ZERO) > 0) {
                    status = PaymentStatus.BALANCE_DUE;
                } else {
                    status = collectedAmount.compareTo(BigDecimal.ZERO) > 0
                        ? PaymentStatus.PARTIALLY_PAID
                        : PaymentStatus.UNPAID;
                }
            }
        }

        PaymentTransaction latestPaidTransaction = transactions.stream()
            .filter(transaction -> transaction.getStage() != PaymentStage.REFUND)
            .findFirst()
            .orElse(null);

        return new PaymentSummary(
            currency,
            status,
            nextStage,
            scale(booking.getTotalPrice()),
            scale(collectedAmount),
            scale(refundedAmount),
            scale(netPaidAmount),
            scale(outstandingAmount),
            scale(plan.depositAmount()),
            scale(depositOutstandingAmount),
            scale(balanceOutstandingAmount),
            scale(dueNowAmount),
            scale(refundableAmount),
            scale(cancellationFeeAmount),
            dueDate,
            overdue,
            latestPaidTransaction == null ? null : latestPaidTransaction.getMethod(),
            latestPaidTransaction == null ? null : latestPaidTransaction.getReceiptNumber(),
            latestPaidTransaction == null ? null : latestPaidTransaction.getTransactionReference(),
            latestPaidTransaction == null ? null : latestPaidTransaction.getCreatedAt()
        );
    }

    private PaymentPlan planFor(Booking booking) {
        long daysUntilDeparture = ChronoUnit.DAYS.between(LocalDate.now(), booking.getDate());
        if (daysUntilDeparture <= fullPaymentWindowDays) {
            return new PaymentPlan(
                scale(booking.getTotalPrice()),
                BigDecimal.ZERO,
                booking.getCreatedAt().toLocalDate(),
                null
            );
        }

        BigDecimal depositAmount = scale(booking.getTotalPrice().multiply(depositRate));
        BigDecimal balanceAmount = scale(booking.getTotalPrice().subtract(depositAmount));
        LocalDate balanceDueDate = booking.getDate().minusDays(balanceDueDaysBeforeDeparture);
        if (balanceDueDate.isBefore(LocalDate.now())) {
            balanceDueDate = LocalDate.now();
        }

        return new PaymentPlan(
            depositAmount,
            balanceAmount,
            booking.getCreatedAt().toLocalDate(),
            balanceDueDate
        );
    }

    private BigDecimal cancellationFeeRate(Booking booking) {
        long daysUntilDeparture = ChronoUnit.DAYS.between(LocalDate.now(), booking.getDate());
        if (daysUntilDeparture >= 30) {
            return new BigDecimal("0.10");
        }
        if (daysUntilDeparture >= 15) {
            return new BigDecimal("0.25");
        }
        if (daysUntilDeparture >= 7) {
            return new BigDecimal("0.50");
        }
        return BigDecimal.ONE;
    }

    private PaymentStage resolveStage(PaymentSummary summary, BigDecimal amount) {
        if (amount.compareTo(summary.outstandingAmount()) == 0) {
            return PaymentStage.FULL;
        }
        if (summary.depositOutstandingAmount().compareTo(BigDecimal.ZERO) > 0) {
            return PaymentStage.DEPOSIT;
        }
        return PaymentStage.BALANCE;
    }

    private void notifyBookingRecipient(Booking booking, NotificationCategory category, String subject, String message) {
        if (booking.getUserId() != null) {
            Optional<AppUser> appUser = appUserService.findById(booking.getUserId());
            if (appUser.isPresent()) {
                notificationService.notifyUser(appUser.get(), category, subject, message, booking.getId(), null);
                return;
            }
        }

        notificationService.notifyGuest(
            booking.getEmail(),
            booking.getCustomerName(),
            category,
            subject,
            message,
            booking.getId(),
            null
        );
    }

    private Booking requireAuthorizedBooking(Long id, String bookingReference, String email, Optional<AppUser> actor) {
        Booking booking = requireBooking(id);
        if (actor.isPresent()) {
            AppUser appUser = actor.get();
            if (appUser.getRole() == UserRole.ADMIN) {
                return booking;
            }
            boolean ownsBooking = (booking.getUserId() != null && booking.getUserId().equals(appUser.getId()))
                || booking.getEmail().equalsIgnoreCase(appUser.getEmail());
            if (ownsBooking) {
                return booking;
            }
        }

        boolean guestAuthorized = booking.getBookingReference().equalsIgnoreCase(normalizeReference(bookingReference))
            && booking.getEmail().equalsIgnoreCase(normalizeEmail(email));
        if (!guestAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this booking");
        }
        return booking;
    }

    private void requireAdmin(AppUser actor) {
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can manage payment operations");
        }
    }

    private Booking requireBooking(Long id) {
        return bookingRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private Tour requireTour(String tourId) {
        return tourCatalogService.findById(tourId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tour not found"));
    }

    private PaymentMethod parseMethod(String method) {
        String normalized = method == null ? "" : method.trim().toLowerCase();
        return switch (normalized) {
            case "card" -> PaymentMethod.CARD;
            case "credit card", "credit", "visa", "mastercard", "master card", "amex", "american express", "discover" -> PaymentMethod.CREDIT_CARD;
            case "debit card", "debit", "rupay", "maestro" -> PaymentMethod.DEBIT_CARD;
            case "upi" -> PaymentMethod.UPI;
            case "net banking", "netbanking", "online banking", "internet banking" -> PaymentMethod.NET_BANKING;
            case "bank", "bank transfer", "wire", "transfer", "ach", "swift", "neft", "rtgs", "imps" -> PaymentMethod.BANK_TRANSFER;
            case "paypal", "pay pal" -> PaymentMethod.PAYPAL;
            case "google pay", "gpay", "googlepay" -> PaymentMethod.GOOGLE_PAY;
            case "apple pay", "applepay" -> PaymentMethod.APPLE_PAY;
            case "phonepe", "phone pe" -> PaymentMethod.PHONEPE;
            case "paytm", "paytm wallet" -> PaymentMethod.PAYTM;
            case "amazon pay", "amazonpay" -> PaymentMethod.AMAZON_PAY;
            case "venmo" -> PaymentMethod.VENMO;
            case "cash app", "cashapp" -> PaymentMethod.CASH_APP;
            case "zelle" -> PaymentMethod.ZELLE;
            case "wallet", "digital wallet" -> PaymentMethod.WALLET;
            case "emi", "installment", "installments", "monthly installment" -> PaymentMethod.EMI;
            case "bnpl", "buy now pay later", "pay later", "klarna", "afterpay", "affirm" -> PaymentMethod.BNPL;
            case "cash", "cash payment" -> PaymentMethod.CASH;
            case "cheque", "check", "bank cheque" -> PaymentMethod.CHEQUE;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment method");
        };
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String normalizeReference(String bookingReference) {
        return bookingReference == null ? "" : bookingReference.trim();
    }

    private BigDecimal positive(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String amountText(BigDecimal amount, String summaryCurrency) {
        return summaryCurrency + " " + scale(amount).toPlainString();
    }

    private String cleanNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record PaymentPlan(
        BigDecimal depositAmount,
        BigDecimal balanceAmount,
        LocalDate depositDueDate,
        LocalDate balanceDueDate
    ) {
    }
}
