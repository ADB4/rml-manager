package com.adb4.rmlmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "subcategory", nullable = false)
    private String subcategory;

    @Column(name = "description")
    private String description;

    @Column(name = "shader")
    private String shader;

    @Column(name = "material")
    private String material;

    @Column(name = "has_animation")
    private boolean hasAnimation;

    @Column(name = "status")
    private String status;

    @Column(name = "version")
    private Integer version;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
