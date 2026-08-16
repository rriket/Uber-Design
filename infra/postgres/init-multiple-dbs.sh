#!/bin/bash
set -e

# Creates the additional databases listed in POSTGRES_MULTIPLE_DATABASES
# (comma-separated), each owned by POSTGRES_USER.

if [ -n "$POSTGRES_MULTIPLE_DATABASES" ]; then
  IFS=',' read -ra DBS <<< "$POSTGRES_MULTIPLE_DATABASES"
  for db in "${DBS[@]}"; do
    db_trimmed=$(echo "$db" | xargs)
    echo "Creating database '$db_trimmed'..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
      SELECT 'CREATE DATABASE $db_trimmed OWNER $POSTGRES_USER'
      WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db_trimmed')\gexec
EOSQL
  done
fi
