package com.medidrop.audit;

import com.medidrop.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
public class SystemSetting extends BaseEntity {

    @Id
    @Column(name = "setting_key", length = 80)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 1000)
    private String value;

    @Column(length = 300)
    private String description;

    @Column(name = "is_public", nullable = false)
    private boolean publicSetting = false;
}
