package com.medidrop.prescription;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "prescription_reviews",
        indexes = @Index(name = "idx_prescription_review_rx", columnList = "prescription_id"))
@Getter
@Setter
public class PrescriptionReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Column(name = "pharmacist_user_id", nullable = false)
    private Long pharmacistUserId;

    @Column(nullable = false, length = 20)
    private String decision;

    @Column(length = 500)
    private String comments;

    @Column(name = "verified_items", length = 2000)
    private String verifiedItems;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt = Instant.now();
}
