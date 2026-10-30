#!/bin/sh
set -eu

# Render and Supabase often provide PostgreSQL URLs as postgresql://... or
# postgres://... URIs. Spring JDBC expects jdbc:postgresql://... and receives
# credentials through DATABASE_USERNAME and DATABASE_PASSWORD separately.
case "${DATABASE_URL:-}" in
  postgresql://*|postgres://*)
    database_uri="${DATABASE_URL#*://}"
    case "$database_uri" in
      *@*) database_uri="${database_uri##*@}" ;;
    esac
    DATABASE_URL="jdbc:postgresql://${database_uri}"
    export DATABASE_URL
    ;;
esac

exec java -jar /app/app.jar
