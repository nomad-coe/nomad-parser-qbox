CREATE TABLE info_kinds (
        gid VARCHAR(39) NOT NULL,
        name VARCHAR(128),
        description TEXT,
        kind_gid VARCHAR(39),
        units VARCHAR(128),
        super_kind_gid VARCHAR(39),
        "dtypeStr" VARCHAR(128),
        repeats BOOLEAN,
        shape_txt TEXT,
        extra_args_txt TEXT,
        public BOOLEAN,
        public_override_gid VARCHAR(39),
        PRIMARY KEY (gid),
        FOREIGN KEY(kind_gid) REFERENCES info_kinds (gid),
        FOREIGN KEY(super_kind_gid) REFERENCES info_kinds (gid),
        FOREIGN KEY(public_override_gid) REFERENCES info_kinds (gid)
)

CREATE TABLE info_kind_anchestors (
        info_kind_gid INTEGER NOT NULL,
        anchestor_gid INTEGER NOT NULL,
        FOREIGN KEY(info_kind_gid) REFERENCES info_kinds (gid),
        FOREIGN KEY(anchestor_gid) REFERENCES info_kinds (gid)
)
