package com.toursim.management.waitlist;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "waitlist_reference", nullable = false, unique = true, length = 40)
    private String waitlistReference;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "tour_id", nullable = false, length = 40)
    private String tourId;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false)
    private int guests;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private WaitlistStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "booking_id")
    private Long bookingId;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (waitlistReference == null || waitlistReference.isBlank()) {
            waitlistReference = "WL-" + System.currentTimeMillis();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWaitlistReference() {
        return waitlistReference;
    }

    public void setWaitlistReference(String waitlistReference) {
        this.waitlistReference = waitlistReference;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTourId() {
        return tourId;
    }

    public void setTourId(String tourId) {
        this.tourId = tourId;
    }

    public LocalDate getTravelDate() {
        return travelDate;
    }

    public void setTravelDate(LocalDate travelDate) {
        this.travelDate = travelDate;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public int getGuests() {
        return guests;
    }

    public void setGuests(int guests) {
        this.guests = guests;
    }

    public WaitlistStatus getStatus() {
        return status;
    }

    public void setStatus(WaitlistStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(LocalDateTime notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }
}
