package com.membora.platform.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    @PostMapping("/login") AuthService.TokenPair login(@Valid @RequestBody Login request) { return service.login(request.email(), request.password(), request.organizationId()); }
    @PostMapping("/refresh") AuthService.TokenPair refresh(@Valid @RequestBody TokenRequest request) { return service.refresh(request.refreshToken()); }
    @PostMapping("/logout") void logout(@Valid @RequestBody TokenRequest request) { service.logout(request.refreshToken()); }
    record Login(@Email @NotBlank String email, @NotBlank String password, @NotNull UUID organizationId) {}
    record TokenRequest(@NotBlank String refreshToken) {}
}
