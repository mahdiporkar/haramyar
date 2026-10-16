#!/bin/sh
# Creates one database and one role per service (least privilege: no service can read another service's data).
set -eu
create() {
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE ROLE $1 LOGIN PASSWORD '$2';
CREATE DATABASE $1 OWNER $1;
REVOKE ALL ON DATABASE $1 FROM PUBLIC;
SQL
}
create hive "$HIVE_DB_PASSWORD"
create openfga "$OPENFGA_DB_PASSWORD"
create keycloak "$KEYCLOAK_DB_PASSWORD"
create rozeh "$ROZEH_DB_PASSWORD"
create martyrs "$MARTYRS_DB_PASSWORD"
create shell "$SHELL_DB_PASSWORD"
# Extensions used by the business schemas must be created by a superuser.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname rozeh -c "CREATE EXTENSION IF NOT EXISTS btree_gist;"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname martyrs -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"
