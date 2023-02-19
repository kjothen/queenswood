--liquibase formatted sql

--changeset kjothen:1
CREATE TABLE service_account(
       id serial primary key,
       name text,
       "project-id" int,
       "unique-id" text,
       email text,
       "display-name" text,
       description text,
       disabled boolean
       );
