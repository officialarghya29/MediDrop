package com.medidrop.pharmacy;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "pharmacy_operating_hours",
        uniqueConstraints = @UniqueConstraint(columnNames = {"pharmacy_id", "day_of_week"}))
@Getter
@Setter
public class PharmacyOperatingHour extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pharmacy_id", nullable = false)
    private Long pharmacyId;

    /** 1 = Monday ... 7 = Sunday */
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime = LocalTime.of(9, 0);

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime = LocalTime.of(21, 0);

    @Column(nullable = false)
    private boolean closed = false;
}
