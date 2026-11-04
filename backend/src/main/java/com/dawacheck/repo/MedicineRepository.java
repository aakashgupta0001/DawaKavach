package com.dawacheck.repo;

import com.dawacheck.domain.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {
  Optional<Medicine> findByBrandIgnoreCase(String brand);
}
