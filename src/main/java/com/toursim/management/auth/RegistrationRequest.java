package com.toursim.management.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
    @NotBlank @Size(max = 120) String fullName,
    @NotBlank @Email String email,
    @Size(max = 30) String phone,
    @NotBlank @Size(min = 8, max = 100)
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "Password must include uppercase, lowercase, and a number"
    )
    String password
) {
}
