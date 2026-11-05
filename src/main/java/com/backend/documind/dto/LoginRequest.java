package com.backend.documind.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Email is required.")
        String email,

        @NotBlank(message = "Password is required.")
        String password,

        // "Keep me signed in for 7 days". If false, the login lasts until the browser is closed.
        // Missing (null) counts as true, so older clients keep the 7-day login.
        Boolean rememberMe) {

    public boolean keepSignedIn() {
        return rememberMe == null || rememberMe;
    }

    /** Keeps the password out of logs if this object is ever printed. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=****, rememberMe=" + rememberMe + "]";
    }
}