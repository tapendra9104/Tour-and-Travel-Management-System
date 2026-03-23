package com.toursim.management.tour;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourViewService {

    private final TourViewEventRepository tourViewEventRepository;

    public TourViewService(TourViewEventRepository tourViewEventRepository) {
        this.tourViewEventRepository = tourViewEventRepository;
    }

    @Transactional
    public void recordView(Long userId, String tourId) {
        TourViewEvent event = new TourViewEvent();
        event.setUserId(userId);
        event.setTourId(tourId);
        tourViewEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<TourViewEvent> recentViews(Long userId) {
        return tourViewEventRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }
}
