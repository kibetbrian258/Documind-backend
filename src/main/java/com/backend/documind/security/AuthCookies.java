package com.backend.documind.security;

import com.backend.documind.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the session cookie that carries the login token.
 *
 * HttpOnly: JavaScript can't read it, so an XSS bug can't steal the token.
 * Secure:   only sent over HTTPS (switched off for local http development).
 * SameSite=Lax: not sent on cross-site POSTs, a first layer against CSRF.
 * Path=/api: only sent to API calls, not to Swagger or static files.
 */
@Component
public class AuthCookies {

    private final AuthProperties properties;

    public AuthCookies(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie loginCookie(String token) {
        return baseCookie(token).maxAge(properties.sessionDuration()).build();
    }

    /** An expired, empty cookie with the same name and path tells the browser to delete it. */
    public ResponseCookie logoutCookie() {
        return baseCookie("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.cookieName(), value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/api");
    }
}