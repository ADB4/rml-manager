create sequence revinfo_seq start with 1 increment by 50;

create table revinfo (
                         id        bigint  not null,
                         timestamp bigint  not null,
                         user_id   uuid,
                         primary key (id)
);

create table app_users (
                           id         uuid           not null,
                           username   varchar(32)    not null unique,
                           password   varchar(255)   not null,
                           role       varchar(32)    not null check (role in ('ADMIN','USER')),
                           created_at timestamp(6)   not null,
                           updated_at timestamp(6),
                           primary key (id)
);

create table categories (
                            id         uuid         not null,
                            name       varchar(64)  not null,
                            created_at timestamp(6) not null,
                            updated_at timestamp(6),
                            primary key (id),
                            constraint uk_category_name unique (name)
);

create table texture_maps (
                              id          uuid         not null,
                              type        varchar(255) not null check (type in ('ALBEDO','NORMAL','ROUGHNESS','METALLIC','AO','DISPLACEMENT','EMISSIVE','OPACITY')),
                              file_name   varchar(255) not null,
                              file_type   varchar(255) not null check (file_type in ('JPG','JPEG','PNG','GIF','SVG','WEBP','AVIF','TIFF','TIF')),
                              s3_key      varchar(255),
                              s3_bucket   varchar(255),
                              file_size   integer,
                              width       integer,
                              height      integer,
                              uploaded_by uuid         not null,
                              checksum    varchar(255),
                              created_at  timestamp(6) not null,
                              updated_at  timestamp(6),
                              primary key (id)
);

create table subcategories (
                               id          uuid         not null,
                               category_id uuid         not null,
                               name        varchar(64)  not null,
                               created_at  timestamp(6) not null,
                               updated_at  timestamp(6),
                               primary key (id),
                               constraint uk_subcategory_category_name unique (category_id, name),
                               constraint fk_subcategory_category foreign key (category_id) references categories
);

create table assets (
                        id             uuid         not null,
                        code           varchar(32)  not null unique,
                        title          varchar(255) not null,
                        subcategory_id uuid         not null,
                        description    varchar(255),
                        preview        varchar(255),
                        has_animation  boolean,
                        status         varchar(255) check (status in ('DRAFT','PUBLISHED')),
                        version        integer,
                        created_by     uuid         not null,
                        created_at     timestamp(6) not null,
                        updated_at     timestamp(6),
                        primary key (id),
                        constraint fk_asset_subcategory foreign key (subcategory_id) references subcategories
);

create table asset_permissions (
                                   id          uuid         not null,
                                   app_user_id uuid         not null,
                                   asset_id    uuid         not null,
                                   level       varchar(255) not null check (level in ('VIEWER','EDITOR')),
                                   granted_by  uuid         not null,
                                   created_at  timestamp(6) not null,
                                   primary key (id),
                                   constraint uk_asset_permission_user_asset unique (app_user_id, asset_id)
);

create table lods (
                      id       uuid         not null,
                      asset_id uuid         not null,
                      level    integer      not null,
                      created_at timestamp(6) not null,
                      updated_at timestamp(6),
                      primary key (id),
                      constraint uk_lod_asset_level unique (asset_id, level),
                      constraint fk_lod_asset foreign key (asset_id) references assets
);

create table mesh_parts (
                            id       uuid         not null,
                            asset_id uuid         not null,
                            code     varchar(64)  not null,
                            shader   varchar(255),
                            material varchar(255),
                            created_at timestamp(6) not null,
                            updated_at timestamp(6),
                            primary key (id),
                            constraint uk_mesh_parts_asset_code unique (asset_id, code),
                            constraint fk_mesh_part_asset foreign key (asset_id) references assets
);

create table variants (
                          id           uuid          not null,
                          asset_id     uuid          not null,
                          code         varchar(32)   not null,
                          display_name varchar(255)  not null,
                          description  varchar(1024),
                          color_hex    varchar(6),
                          sort_order   integer,
                          is_default   boolean,
                          created_at   timestamp(6)  not null,
                          updated_at   timestamp(6),
                          primary key (id),
                          constraint uk_variant_asset_code unique (asset_id, code),
                          constraint fk_variant_asset foreign key (asset_id) references assets
);

create table geometries (
                            id             uuid          not null,
                            lod_id         uuid          not null,
                            mesh_part_id   uuid,
                            file_name      varchar(128)  not null,
                            file_type      varchar(255)  not null check (file_type in ('FBX','GLB','GLTF','OBJ','BLEND')),
                            s3_key         varchar(128)  not null,
                            s3_bucket      varchar(128)  not null,
                            file_size      bigint        not null,
                            content_type   varchar(255),
                            vertex_count   integer,
                            polygon_count  integer,
                            triangle_count integer,
                            has_materials  boolean,
                            has_textures   boolean,
                            has_animation  boolean,
                            version        integer,
                            is_latest      boolean,
                            checksum       varchar(255),
                            uploaded_by    uuid          not null,
                            created_at     timestamp(6)  not null,
                            updated_at     timestamp(6),
                            primary key (id),
                            constraint fk_geometry_lod       foreign key (lod_id)       references lods,
                            constraint fk_geometry_mesh_part foreign key (mesh_part_id) references mesh_parts
);

create table texture_sets (
                              id           uuid         not null,
                              variant_id   uuid         not null,
                              lod_id       uuid,
                              mesh_part_id uuid,
                              s3_key       varchar(255),
                              s3_bucket    varchar(255),
                              version      integer,
                              is_latest    boolean,
                              checksum     varchar(255),
                              created_at   timestamp(6) not null,
                              updated_at   timestamp(6),
                              primary key (id),
                              constraint fk_texture_set_variant   foreign key (variant_id)   references variants,
                              constraint fk_texture_set_lod       foreign key (lod_id)       references lods,
                              constraint fk_texture_set_mesh_part foreign key (mesh_part_id) references mesh_parts
);

create table texture_set_maps (
                                  texture_set_id uuid not null,
                                  texture_map_id uuid not null,
                                  primary key (texture_map_id, texture_set_id),
                                  constraint fk_texture_set_maps_map foreign key (texture_map_id) references texture_maps,
                                  constraint fk_texture_set_maps_set foreign key (texture_set_id) references texture_sets
);

CREATE UNIQUE INDEX uk_geometry_latest_part
    ON geometries (lod_id, mesh_part_id, file_type)
    WHERE is_latest = true AND mesh_part_id IS NOT NULL;

CREATE UNIQUE INDEX uk_geometry_latest_baked
    ON geometries (lod_id, file_type)
    WHERE is_latest = true AND mesh_part_id IS NULL;

CREATE UNIQUE INDEX uk_texture_set_latest
    ON texture_sets (variant_id,
                     COALESCE(lod_id,       '00000000-0000-0000-0000-000000000000'),
                     COALESCE(mesh_part_id, '00000000-0000-0000-0000-000000000000'))
    WHERE is_latest = true;

-- envers audit tables

create table assets_aud (
                            id               uuid    not null,
                            rev              bigint  not null,
                            revtype          smallint,
                            code             varchar(32),
                            title            varchar(255),
                            description      varchar(255),
                            preview          varchar(255),
                            status           varchar(255) check (status in ('DRAFT','PUBLISHED')),
                            has_animation    boolean,
                            version          integer,
                            subcategory_id   uuid,
                            created_by       uuid,
                            code_mod         boolean,
                            title_mod        boolean,
                            description_mod  boolean,
                            preview_mod      boolean,
                            status_mod       boolean,
                            has_animation_mod boolean,
                            version_mod      boolean,
                            subcategory_mod  boolean,
                            created_by_mod   boolean,
                            primary key (rev, id),
                            constraint fk_assets_aud_rev foreign key (rev) references revinfo
);

create table mesh_parts_aud (
                                id           uuid    not null,
                                rev          bigint  not null,
                                revtype      smallint,
                                asset_id     uuid,
                                code         varchar(64),
                                shader       varchar(255),
                                material     varchar(255),
                                asset_mod    boolean,
                                code_mod     boolean,
                                shader_mod   boolean,
                                material_mod boolean,
                                primary key (rev, id),
                                constraint fk_mesh_parts_aud_rev foreign key (rev) references revinfo
);

create table variants_aud (
                              id               uuid    not null,
                              rev              bigint  not null,
                              revtype          smallint,
                              asset_id         uuid,
                              code             varchar(32),
                              display_name     varchar(255),
                              description      varchar(1024),
                              color_hex        varchar(6),
                              sort_order       integer,
                              is_default       boolean,
                              asset_mod        boolean,
                              code_mod         boolean,
                              display_name_mod boolean,
                              description_mod  boolean,
                              color_hex_mod    boolean,
                              sort_order_mod   boolean,
                              is_default_mod   boolean,
                              primary key (rev, id),
                              constraint fk_variants_aud_rev foreign key (rev) references revinfo
);