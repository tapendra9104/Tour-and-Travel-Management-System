package com.toursim.management.waitlist;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WaitlistActionRequest(
    @NotNull WaitlistStatus status,
    @Size(max = 500) String message
) {
}
