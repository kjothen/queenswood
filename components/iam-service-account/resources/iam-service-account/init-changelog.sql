--liquibase formatted sql

--changeset kjothen:1
CREATE TABLE service_account(id serial, description varchar(255), name varchar(255));
