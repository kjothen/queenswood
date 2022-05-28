--liquibase formatted sql

--changeset kjothen:1
create table test(id serial, name varchar(255));

--changeset kjothen:2
insert into test(name) values('hello');
insert into test(name) values('world');
