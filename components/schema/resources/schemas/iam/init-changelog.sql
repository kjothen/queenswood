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

--changeset kjothen:2
ALTER TABLE service_account
  ALTER COLUMN name SET NOT NULL,
  ALTER COLUMN project_id SET NOT NULL,
  ALTER COLUMN email SET NOT NULL,
  ALTER COLUMN disabled SET NOT NULL;
CREATE UNIQUE INDEX service_account_name_active_unique
  ON service_account(name) WHERE deleted_at IS NULL;
