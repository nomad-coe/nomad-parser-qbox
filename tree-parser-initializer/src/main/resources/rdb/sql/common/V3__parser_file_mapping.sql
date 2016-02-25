CREATE SEQUENCE parser_id_seq;
CREATE TABLE parser (
    parser_id INTEGER NOT NULL DEFAULT nextval('parser_id_seq'),
    name VARCHAR(128),
    version VARCHAR(39),
    extra TEXT,
    PRIMARY KEY (parser_id)
);
CREATE SEQUENCE tree_parser_id_seq;
CREATE TABLE tree_parser(
    tree_parser_id INTEGER NOT NULL DEFAULT nextval('tree_parser_id_seq'),
    parser_id INTEGER NOT NULL,
    PRIMARY KEY (tree_parser_id),
    FOREIGN KEY(parser_id) REFERENCES parser (parser_id)
);
CREATE SEQUENCE parser_file_mapping_id_seq;
CREATE TABLE parser_file_mapping(
    parser_file_mapping_id INTEGER NOT NULL DEFAULT nextval('parser_file_mapping_id_seq'),
    tree_parser_id INTEGER NOT NULL,
    uri VARCHAR(128),
    extraInfo TEXT,
    PRIMARY KEY (parser_file_mapping_id),
    FOREIGN KEY(tree_parser_id) REFERENCES tree_parser (tree_parser_id)
);