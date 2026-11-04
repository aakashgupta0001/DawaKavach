package com.dawacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Table {@code interactions}. Imported from DDInter 2.0, which grades each pair
 * major, moderate or minor. These rows are facts from a dataset and are kept
 * visibly separate from anything the model predicts (section 14).
 */
@Entity
@Table(name = "interactions", indexes = @Index(columnList = "salt_a_id,salt_b_id"))
public class Interaction {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  /** Stored with saltAId below saltBId, so a pair has exactly one row. */
  @Column(name = "salt_a_id", nullable = false)
  public Long saltAId;

  @Column(name = "salt_b_id", nullable = false)
  public Long saltBId;

  @Column(nullable = false, length = 20)
  public String severity;

  @Column(length = 1000)
  public String mechanism;

  @Column(name = "mechanism_hi", length = 1500)
  public String mechanismHi;

  @Column(name = "source_dataset", length = 60)
  public String sourceDataset = "DDInter 2.0";
}
