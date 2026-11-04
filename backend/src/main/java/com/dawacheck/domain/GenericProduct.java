package com.dawacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Table {@code generic_products}. The Jan Aushadhi list publishes composition as
 * free text, so both forms are kept: {@code composition} exactly as published,
 * and {@code saltKey} normalised at import time for matching.
 */
@Entity
@Table(name = "generic_products")
public class GenericProduct {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 300)
  public String name;

  @Column(length = 300)
  public String composition;

  /**
   * Salt id and strength in mg, sorted and joined: {@code 1@650|17@50}. Two
   * products with the same key are the same medicine by a different name.
   */
  @Column(name = "salt_key", length = 300)
  public String saltKey = "";

  /** Salt ids only, sorted: matches a brand whose strengths differ. */
  @Column(name = "salt_ids", length = 255)
  public String saltIds = "";

  @Column(length = 40)
  public String form;

  @Column(length = 60)
  public String pack;

  public Double mrp;
}
