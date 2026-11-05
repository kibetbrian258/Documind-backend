package com.backend.documind.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "Your name is required.")
        @Size(max = 100, message = "Name must be at most 100 characters.")
        String fullName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Email must be at most 254 characters.")
        String email,

        // BCrypt only uses the first 72 bytes of a password, so longer ones are rejected.
        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.")
        String password) {

    /** Keeps the password out of logs if this object is ever printed. */
    @Override
    public String toString() {
        return "RegisterRequest[fullName=" + fullName + ", email=" + email + ", password=****]";
    }
}