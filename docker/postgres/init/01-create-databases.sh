#!/bin/sh
# Database-per-service (ADR-011): each service gets its own database and user,
# and no other role can connect to it.
set -eu

create_service_database() {
  db_name="$1"
  db_user="$2"
  db_password="$3"

  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
	CREATE USER ${db_user} WITH PASSWORD '${db_password}';
	CREATE DATABASE ${db_name} OWNER ${db_user};
	REVOKE ALL ON DATABASE ${db_name} FROM PUBLIC;
EOSQL
}

create_service_database clients_db clients_user "$CLIENTS_DB_PASSWORD"
create_service_database accounts_db accounts_user "$ACCOUNTS_DB_PASSWORD"
