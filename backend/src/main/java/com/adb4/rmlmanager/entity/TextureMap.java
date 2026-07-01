package com.adb4.rmlmanager.entity;

import com.adb4.rmlmanager.enums.TextureFileType;
import com.adb4.rmlmanager.enums.TextureMapType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "texture_maps")
public class TextureMap extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @ManyToMany(mappedBy = "textureMaps", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TextureSet> textureSets = new HashSet<>();

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TextureMapType type;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TextureFileType fileType;

    @Column(name = "s3_key")
    private String s3Key;

    @Column(name = "s3_bucket")
    private String s3Bucket;

    @Column(name = "file_size")
    private Integer fileSize;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @CreatedBy
    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "checksum")
    private String checksum;
}
