package com.dawacheck.repo;

import com.dawacheck.domain.LlmCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LlmCacheRepository extends JpaRepository<LlmCache, Long> {
  Optional<LlmCache> findByCacheKey(String cacheKey);
}
