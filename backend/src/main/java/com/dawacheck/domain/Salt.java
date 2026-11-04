package com.dawacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Table {@code salts}. The unit every safety check works on. */
@Entity
@Table(name = "salts")
public class Salt {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 150)
  public String name;

  /** WHO ATC code, used as a weak similarity signal by the risk model. */
  @Column(length = 20)
  public String atc = "";

  /** Pharmacological classes, comma separated: {@code nsaid,antiplatelet}. */
  @Column(name = "drug_classes", length = 255)
  public String drugClasses = "";

  /**
   * Highest sensible daily amount in mg for this salt, or null when no ceiling
   * is defined. Drives the same-salt daily total check (competitive feature 6).
   */
  @Column(name = "daily_ceiling_mg")
  public Double dailyCeilingMg;
}
