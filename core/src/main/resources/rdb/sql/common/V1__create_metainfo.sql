CREATE SEQUENCE nomad_meta_infos_metainfo_id_seq;
CREATE TABLE nomad_meta_infos (
        metainfo_id integer not null default nextval('nomad_meta_infos_metainfo_id_seq'),
        gid VARCHAR(39) NOT NULL,
        name VARCHAR(128),
        description TEXT,
        kind_gid VARCHAR(39),
        units VARCHAR(128),
        super_kind_gid VARCHAR(39),
        dtype_str VARCHAR(128),
        repeats BOOLEAN,
        shape_txt TEXT,
        extra_args_txt TEXT,
        public BOOLEAN,
        public_override_gid VARCHAR(39),
        PRIMARY KEY (metainfo_id),
        FOREIGN KEY(kind_gid) REFERENCES nomad_meta_infos (gid),
        FOREIGN KEY(super_kind_gid) REFERENCES nomad_meta_infos (gid),
        FOREIGN KEY(public_override_gid) REFERENCES nomad_meta_infos (gid)
);


CREATE TABLE nomad_meta_info_parents (
        metainfo_id INTEGER NOT NULL,
        parent_metainfo_id INTEGER NOT NULL,
        FOREIGN KEY(metainfo_id) REFERENCES nomad_meta_infos (metainfo_id),
        FOREIGN KEY(parent_metainfo_id) REFERENCES nomad_meta_infos (metainfo_id)
);

CREATE TABLE nomad_meta_info_anchestors (
        metainfo_id INTEGER NOT NULL,
        anchestor_metainfo_id INTEGER NOT NULL,
        FOREIGN KEY(metainfo_id) REFERENCES nomad_meta_infos (metainfo_id),
        FOREIGN KEY(anchestor_metainfo_id) REFERENCES nomad_meta_infos (metainfo_id)
);

CREATE SEQUENCE nomad_meta_info_versions;
CREATE TABLE nomad_meta_info_versions (
       version_id INTEGER NOT NULL,
       name VARCHAR(128) NOT NULL
);

CREATE TABLE nomad_meta_info_version (
       version_id INTEGER NOT NULL,
       metainfo_id INTEGER NOT NULL
);
