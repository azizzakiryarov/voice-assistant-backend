package com.voiceassistant.repository;

import com.voiceassistant.model.FormScan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FormScanRepository extends JpaRepository<FormScan, Long> {
    Optional<FormScan> findByIdAndOwnerId(Long id, Long ownerId);
}
