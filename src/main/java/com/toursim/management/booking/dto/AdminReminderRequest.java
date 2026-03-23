package com.toursim.management.booking.dto;

import jakarta.validation.constraints.Size;

public record AdminReminderRequest(@Size(max = 500) String message) {
}
