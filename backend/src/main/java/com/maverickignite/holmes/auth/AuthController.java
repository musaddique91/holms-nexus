package com.maverickignite.holmes.auth;

import com.maverickignite.holmes.config.HolmesProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Single operator account from configuration; issues short-lived bearer tokens. */
@RestController
@RequestMapping("/api/auth")
class AuthController {

  private static final int MAX_FAILURES = 5;
  private static final Duration LOCKOUT = Duration.ofMinutes(5);

  private final HolmesProperties.Security config;
  private final PasswordEncoder encoder;
  private final TokenService tokens;
  private final Map<String, Failures> failures = new ConcurrentHashMap<>();

  private record Failures(int count, Instant since) {}

  record Login(@NotBlank String username, @NotBlank String password) {}

  AuthController(HolmesProperties properties, PasswordEncoder encoder, TokenService tokens) {
    this.config = properties.getSecurity();
    this.encoder = encoder;
    this.tokens = tokens;
  }

  @PostMapping("/login")
  ResponseEntity<?> login(@Valid @RequestBody Login login, HttpServletRequest request) {
    String client = request.getRemoteAddr();
    if (failures.size() > 10_000) {
      failures.values().removeIf(x -> x.since().plus(LOCKOUT).isBefore(Instant.now()));
    }
    Failures f = failures.get(client);
    if (f != null && f.count() >= MAX_FAILURES && f.since().plus(LOCKOUT).isAfter(Instant.now())) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(Map.of("detail", "Too many failed attempts; try again in a few minutes"));
    }
    boolean userOk =
        MessageDigest.isEqual(
            config.getAdminUsername().getBytes(StandardCharsets.UTF_8),
            login.username().getBytes(StandardCharsets.UTF_8));
    boolean passwordOk = encoder.matches(login.password(), SecurityPasswords.encoded(config.getAdminPassword()));
    if (!userOk || !passwordOk) {
      failures.merge(
          client,
          new Failures(1, Instant.now()),
          (old, n) -> old.since().plus(LOCKOUT).isBefore(Instant.now()) ? n : new Failures(old.count() + 1, old.since()));
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Invalid credentials"));
    }
    failures.remove(client);
    TokenService.IssuedToken token = tokens.issue(login.username());
    return ResponseEntity.ok(Map.of("token", token.token(), "expiresAt", token.expiresAt(), "username", login.username()));
  }

  @GetMapping("/me")
  Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("username", jwt.getSubject(), "expiresAt", jwt.getExpiresAt());
  }
}
