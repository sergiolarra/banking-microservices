#!/bin/sh
# Builds database.sql from the Flyway migrations of both services, so the consolidated
# script never drifts from what the services actually apply (F7-AC4).
# Usage: scripts/build-database-sql.sh   (CI fails if the committed file is out of date)
set -eu

root=$(cd "$(dirname "$0")/.." && pwd)
out="$root/database.sql"

append_migrations() {
  for migration in $(ls "$1"/V*__*.sql | sort -V); do
    printf -- '-- %s\n' "${migration#"$root"/}"
    cat "$migration"
    printf '\n'
  done
}

{
  cat <<'HEADER'
-- banking-microservices: consolidated database script (generated, do not edit by hand).
-- Source: the Flyway migrations of each service; regenerate with scripts/build-database-sql.sh.
--
-- With docker compose this script is NOT needed: each service creates its schema with
-- Flyway on startup. It documents the whole model and allows a manual setup:
--   psql -U postgres -f database.sql
-- One database and one user per service (database-per-service, ADR-011). The passwords
-- are the local development defaults; change them for any other environment.

CREATE ROLE clients_user LOGIN PASSWORD 'clients_pass';
CREATE DATABASE clients_db OWNER clients_user;
REVOKE ALL ON DATABASE clients_db FROM PUBLIC;

CREATE ROLE accounts_user LOGIN PASSWORD 'accounts_pass';
CREATE DATABASE accounts_db OWNER accounts_user;
REVOKE ALL ON DATABASE accounts_db FROM PUBLIC;

-- ============================================================================
-- clients-service
-- ============================================================================
\connect clients_db
SET ROLE clients_user;

HEADER
  append_migrations "$root/clients-service/src/main/resources/db/migration"
  cat <<'MIDDLE'
-- ============================================================================
-- accounts-service
-- ============================================================================
\connect accounts_db
SET ROLE accounts_user;

MIDDLE
  append_migrations "$root/accounts-service/src/main/resources/db/migration"
} > "$out"

echo "Wrote $out"
