package com.backend.documind.controller;

import com.backend.documind.dto.LoginRequest;
import com.backend.documind.dto.RegisterRequest;
import com.backend.documind.dto.UserResponse;
import com.backend.documind.entity.User;
import com.backend.documind.security.AuthCookies;
import com.backend.documind.service.AuthService;
import com.backend.documind.service.JwtService;
import com.backend.documind.service.QuotaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final AuthCookies authCookies;
    private final QuotaService quotaService;

    /**
     * Sets the XSRF-TOKEN cookie. The frontend calls this once when it loads,
     * then sends the cookie's value back in the X-XSRF-TOKEN header on every
     * POST, PATCH and DELETE (Angular's HttpClient does this automatically).
     */
    @GetMapping("/csrf")
    @Operation(summary = "Get a CSRF cookie (call this first)")
    public ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        // Spring Security creates the token lazily; reading it makes Spring write the cookie.
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account and log in")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request.fullName(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, authCookies.loginCookie(jwtService.issueToken(user)).toString())
                .body(UserResponse.from(user, quotaService.today(user)));
    }

    @PostMapping("/login")
    @Operation(summary = "Log in; sets the session cookie")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.authenticate(request.email(), request.password());
        String cookie = authCookies.loginCookie(jwtService.issueToken(user), request.keepSignedIn()).toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(UserResponse.from(user, quotaService.today(user)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out; clears the session cookie")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookies.logoutCookie().toString())
                .build();
    }
}