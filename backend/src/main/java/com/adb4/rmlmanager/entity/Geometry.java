package com.adb4.rmlmanager.entity;

import com.adb4.rmlmanager.enums.GeometryFileType;
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
public class Geometry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid",  nullable = false, updatable = false)
    private UUID id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private GeometryFileType fileType;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "s3_bucket", nullable = false)
    private String s3Bucket;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "vertex_count")
    private Integer vertexCount;

    @Column(name = "polygon_count")
    private Integer polygonCount;

    @Column(name = "triangle_count")
    private Integer triangleCount;

    @Column(name = "has_materials")
    private Boolean hasMaterials;

    @Column(name = "has_textures")
    private Boolean hasTextures;

    @Column(name = "has_animation")
    private Boolean hasAnimation;

    @Column(name = "version")
    private Integer version;

    @Column(name = "is_latest")
    private Boolean isLatest;

    @Column(name = "checksum")
    private String checksum;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
