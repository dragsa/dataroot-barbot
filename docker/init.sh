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
psql --comand "CREATE TABLE bars
               (name varchar(100) not null
               		constraint bars_name_key
               			unique,
               	info_source varchar not null,
               	is_active boolean not null,
               	id serial not null
               		constraint bars_pkey
               			primary key
               );"
psql -U pguser -d pgdb -a -f /a_bars_table_inserts.sql