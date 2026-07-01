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
public class Variant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid",  nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "color_hex")
    private String colorHex;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "is_default")
    private Boolean isDefault;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
