package com.toursim.management.waitlist;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.toursim.management.auth.AppUser;
import com.toursim.management.booking.BookingRequestContext;
import com.toursim.management.notification.NotificationCategory;
import com.toursim.management.notification.NotificationService;
import com.toursim.management.tour.Tour;

@Service
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final NotificationService notificationService;

    public WaitlistService(WaitlistRepository waitlistRepository, NotificationService notificationService) {
        this.waitlistRepository = waitlistRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public WaitlistEntry createWaitlistEntry(Tour tour, BookingRequestContext requestContext, Optional<AppUser> user) {
        WaitlistEntry waitlistEntry = new WaitlistEntry();
        waitlistEntry.setUserId(user.map(AppUser::getId).orElse(null));
        waitlistEntry.setTourId(tour.getId());
        waitlistEntry.setTravelDate(requestContext.date());
        waitlistEntry.setCustomerName(requestContext.customerName());
        waitlistEntry.setEmail(requestContext.email());
        waitlistEntry.setPhone(requestContext.phone());
        waitlistEntry.setGuests(requestContext.guests());
        waitlistEntry.setStatus(WaitlistStatus.WAITLISTED);
        waitlistEntry = waitlistRepository.save(waitlistEntry);

        String subject = "You are on the waitlist for " + tour.getTitle();
        String message = "Hi " + requestContext.customerName() + ",\n\n"
            + "The " + requestContext.date() + " departure for " + tour.getTitle() + " is currently full. "
            + "We added you to the waitlist with reference " + waitlistEntry.getWaitlistReference() + ". "
            + "We will notify you if seats open up.\n\nWanderlust Travels";

        if (user.isPresent()) {
            notificationService.notifyUser(user.get(), NotificationCategory.WAITLIST_JOINED, subject, message, null, null);
        } else {
            notificationService.notifyGuest(requestContext.email(), requestContext.customerName(), NotificationCategory.WAITLIST_JOINED, subject, message, null, null);
        }

        notificationService.notifyAdmins(
            NotificationCategory.ADMIN_ALERT,
            "Waitlist joined for " + tour.getTitle(),
            requestContext.customerName() + " joined the waitlist for " + requestContext.date() + ".",
            null,
            null
        );
        return waitlistEntry;
    }

    @Transactional
    public void notifyNextEligibleTraveler(Tour tour, LocalDate date) {
        waitlistRepository.findByTourIdAndTravelDateAndStatusOrderByCreatedAtAsc(tour.getId(), date, WaitlistStatus.WAITLISTED)
            .stream()
            .findFirst()
            .ifPresent(waitlistEntry -> {
                waitlistEntry.setStatus(WaitlistStatus.NOTIFIED);
                waitlistEntry.setNotifiedAt(LocalDateTime.now());
                waitlistRepository.save(waitlistEntry);

                String subject = "Seats are open again for " + tour.getTitle();
                String message = "Hi " + waitlistEntry.getCustomerName() + ",\n\n"
                    + "A seat has opened for " + tour.getTitle() + " on " + date + ". "
                    + "Please return to Wanderlust and complete your booking for that date.\n\nWanderlust Travels";

                notificationService.notifyGuest(
                    waitlistEntry.getEmail(),
                    waitlistEntry.getCustomerName(),
                    NotificationCategory.WAITLIST_AVAILABLE,
                    subject,
                    message,
                    waitlistEntry.getBookingId(),
                    null
                );
            });
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntry> recentActiveEntries() {
        return waitlistRepository.findTop15ByStatusInOrderByCreatedAtDesc(List.of(WaitlistStatus.WAITLISTED, WaitlistStatus.NOTIFIED));
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntry> activeEntries() {
        return waitlistRepository.findByStatusInOrderByCreatedAtDesc(List.of(WaitlistStatus.WAITLISTED, WaitlistStatus.NOTIFIED));
    }

    @Transactional(readOnly = true)
    public long activeWaitlistCount() {
        return waitlistRepository.countByStatusIn(List.of(WaitlistStatus.WAITLISTED, WaitlistStatus.NOTIFIED));
    }

    @Transactional(readOnly = true)
    public WaitlistEntry requireEntry(Long id) {
        return waitlistRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Waitlist entry not found"));
    }

    @Transactional
    public WaitlistEntry updateStatus(Tour tour, Long id, WaitlistStatus status, String message) {
        WaitlistEntry waitlistEntry = requireEntry(id);

        waitlistEntry.setStatus(status);
        if (status == WaitlistStatus.NOTIFIED) {
            waitlistEntry.setNotifiedAt(LocalDateTime.now());
            String subject = "Update on your waitlist request for " + tour.getTitle();
            String body = (message == null || message.isBlank())
                ? "Hi " + waitlistEntry.getCustomerName() + ",\n\nA seat is now available for " + tour.getTitle()
                    + " on " + waitlistEntry.getTravelDate() + ". Please return to Wanderlust and complete your booking.\n\nWanderlust Travels"
                : message.trim();
            notificationService.notifyGuest(
                waitlistEntry.getEmail(),
                waitlistEntry.getCustomerName(),
                NotificationCategory.WAITLIST_AVAILABLE,
                subject,
                body,
                waitlistEntry.getBookingId(),
                null
            );
        }
        if (status == WaitlistStatus.CANCELLED) {
            String body = (message == null || message.isBlank())
                ? "Hi " + waitlistEntry.getCustomerName() + ",\n\nYour waitlist request " + waitlistEntry.getWaitlistReference()
                    + " has been closed by the travel team.\n\nWanderlust Travels"
                : message.trim();
            notificationService.notifyGuest(
                waitlistEntry.getEmail(),
                waitlistEntry.getCustomerName(),
                NotificationCategory.ADMIN_ALERT,
                "Waitlist update",
                body,
                waitlistEntry.getBookingId(),
                null
            );
        }

        return waitlistRepository.save(waitlistEntry);
    }
}
