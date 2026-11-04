package com.dawacheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Table {@code llm_cache}. The same salt pair and alert type produce the same
 * explanation, so it is generated once and reused.
 *
 * The cache key is built from the alert type, the salt names, the severity and
 * the language. Nothing identifying goes into it: no member name, no age, no
 * photo, which is also all that is ever sent to the language model (section 14).
 */
@Entity
@Table(name = "llm_cache", indexes = @Index(columnList = "cache_key", unique = true))
public class LlmCache {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "cache_key", nullable = false, length = 400)
  public String cacheKey;

  @Column(nullable = false, length = 5)
  public String lang;

  @Lob
  @Column(name = "payload_json", nullable = false)
  public String payloadJson;

  /** template or the model name, so a cached line can be traced to its author. */
  @Column(length = 60)
  public String generator = "template";

  @Column(name = "created_at", nullable = false)
  public Instant createdAt = Instant.now();
}
