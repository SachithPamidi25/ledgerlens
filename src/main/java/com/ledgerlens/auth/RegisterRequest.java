package com.ledgerlens.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank
        @Size(min = 8)
        @Pattern(
                regexp = "^(?=.*[A-Z])(?=.*\\d).+$",
                message = "must contain at least one uppercase letter and one digit"
        )
        String password,
        @NotBlank String fullName
) {}