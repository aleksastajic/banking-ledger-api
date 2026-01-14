#!/usr/bin/env bash
set -euo pipefail

# Starts postgres via docker-compose, waits for readiness, then runs integration tests (Maven profile `it`).
cd "$(dirname "$0")/.."

: "${POSTGRES_HOST_PORT:=5433}"

export POSTGRES_HOST_PORT
echo "Starting Postgres via docker-compose..."
docker compose up -d db --remove-orphans

echo "Waiting for Postgres to be ready..."
TRIES=0
until docker compose exec -T db pg_isready -U postgres -d ledger >/dev/null 2>&1; do
  TRIES=$((TRIES+1))
  if [ "$TRIES" -gt 60 ]; then
    echo "Postgres did not become ready in time" >&2
    docker compose logs db
    exit 1
  fi
  sleep 1
done

echo "Running integration tests against external Postgres..."
./mvnw -Pit verify

echo "Tests finished. You can stop postgres with: docker compose down"
