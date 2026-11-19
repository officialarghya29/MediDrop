package com.medidrop.medicine;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "manufacturers")
@Getter
@Setter
public class Manufacturer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String name;

    @Column(length = 200)
    private String website;

    @Column(nullable = false)
    private boolean active = true;
}
