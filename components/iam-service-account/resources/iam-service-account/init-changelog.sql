--liquibase formatted sql

--changeset kjothen:1
CREATE TABLE service_account(
       id serial primary key,
       name text,
       project_id int,
       unique_id text,
       email text,
       display_name text,
       description text,
       disabled boolean
       );
