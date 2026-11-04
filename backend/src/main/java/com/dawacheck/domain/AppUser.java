package com.dawacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Table {@code users}. One row per account; an account only ever sees its own family. */
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class AppUser {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 120)
  public String name;

  @Column(nullable = false, length = 190)
  public String email;

  /** BCrypt hash. The plain password is never stored or logged (section 14). */
  @Column(name = "password_hash", nullable = false, length = 100)
  public String passwordHash;

  @Column(nullable = false, length = 20)
  public String role = "OWNER";

  /** DPDP Act 2023: consent is recorded, not assumed. */
  @Column(name = "consent_at")
  public Instant consentAt;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = Instant.now();

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getRole() {
    return role;
  }
}
