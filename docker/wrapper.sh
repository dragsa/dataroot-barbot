#!/bin/bash

# Start the first process
postgres -D $PGDATA >$PGDATA/logfile 2>&1 &
status=$?
if [ $status -ne 0 ]; then
  echo "Failed to start postgres: $status"
  exit $status
fi

until psql -h localhost -U "postgres" -c '\q'; do
  >&2 echo "Postgres is unavailable - sleeping for 3 secs"
  sleep 3
done

# Start the second process
java -jar /app/barbot-assembly-0.1.jar
status=$?
if [ $status -ne 0 ]; then
  echo "Failed to start barbot: $status"
  exit $status
fi

while /bin/true; do
  ps aux |grep postgres |grep -q -v grep
  PROCESS_1_STATUS=$?
  ps aux |grep hls-assembly |grep -q -v grep
  PROCESS_2_STATUS=$?
  # If the greps above find anything, they will exit with 0 status
  # If they are not both 0, then something is wrong
  if [ $PROCESS_1_STATUS -ne 0 -o $PROCESS_2_STATUS -ne 0 ]; then
    echo "One of the processes has already exited."
    exit -1
  fi
  sleep 60
done