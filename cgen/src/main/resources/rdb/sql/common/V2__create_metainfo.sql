CREATE SEQUENCE nomad_parsers_id_seq;
create table nomad_parsers (
       parser_id integer not null default nextval('nomad_parsers_id_seq'),
       name VARCHAR(128) NOT NULL,
       version VARCHAR(128) NOT NULL,
       PRIMARY KEY (parser_id)
);

CREATE SEQUENCE nomad_parsing_session_id_seq;
create table nomad_parsing_session (
       session_id integer not null default nextval('nomad_parsing_session_id_seq'),
       name VARCHAR(128),
       PRIMARY KEY (session_id)
);

create table parsing_statistics (
       parser_id integer not null,
       session_id integer not null,
       metainfo_id integer not null,
       count integer not null,
       FOREIGN KEY(parser_id) REFERENCES nomad_parsers (parser_id),
       FOREIGN KEY(session_id) REFERENCES nomad_parsing_session (session_id),
       FOREIGN KEY(metainfo_id) REFERENCES nomad_meta_infos (metainfo_id),
);
