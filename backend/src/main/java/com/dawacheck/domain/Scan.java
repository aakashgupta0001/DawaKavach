package com.dawacheck.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Table {@code scans}. One uploaded photo and the text Tesseract read from it.
 *
 * The image bytes live here only until the user confirms the scan. After
 * confirmation they are cleared unless the user asked to keep the photo
 * (section 14).
 */
@Entity
@Table(name = "scans")
public class Scan {
  public static final String AWAITING = "AWAITING_CONFIRMATION";
  public static final String CONFIRMED = "CONFIRMED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  public FamilyMember member;

  @Column(nullable = false, length = 30)
  public String status = AWAITING;

  @Column(name = "image_name", length = 255)
  public String imageName;

  @Lob
  @Column(name = "image_bytes")
  public byte[] imageBytes;

  @Column(name = "image_retained", nullable = false)
  public boolean imageRetained = false;

  @Lob
  @Column(name = "raw_text")
  public String rawText;

  /** Set when the photo could not be read and sample text was used instead. */
  @Column(name = "fixture_label", length = 120)
  public String fixtureLabel;

  @Column(name = "ocr_engine", length = 60)
  public String ocrEngine;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = Instant.now();

  @OneToMany(mappedBy = "scan", cascade = CascadeType.ALL, orphanRemoval = true,
      fetch = FetchType.EAGER)
  @OrderBy("id ASC")
  public List<ScanItem> items = new ArrayList<>();
}
