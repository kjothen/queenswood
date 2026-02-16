--liquibase formatted sql

--changeset kjothen:1
CREATE SEQUENCE account_id_seq;
CREATE TABLE account(
  unique_id NUMERIC(21,0) NOT NULL DEFAULT nextval('account_id_seq'),
  account_id text NOT NULL,
  name text,
  status text NOT NULL DEFAULT 'open',
  balance NUMERIC(19,4) NOT NULL DEFAULT 0,
  currency text NOT NULL DEFAULT 'USD',
  created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc', now()),
  updated_at TIMESTAMPTZ,
  deleted_at TIMESTAMPTZ);
ALTER SEQUENCE account_id_seq OWNED BY account.unique_id;
