package com.adb4.rmlmanager.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "texture_sets")
public class TextureSet extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private Variant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lod_id")
    private Lod lod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesh_part_id")
    private MeshPart meshPart;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "texture_set_maps",
            joinColumns = @JoinColumn(name = "texture_set_id"),
            inverseJoinColumns = @JoinColumn(name = "texture_map_id")
    )
    @Builder.Default
    private Set<TextureMap> textureMaps = new HashSet<>();

    // optional in case we want to have an s3 location for a zip archive
    // of texture map
    @Column(name = "s3_key")
    private String s3Key;

    @Column(name = "s3_bucket")
    private String s3Bucket;

    @Column(name = "version")
    private Integer version;

    @Column(name = "is_latest")
    private Boolean isLatest;

    @Column(name = "checksum")
    private String checksum;
}
