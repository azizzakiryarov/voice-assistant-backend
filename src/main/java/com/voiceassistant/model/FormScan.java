package com.voiceassistant.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Data;

import java.time.Instant;

@Entity
@Data
public class FormScan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant approvedAt;
    private String detectedFormType;
    private String childName;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String extractedText;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String draftJson;

    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormScanStatus status;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private AppUser owner;
}
