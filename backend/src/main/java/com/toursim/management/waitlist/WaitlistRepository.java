package com.toursim.management.waitlist;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    List<WaitlistEntry> findByTourIdAndTravelDateAndStatusOrderByCreatedAtAsc(String tourId, LocalDate travelDate, WaitlistStatus status);

    List<WaitlistEntry> findTop15ByStatusInOrderByCreatedAtDesc(List<WaitlistStatus> statuses);

    List<WaitlistEntry> findByStatusInOrderByCreatedAtDesc(List<WaitlistStatus> statuses);

    long countByStatusIn(List<WaitlistStatus> statuses);
}
