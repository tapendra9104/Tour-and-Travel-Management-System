package com.toursim.management.dashboard;

import java.util.List;

import com.toursim.management.booking.dto.BookingResponse;
import com.toursim.management.inquiry.InquiryResponse;
import com.toursim.management.tour.Tour;

public record DashboardResponse(
    String mode,
    boolean authenticated,
    boolean admin,
    String heading,
    DashboardStats stats,
    List<BookingResponse> bookings,
    List<NotificationResponse> notifications,
    List<InquiryResponse> inquiries,
    List<CustomerSummaryResponse> customers,
    List<Tour> recommendations
) {
}
