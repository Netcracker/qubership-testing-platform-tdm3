DROP TABLE IF EXISTS test_data_cleanup_config;
CREATE TABLE test_data_cleanup_config(
    id uuid NOT NULL,
    enabled boolean NOT NULL,
    schedule character varying,
    search_sql character varying,
    search_class character varying,
    shared boolean,
    CONSTRAINT "U_CREATE_TABLE_TEST_DATA_CLEANUP_CONFIG(ID)" UNIQUE (id)
);

DROP TABLE IF EXISTS test_data_cleanup_statistics;
CREATE TABLE test_data_cleanup_statistics(
    id bigint NOT NULL,
    table_name character varying NOT NULL,
    count integer NOT NULL DEFAULT 1,
    occupied_date date NOT NULL,
    cleanup_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
