package com.medidrop.medicine;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "medicines", indexes = {
        @Index(name = "idx_medicine_name", columnList = "name"),
        @Index(name = "idx_medicine_generic", columnList = "generic_name"),
        @Index(name = "idx_medicine_category", columnList = "category_id")
})
@Getter
@Setter
public class Medicine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 160)
    private String brand;

    @Column(name = "generic_name", length = 200)
    private String genericName;

    @Column(length = 60)
    private String strength;

    @Column(name = "dosage_form", nullable = false, length = 40)
    private String dosageForm;

    @Column(name = "pack_size", length = 80)
    private String packSize;

    @Column(name = "manufacturer_id")
    private Long manufacturerId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "prescription_required", nullable = false)
    private boolean prescriptionRequired = false;

    @Column(length = 2000)
    private String description;

    @Column(length = 1000)
    private String warnings;

    @Column(name = "schedule_class", length = 20)
    private String scheduleClass = "OTC";

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "is_demo", nullable = false)
    private boolean demo = false;
}
