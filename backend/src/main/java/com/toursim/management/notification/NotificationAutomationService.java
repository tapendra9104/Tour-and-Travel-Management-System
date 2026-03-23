package com.toursim.management.notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toursim.management.auth.AppUser;
import com.toursim.management.auth.AppUserService;
import com.toursim.management.booking.Booking;
import com.toursim.management.booking.BookingService;
import com.toursim.management.booking.BookingStatus;
import com.toursim.management.payment.PaymentService;
import com.toursim.management.payment.PaymentSummary;
import com.toursim.management.tour.Tour;
import com.toursim.management.tour.TourCatalogService;

@Service
public class NotificationAutomationService {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final TourCatalogService tourCatalogService;
    private final AppUserService appUserService;
    private final int paymentReminderLeadDays;

    public NotificationAutomationService(
        BookingService bookingService,
        PaymentService paymentService,
        NotificationService notificationService,
        TourCatalogService tourCatalogService,
        AppUserService appUserService,
        @Value("${app.notifications.payment-reminder-lead-days:2}") int paymentReminderLeadDays
    ) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
        this.tourCatalogService = tourCatalogService;
        this.appUserService = appUserService;
        this.paymentReminderLeadDays = paymentReminderLeadDays;
    }

    @Scheduled(cron = "${app.notifications.automation-cron:0 0 8 * * *}")
    @Transactional
    public void processAutomatedNotifications() {
        List<Booking> bookings = bookingService.findAll();
        java.util.Map<Long, PaymentSummary> paymentSummaries = paymentService.summarize(bookings);

        for (Booking booking : bookings) {
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                continue;
            }

            Tour tour = tourCatalogService.findById(booking.getTourId()).orElse(null);
            if (tour == null) {
                continue;
            }

            PaymentSummary paymentSummary = paymentSummaries.get(booking.getId());
            if (paymentSummary != null) {
                maybeSendPaymentReminder(booking, tour, paymentSummary);
            }
            maybeSendTripReminder(booking, tour);
        }
    }

    private void maybeSendPaymentReminder(Booking booking, Tour tour, PaymentSummary paymentSummary) {
        if (paymentSummary.outstandingAmount().compareTo(java.math.BigDecimal.ZERO) <= 0 || paymentSummary.dueDate() == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        boolean dueSoon = !paymentSummary.dueDate().isAfter(today.plusDays(paymentReminderLeadDays));
        if (!dueSoon) {
            return;
        }

        if (notificationService.hasRecentBookingEmail(
            NotificationCategory.PAYMENT_DUE,
            booking.getId(),
            booking.getEmail(),
            LocalDateTime.now().minusHours(20)
        )) {
            return;
        }

        String subject = paymentSummary.overdue()
            ? "Payment overdue for " + tour.getTitle()
            : "Payment due for " + tour.getTitle();
        String message = "Hi " + booking.getCustomerName() + ",\n\n"
            + "This is a reminder that booking " + booking.getBookingReference() + " for " + tour.getTitle()
            + " has an outstanding amount of " + paymentSummary.currency() + " " + paymentSummary.outstandingAmount() + ". "
            + "The next payment of " + paymentSummary.currency() + " " + paymentSummary.dueNowAmount()
            + " is due by " + paymentSummary.dueDate() + "."
            + "\n\nYou can complete the payment from your Wanderlust dashboard."
            + "\n\nWanderlust Travels";

        notifyBookingRecipient(booking, NotificationCategory.PAYMENT_DUE, subject, message);
    }

    private void maybeSendTripReminder(Booking booking, Tour tour) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            return;
        }

        long daysUntilDeparture = ChronoUnit.DAYS.between(LocalDate.now(), booking.getDate());
        if (daysUntilDeparture != 7 && daysUntilDeparture != 3 && daysUntilDeparture != 1) {
            return;
        }

        if (notificationService.hasRecentBookingEmail(
            NotificationCategory.TRIP_REMINDER,
            booking.getId(),
            booking.getEmail(),
            LocalDateTime.now().minusHours(20)
        )) {
            return;
        }

        String subject = "Trip reminder for " + tour.getTitle();
        String message = "Hi " + booking.getCustomerName() + ",\n\n"
            + "Your " + tour.getTitle() + " trip departs on " + booking.getDate() + ", which is in " + daysUntilDeparture
            + " day" + (daysUntilDeparture == 1 ? "" : "s") + ". "
            + "Please review your booking details, traveler preferences, transport arrangements, and payment status in the dashboard before departure."
            + "\n\nWanderlust Travels";

        notifyBookingRecipient(booking, NotificationCategory.TRIP_REMINDER, subject, message);
    }

    private void notifyBookingRecipient(Booking booking, NotificationCategory category, String subject, String message) {
        if (booking.getUserId() != null) {
            java.util.Optional<AppUser> appUser = appUserService.findById(booking.getUserId());
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
}
