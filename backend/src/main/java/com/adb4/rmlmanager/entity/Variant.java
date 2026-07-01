package com.adb4.rmlmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "variants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_variant_asset_code", columnNames = {"asset_id", "code"})
})
public class Variant extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid",  nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "is_default")
    private Boolean isDefault;
}
