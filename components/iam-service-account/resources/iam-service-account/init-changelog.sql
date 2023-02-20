--liquibase formatted sql

--changeset kjothen:1
CREATE SEQUENCE service_account_id_seq;
CREATE TABLE service_account(
  unique_id NUMERIC(21,0) NOT NULL DEFAULT nextval('service_account_id_seq'),
  name text,
  project_id int,
  email text,
  display_name text,
  description text,
  enabled boolean);
ALTER SEQUENCE service_account_id_seq OWNED BY service_account.unique_id;
