package com.toursim.management.booking;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingActivityRepository extends JpaRepository<BookingActivity, Long> {

    List<BookingActivity> findTop10ByBookingIdOrderByCreatedAtDesc(Long bookingId);

    List<BookingActivity> findTop20ByOrderByCreatedAtDesc();
}
