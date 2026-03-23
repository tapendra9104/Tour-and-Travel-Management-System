package com.toursim.management.tour;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TourViewEventRepository extends JpaRepository<TourViewEvent, Long> {

    List<TourViewEvent> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
