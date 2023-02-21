--liquibase formatted sql

--changeset kjothen:1
CREATE SEQUENCE service_account_id_seq;
CREATE TABLE service_account(
  unique_id NUMERIC(21,0) NOT NULL DEFAULT nextval('service_account_id_seq'),
  name text,
  project_id text,
  email text,
  display_name text,
  description text,
  disabled boolean,
  created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now()),
  updated_at TIMESTAMPTZ,
  deleted_at TIMESTAMPTZ);
ALTER SEQUENCE service_account_id_seq OWNED BY service_account.unique_id;
