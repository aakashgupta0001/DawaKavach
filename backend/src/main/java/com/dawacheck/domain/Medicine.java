package com.dawacheck.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/** Table {@code medicines}. An Indian brand name as printed on the box. */
@Entity
@Table(name = "medicines")
public class Medicine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 200)
  public String brand;

  @Column(length = 150)
  public String maker;

  @Column(length = 40)
  public String form;

  @Column(length = 60)
  public String pack;

  /** Printed MRP from a public dataset. Always shown as approximate. */
  public Double price;

  @OneToMany(mappedBy = "medicine", cascade = CascadeType.ALL, orphanRemoval = true,
      fetch = FetchType.EAGER)
  public List<MedicineSalt> composition = new ArrayList<>();
}
