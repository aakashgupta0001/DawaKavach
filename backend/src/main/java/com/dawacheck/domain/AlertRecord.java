package com.dawacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Table {@code alerts}. The safety check is recomputed from the cabinet every
 * time it is asked for, so these rows are a snapshot of the last result rather
 * than the source of truth. They exist so the doctor summary and the reminder
 * job can read what the family was last shown without recomputing, and so a
 * change over time is visible.
 */
@Entity
@Table(name = "alerts")
public class AlertRecord {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  /** Write-only from the application's side; nothing reads it back. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  public FamilyMember member;

  @Column(nullable = false, length = 40)
  public String type;

  @Column(nullable = false, length = 20)
  public String severity;

  @Column(nullable = false, length = 10)
  public String colour;

  /** rule, dataset or ai. Never blurred together (section 14). */
  @Column(nullable = false, length = 20)
  public String source;

  @Column(name = "salt_ids", length = 255)
  public String saltIds = "";

  @Column(name = "entry_ids", length = 255)
  public String entryIds = "";

  /** The full alert object the API returned, English and Hindi both. */
  @Lob
  @Column(name = "payload_json")
  public String payloadJson;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = Instant.now();
}
