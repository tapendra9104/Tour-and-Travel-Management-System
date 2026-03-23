package com.toursim.management.notification;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_user_id")
    private Long recipientUserId;

    @Column(name = "recipient_email", length = 254)
    private String recipientEmail;

    @Column(name = "recipient_name", length = 120)
    private String recipientName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private NotificationCategory category;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationStatus status;

    @Column(name = "admin_visible", nullable = false)
    private boolean adminVisible;

    @Column(name = "related_booking_id")
    private Long relatedBookingId;

    @Column(name = "related_inquiry_id")
    private Long relatedInquiryId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(Long recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public NotificationCategory getCategory() {
        return category;
    }

    public void setCategory(NotificationCategory category) {
        this.category = category;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public boolean isAdminVisible() {
        return adminVisible;
    }

    public void setAdminVisible(boolean adminVisible) {
        this.adminVisible = adminVisible;
    }

    public Long getRelatedBookingId() {
        return relatedBookingId;
    }

    public void setRelatedBookingId(Long relatedBookingId) {
        this.relatedBookingId = relatedBookingId;
    }

    public Long getRelatedInquiryId() {
        return relatedInquiryId;
    }

    public void setRelatedInquiryId(Long relatedInquiryId) {
        this.relatedInquiryId = relatedInquiryId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
