--liquibase formatted sql

--changeset kjothen:1
create table service-account(id serial, description varchar(255), name varchar(255));
