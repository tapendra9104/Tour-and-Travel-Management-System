package com.toursim.management.tour;

import java.time.LocalDate;

public record TourAvailability(
    LocalDate date,
    int capacity,
    int booked,
    int remaining,
    boolean soldOut
) {
}
