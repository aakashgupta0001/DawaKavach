package com.dawacheck.repo;

import com.dawacheck.domain.Salt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SaltRepository extends JpaRepository<Salt, Long> {
  Optional<Salt> findByNameIgnoreCase(String name);
}
