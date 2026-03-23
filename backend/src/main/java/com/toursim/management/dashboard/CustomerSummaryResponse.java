package com.toursim.management.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerSummaryResponse(
    String customerName,
    String email,
    String phone,
    int bookingCount,
    BigDecimal totalSpend,
    LocalDate latestTravelDate,
    String segment
) {
}
