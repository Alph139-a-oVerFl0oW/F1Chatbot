package com.f1app.auth;

import com.f1app.auth.AuthDtos.AuthResponse;
import com.f1app.auth.AuthDtos.LoginRequest;
import com.f1app.auth.AuthDtos.RegisterRequest;
import com.f1app.auth.AuthDtos.UserProfile;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Lets the frontend validate a stored token on page load: a 200 means the
     * token is still good, a 401 means clear it and show the login form.
     * @AuthenticationPrincipal resolves to the User the JWT filter put in the
     * SecurityContext.
     */
    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal User user) {
        return authService.profile(user);
    }
}
