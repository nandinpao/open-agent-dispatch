-- Optional helper for DBA-managed environments.
-- Run from a maintenance database, for example:
--   psql -h localhost -U postgres -d postgres -f 00_create_database.sql
-- Adjust owner/password policy according to the customer's database standard.

-- create user admin with password '123456';
-- create database aeg_core owner admin encoding 'UTF8';
