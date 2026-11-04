package com.dawacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Table {@code reminders}. A dose reminder carries a time of day; a refill
 * reminder carries a date. The scheduled job in
 * {@code com.dawacheck.service.ReminderService} sends them by email.
 */
@Entity
@Table(name = "reminders")
public class Reminder {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "entry_id", nullable = false)
  public CabinetEntry entry;

  /** dose or refill. */
  @Column(nullable = false, length = 20)
  public String type = "dose";

  /** "08:00" for a dose, "2026-11-08" for a refill. */
  @Column(name = "remind_at", nullable = false, length = 20)
  public String remindAt;

  @Column(nullable = false, length = 20)
  public String channel = "email";

  @Column(nullable = false)
  public boolean enabled = true;

  @Column(name = "last_sent_at")
  public Instant lastSentAt;
}
