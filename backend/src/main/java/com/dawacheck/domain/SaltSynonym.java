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

/**
 * Table {@code salt_synonyms}. Acetaminophen and PCM both have to resolve to
 * Paracetamol, or a duplicate goes unnoticed.
 */
@Entity
@Table(name = "salt_synonyms")
public class SaltSynonym {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "salt_id", nullable = false)
  public Salt salt;

  @Column(nullable = false, length = 150)
  public String synonym;
}
