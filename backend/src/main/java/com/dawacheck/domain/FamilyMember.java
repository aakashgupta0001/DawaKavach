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

/** Table {@code family_members}. */
@Entity
@Table(name = "family_members")
public class FamilyMember {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  public AppUser owner;

  @Column(nullable = false, length = 120)
  public String name;

  @Column(nullable = false)
  public int age;

  @Column(length = 60)
  public String relation;

  @Column(length = 60)
  public String nickname;

  /** Salt ids this member reacts to, comma separated (competitive feature 8). */
  @Column(name = "allergy_salt_ids", length = 255)
  public String allergySaltIds = "";
}
