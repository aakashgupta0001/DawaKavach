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

/**
 * Table {@code scan_items}. One line of the bill, the brand the fuzzy matcher
 * thinks it is, and whether the user has confirmed that guess. Nothing reaches
 * the cabinet until {@code confirmed} is true.
 */
@Entity
@Table(name = "scan_items")
public class ScanItem {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  /** Back-reference, kept lazy on purpose: nothing reads it. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "scan_id", nullable = false)
  public Scan scan;

  @Column(name = "detected_text", length = 500)
  public String detectedText;

  /** Prices, pack sizes, batch numbers and expiry dates stripped out. */
  @Column(name = "cleaned_text", length = 300)
  public String cleanedText;

  /** How sure Tesseract was about the characters, 0 to 1. */
  @Column(name = "ocr_confidence")
  public double ocrConfidence;

  /** Top brand guesses with their Jaro-Winkler scores, as JSON. */
  @Lob
  @Column(name = "suggestions_json")
  public String suggestionsJson = "[]";

  @Column(name = "matched_medicine_id")
  public Long matchedMedicineId;

  @Column(nullable = false)
  public boolean confirmed = false;
}
