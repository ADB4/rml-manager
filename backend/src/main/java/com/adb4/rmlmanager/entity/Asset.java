package com.adb4.rmlmanager.entity;

import com.adb4.rmlmanager.enums.AssetStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.springframework.data.annotation.CreatedBy;
import java.util.UUID;

@Entity
@Audited
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "assets")
public class Asset extends Auditable {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "preview")
    private String preview;

    @Column(name = "title", nullable = false)
    private String title;

    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id", nullable = false)
    private Subcategory subcategory;

    @Column(name = "description")
    private String description;

    @Column(name = "has_animation")
    private boolean hasAnimation;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private AssetStatus status;

    @Column(name = "version")
    private Integer version;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    public Category getCategory() {
        return subcategory.getCategory();
    }
}
