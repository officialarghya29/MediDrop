package com.medidrop.prescription;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "prescriptions", indexes = @Index(name = "idx_prescription_customer", columnList = "customer_id"))
@Getter
@Setter
public class Prescription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "storage_key", nullable = false, unique = true, length = 400)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PrescriptionStatus status = PrescriptionStatus.UPLOADED;

    @Column(name = "pharmacy_id")
    private Long pharmacyId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(length = 500)
    private String notes;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_by_registration", length = 80)
    private String reviewedByRegistration;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "ocr_extracted_text", length = 4000)
    private String ocrExtractedText;

    @Column(name = "ocr_confidence", precision = 5, scale = 2)
    private java.math.BigDecimal ocrConfidence;

    @Column(name = "is_demo", nullable = false)
    private boolean demo = false;
}
