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

/** Table {@code medicine_salts}. The brand-to-salt mapping the whole app rests on. */
@Entity
@Table(name = "medicine_salts")
public class MedicineSalt {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  /** Back-reference, kept lazy on purpose: nothing reads it. */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "medicine_id", nullable = false)
  public Medicine medicine;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "salt_id", nullable = false)
  public Salt salt;

  /** As printed on the box: 650 mg, 250 IU. */
  @Column(nullable = false, length = 40)
  public String strength;
}
