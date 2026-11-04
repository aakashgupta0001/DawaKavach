package com.dawacheck.repo;

import com.dawacheck.domain.GenericProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GenericProductRepository extends JpaRepository<GenericProduct, Long> {
  /** Everything sharing the brand's salts; the exact-strength test happens in Java. */
  List<GenericProduct> findBySaltIds(String saltIds);
}
