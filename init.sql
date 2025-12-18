-- init.sql
-- 1. Create the MPT schema if it doesn't already exist.
CREATE SCHEMA IF NOT EXISTS MPT;

-- 2. Grant necessary privileges on the schema to the user defined in POSTGRES_USER.
-- The username comes from your .env file (e.g., 'appuser_dev').
GRANT
ALL
PRIVILEGES
ON
SCHEMA
MPT TO appuser;