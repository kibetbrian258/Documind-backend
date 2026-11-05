package com.backend.documind.security;

import com.backend.documind.config.AuthProperties;
import lombok.RequiredArgsConstructor;
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
 *
 * "Keep me signed in" ticked: the cookie lasts 7 days. Not ticked: it has no
 * expiry date, so the browser deletes it when it closes (a "session cookie").
 * The token inside expires after 7 days either way.
 */
@Component
@RequiredArgsConstructor
public class AuthCookies {

    private final AuthProperties properties;

    public ResponseCookie loginCookie(String token) {
        return loginCookie(token, true);
    }

    public ResponseCookie loginCookie(String token, boolean keepSignedIn) {
        ResponseCookie.ResponseCookieBuilder cookie = baseCookie(token);
        if (keepSignedIn) {
            cookie.maxAge(properties.sessionDuration());
        }
        return cookie.build();
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