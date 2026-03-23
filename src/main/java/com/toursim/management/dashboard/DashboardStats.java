package com.toursim.management.dashboard;

import java.math.BigDecimal;

public record DashboardStats(
    int total,
    int pending,
    int confirmed,
    int cancelled,
    BigDecimal revenue
) {
}
