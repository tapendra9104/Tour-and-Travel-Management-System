package com.toursim.management.payment;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findAllByBookingIdOrderByCreatedAtDesc(Long bookingId);

    List<PaymentTransaction> findAllByBookingIdInOrderByBookingIdAscCreatedAtDesc(Collection<Long> bookingIds);
}
