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

import java.time.LocalDate;

/** Table {@code cabinet_entries}. One medicine a member is actually taking. */
@Entity
@Table(name = "cabinet_entries")
public class CabinetEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  public FamilyMember member;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "medicine_id", nullable = false)
  public Medicine medicine;

  /** Free text shown back to the user, such as Morning after breakfast. */
  @Column(length = 160)
  public String schedule = "As advised";

  @Column(name = "doses_per_day", nullable = false)
  public int dosesPerDay = 1;

  @Column(name = "prescribed_by", length = 160)
  public String prescribedBy = "Not recorded";

  @Column(name = "start_date")
  public LocalDate startDate;

  @Column(name = "end_date")
  public LocalDate endDate;

  @Column(nullable = false)
  public boolean active = true;

  /** Set by the user; feeds the STOPP-style long-term checks. */
  @Column(name = "long_term", nullable = false)
  public boolean longTerm = false;

  @Column(name = "from_scan_id")
  public Long fromScanId;
}
