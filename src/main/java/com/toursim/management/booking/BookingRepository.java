package com.toursim.management.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllByOrderByCreatedAtDesc();

    @Query("""
        select b from Booking b
        where b.userId = :userId or lower(b.email) = lower(:email)
        order by b.createdAt desc
        """)
    List<Booking> findVisibleToUser(@Param("userId") Long userId, @Param("email") String email);

    Optional<Booking> findByBookingReferenceAndEmailIgnoreCase(String bookingReference, String email);

    List<Booking> findAllByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    @Query("""
        select coalesce(sum(b.guests), 0)
        from Booking b
        where b.tourId = :tourId
          and b.date = :date
          and b.status <> com.toursim.management.booking.BookingStatus.CANCELLED
        """)
    int totalGuestsForDeparture(@Param("tourId") String tourId, @Param("date") LocalDate date);
}
