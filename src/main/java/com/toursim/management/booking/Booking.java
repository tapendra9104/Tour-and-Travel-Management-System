package com.toursim.management.booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

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
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", nullable = false, unique = true, length = 40)
    private String bookingReference;

    @Column(name = "tour_id", nullable = false, length = 40)
    private String tourId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 50)
    private String phone;

    @Column(nullable = false)
    private int guests;

    @Column(name = "travel_date", nullable = false)
    private LocalDate date;

    @Column(name = "service_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceFee;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "meal_preference", length = 80)
    private String mealPreference;

    @Column(name = "dietary_restrictions", length = 500)
    private String dietaryRestrictions;

    @Column(name = "occasion_type", length = 80)
    private String occasionType;

    @Column(name = "occasion_notes", length = 500)
    private String occasionNotes;

    @Column(name = "room_preference", length = 120)
    private String roomPreference;

    @Column(name = "trip_style", length = 80)
    private String tripStyle;

    @Column(name = "assistance_notes", length = 500)
    private String assistanceNotes;

    @Column(name = "transfer_required", nullable = false)
    private boolean transferRequired;

    @Column(name = "traveler_notes", length = 500)
    private String travelerNotes;

    @Column(name = "transport_mode", length = 40)
    private String transportMode;

    @Column(name = "transport_class", length = 80)
    private String transportClass;

    @Column(name = "transport_status", nullable = false, length = 40)
    private String transportStatus;

    @Column(name = "documents_verified", nullable = false)
    private boolean documentsVerified;

    @Column(name = "operations_priority", nullable = false, length = 40)
    private String operationsPriority;

    @Column(name = "operations_notes", length = 500)
    private String operationsNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (bookingReference == null || bookingReference.isBlank()) {
            bookingReference = "BK-" + System.currentTimeMillis();
        }
        if (transportStatus == null || transportStatus.isBlank()) {
            transportStatus = "Not Required";
        }
        if (operationsPriority == null || operationsPriority.isBlank()) {
            operationsPriority = "Normal";
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
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

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public String getTourId() {
        return tourId;
    }

    public void setTourId(String tourId) {
        this.tourId = tourId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public BigDecimal getServiceFee() {
        return serviceFee;
    }

    public void setServiceFee(BigDecimal serviceFee) {
        this.serviceFee = serviceFee;
    }

    public String getMealPreference() {
        return mealPreference;
    }

    public void setMealPreference(String mealPreference) {
        this.mealPreference = mealPreference;
    }

    public String getDietaryRestrictions() {
        return dietaryRestrictions;
    }

    public void setDietaryRestrictions(String dietaryRestrictions) {
        this.dietaryRestrictions = dietaryRestrictions;
    }

    public String getOccasionType() {
        return occasionType;
    }

    public void setOccasionType(String occasionType) {
        this.occasionType = occasionType;
    }

    public String getOccasionNotes() {
        return occasionNotes;
    }

    public void setOccasionNotes(String occasionNotes) {
        this.occasionNotes = occasionNotes;
    }

    public String getRoomPreference() {
        return roomPreference;
    }

    public void setRoomPreference(String roomPreference) {
        this.roomPreference = roomPreference;
    }

    public String getTripStyle() {
        return tripStyle;
    }

    public void setTripStyle(String tripStyle) {
        this.tripStyle = tripStyle;
    }

    public String getAssistanceNotes() {
        return assistanceNotes;
    }

    public void setAssistanceNotes(String assistanceNotes) {
        this.assistanceNotes = assistanceNotes;
    }

    public boolean isTransferRequired() {
        return transferRequired;
    }

    public void setTransferRequired(boolean transferRequired) {
        this.transferRequired = transferRequired;
    }

    public String getTravelerNotes() {
        return travelerNotes;
    }

    public void setTravelerNotes(String travelerNotes) {
        this.travelerNotes = travelerNotes;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
    }

    public String getTransportClass() {
        return transportClass;
    }

    public void setTransportClass(String transportClass) {
        this.transportClass = transportClass;
    }

    public String getTransportStatus() {
        return transportStatus;
    }

    public void setTransportStatus(String transportStatus) {
        this.transportStatus = transportStatus;
    }

    public boolean isDocumentsVerified() {
        return documentsVerified;
    }

    public void setDocumentsVerified(boolean documentsVerified) {
        this.documentsVerified = documentsVerified;
    }

    public String getOperationsPriority() {
        return operationsPriority;
    }

    public void setOperationsPriority(String operationsPriority) {
        this.operationsPriority = operationsPriority;
    }

    public String getOperationsNotes() {
        return operationsNotes;
    }

    public void setOperationsNotes(String operationsNotes) {
        this.operationsNotes = operationsNotes;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
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
}
