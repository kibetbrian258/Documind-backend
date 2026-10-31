package com.backend.documind.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Tells Spring Security where to find the login token: in our HttpOnly cookie
 * instead of the usual "Authorization: Bearer ..." header.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";

    private final String cookieName;

    public CookieBearerTokenResolver(String cookieName) {
        this.cookieName = cookieName;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        // Ignore the cookie on login, register, logout and csrf. Otherwise an
        // expired cookie would be rejected with 401 and block logging in again.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith(AUTH_PATH_PREFIX)) {
            return null;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
