#!/bin/bash

postgres -D $PGDATA 2>&1 &

until psql -h localhost -U "postgres" -c '\q'; do
  >&2 echo "Postgres is unavailable - sleeping for 3 secs"
  sleep 3
done

>&2 echo "Postgres is up - executing init commands"
psql --command "CREATE USER pguser WITH SUPERUSER PASSWORD 'pguser';"
psql --command "CREATE DATABASE pgdb;"
psql --command "GRANT ALL PRIVILEGES ON DATABASE pgdb TO pguser;"

psql -U pguser -d pgdb -a -f /app/bar-table-inserts.sql