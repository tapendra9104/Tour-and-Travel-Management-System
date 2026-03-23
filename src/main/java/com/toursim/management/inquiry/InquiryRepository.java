package com.toursim.management.inquiry;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    List<Inquiry> findTop10ByOrderByCreatedAtDesc();

    List<Inquiry> findAllByOrderByCreatedAtDesc();
}
