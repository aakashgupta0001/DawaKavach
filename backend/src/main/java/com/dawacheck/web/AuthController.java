package com.dawacheck.web;

import com.dawacheck.domain.AppUser;
import com.dawacheck.dto.Dto;
import com.dawacheck.repo.UserRepository;
import com.dawacheck.service.BadRequestException;
import com.dawacheck.service.CurrentUser;
import com.dawacheck.service.ViewMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Module 1, the account half.
 *
 * {@code POST /api/auth/register}, {@code POST /api/auth/login},
 * {@code POST /api/auth/logout} and {@code GET /api/auth/me}.
 *
 * Authentication is by session cookie rather than a token in a header, because
 * the frontend is plain JavaScript served from the same origin: a cookie marked
 * HttpOnly cannot be read by a script, which a token in localStorage can.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AuthenticationManager authentication;
  private final CurrentUser currentUser;
  private final ViewMapper view;
  private final SecurityContextRepository contextRepository =
      new HttpSessionSecurityContextRepository();

  public AuthController(UserRepository users, PasswordEncoder encoder,
      AuthenticationManager authentication, CurrentUser currentUser, ViewMapper view) {
    this.users = users;
    this.encoder = encoder;
    this.authentication = authentication;
    this.currentUser = currentUser;
    this.view = view;
  }

  @PostMapping("/register")
  @Transactional
  public Dto.AuthResponse register(@Valid @RequestBody Dto.RegisterRequest req,
      HttpServletRequest request, HttpServletResponse response) {
    String email = req.email().trim().toLowerCase();
    if (users.existsByEmailIgnoreCase(email)) {
      throw new BadRequestException("An account already exists for that email address.");
    }
    if (!req.consent()) {
      // DPDP Act 2023: the account is not created without the family agreeing to
      // what is stored, so there is no record to have to delete afterwards.
      throw new BadRequestException(
          "Please agree to how the medicine information is stored before continuing.");
    }

    AppUser user = new AppUser();
    user.name = req.name().trim();
    user.email = email;
    user.passwordHash = encoder.encode(req.password());
    user.role = "OWNER";
    user.consentAt = Instant.now();
    user.createdAt = Instant.now();
    users.save(user);

    signIn(email, req.password(), request, response);
    return new Dto.AuthResponse(view.user(user));
  }

  @PostMapping("/login")
  public Dto.AuthResponse login(@Valid @RequestBody Dto.LoginRequest req,
      HttpServletRequest request, HttpServletResponse response) {
    signIn(req.email().trim(), req.password(), request, response);
    return new Dto.AuthResponse(view.user(currentUser.require()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    return ResponseEntity.noContent().build();
  }

  /**
   * Returns the signed-in account, or 204 when nobody is signed in.
   *
   * Every page asks this on load to decide whether to show itself or send the
   * visitor to the login screen, so "not logged in" is an ordinary answer here
   * rather than a 401.
   */
  @GetMapping("/me")
  public ResponseEntity<Dto.UserView> me() {
    return currentUser.find()
        .map(u -> ResponseEntity.ok(view.user(u)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  /**
   * Authenticates and writes the result into the session.
   *
   * A new session id is issued on every sign-in: keeping the one the visitor
   * arrived with would leave the application open to session fixation, where an
   * attacker sets the id first and inherits the session once someone logs in.
   */
  private void signIn(String email, String password, HttpServletRequest request,
      HttpServletResponse response) {
    HttpSession existing = request.getSession(false);
    if (existing != null) {
      existing.invalidate();
    }
    Authentication result = authentication.authenticate(
        new UsernamePasswordAuthenticationToken(email, password));
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(result);
    SecurityContextHolder.setContext(context);
    request.getSession(true);
    contextRepository.saveContext(context, request, response);
  }
}
