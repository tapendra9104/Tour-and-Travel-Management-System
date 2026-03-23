package com.toursim.management.booking;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingActivityService {

    private final BookingActivityRepository bookingActivityRepository;

    public BookingActivityService(BookingActivityRepository bookingActivityRepository) {
        this.bookingActivityRepository = bookingActivityRepository;
    }

    @Transactional
    public void record(
        Booking booking,
        Long actorUserId,
        String actorName,
        String actorRole,
        String actionType,
        BookingStatus previousStatus,
        BookingStatus newStatus,
        String note
    ) {
        BookingActivity activity = new BookingActivity();
        activity.setBookingId(booking.getId());
        activity.setActorUserId(actorUserId);
        activity.setActorName(actorName);
        activity.setActorRole(actorRole);
        activity.setActionType(actionType);
        activity.setPreviousStatus(previousStatus == null ? null : previousStatus.name());
        activity.setNewStatus(newStatus == null ? null : newStatus.name());
        activity.setNote(note);
        bookingActivityRepository.save(activity);
    }

    @Transactional(readOnly = true)
    public List<BookingActivity> recentForBooking(Long bookingId) {
        return bookingActivityRepository.findTop10ByBookingIdOrderByCreatedAtDesc(bookingId);
    }

    @Transactional(readOnly = true)
    public List<BookingActivity> recentActivityFeed() {
        return bookingActivityRepository.findTop20ByOrderByCreatedAtDesc();
    }
}
