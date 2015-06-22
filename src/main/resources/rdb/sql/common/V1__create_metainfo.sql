CREATE TABLE nomad_meta_infos (
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
        PRIMARY KEY (gid),
        FOREIGN KEY(kind_gid) REFERENCES nomad_meta_infos (gid),
        FOREIGN KEY(super_kind_gid) REFERENCES nomad_meta_infos (gid),
        FOREIGN KEY(public_override_gid) REFERENCES nomad_meta_infos (gid)
);


CREATE TABLE nomad_meta_info_parents (
        info_kind_gid INTEGER NOT NULL,
        parent_gid INTEGER NOT NULL,
        FOREIGN KEY(info_kind_gid) REFERENCES nomad_meta_infos (gid),
        FOREIGN KEY(parent_gid) REFERENCES nomad_meta_infos (gid)
);


CREATE TABLE nomad_meta_info_anchestors (
        info_kind_gid INTEGER NOT NULL,
        anchestor_gid INTEGER NOT NULL,
        FOREIGN KEY(info_kind_gid) REFERENCES nomad_meta_infos (gid),
        FOREIGN KEY(anchestor_gid) REFERENCES nomad_meta_infos (gid)
);

CREATE TABLE meta_info_version(
       name VARCHAR(128) NOT NULL
);
