package com.toursim.management.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.toursim.management.booking.Booking;
import com.toursim.management.booking.BookingStatus;
import com.toursim.management.payment.PaymentSummary;

public record BookingResponse(
    Long id,
    String bookingReference,
    String tourId,
    String tourTitle,
    String customerName,
    String email,
    String phone,
    int guests,
    LocalDate date,
    BigDecimal serviceFee,
    BigDecimal totalPrice,
    String mealPreference,
    String dietaryRestrictions,
    String occasionType,
    String occasionNotes,
    String roomPreference,
    String tripStyle,
    String assistanceNotes,
    boolean transferRequired,
    String travelerNotes,
    String transportMode,
    String transportClass,
    String transportStatus,
    boolean documentsVerified,
    String paymentStatus,
    String paymentCurrency,
    BigDecimal paymentPaidAmount,
    BigDecimal paymentRefundedAmount,
    BigDecimal paymentOutstandingAmount,
    BigDecimal paymentDueNowAmount,
    BigDecimal paymentDepositAmount,
    BigDecimal paymentDepositOutstandingAmount,
    BigDecimal paymentBalanceOutstandingAmount,
    BigDecimal paymentRefundableAmount,
    BigDecimal paymentCancellationFeeAmount,
    String paymentNextStage,
    LocalDate paymentDueDate,
    boolean paymentOverdue,
    String paymentLastMethod,
    String paymentLastReceiptNumber,
    String paymentLastTransactionReference,
    LocalDateTime paymentLastPaidAt,
    String statusReason,
    @JsonProperty("status") String statusValue,
    LocalDateTime createdAt
) {

    public static BookingResponse from(Booking booking, String tourTitle, PaymentSummary paymentSummary) {
        return new BookingResponse(
            booking.getId(),
            booking.getBookingReference(),
            booking.getTourId(),
            tourTitle,
            booking.getCustomerName(),
            booking.getEmail(),
            booking.getPhone(),
            booking.getGuests(),
            booking.getDate(),
            booking.getServiceFee(),
            booking.getTotalPrice(),
            booking.getMealPreference(),
            booking.getDietaryRestrictions(),
            booking.getOccasionType(),
            booking.getOccasionNotes(),
            booking.getRoomPreference(),
            booking.getTripStyle(),
            booking.getAssistanceNotes(),
            booking.isTransferRequired(),
            booking.getTravelerNotes(),
            booking.getTransportMode(),
            booking.getTransportClass(),
            booking.getTransportStatus(),
            booking.isDocumentsVerified(),
            paymentSummary.status().label(),
            paymentSummary.currency(),
            paymentSummary.paidAmount(),
            paymentSummary.refundedAmount(),
            paymentSummary.outstandingAmount(),
            paymentSummary.dueNowAmount(),
            paymentSummary.depositAmount(),
            paymentSummary.depositOutstandingAmount(),
            paymentSummary.balanceOutstandingAmount(),
            paymentSummary.refundableAmount(),
            paymentSummary.cancellationFeeAmount(),
            paymentSummary.nextStage() == null ? null : paymentSummary.nextStage().label(),
            paymentSummary.dueDate(),
            paymentSummary.overdue(),
            paymentSummary.lastMethod() == null ? null : paymentSummary.lastMethod().label(),
            paymentSummary.lastReceiptNumber(),
            paymentSummary.lastTransactionReference(),
            paymentSummary.lastPaidAt(),
            booking.getStatusReason(),
            toClientStatus(booking.getStatus()),
            booking.getCreatedAt()
        );
    }

    private static String toClientStatus(BookingStatus status) {
        return status.name().toLowerCase();
    }
}
