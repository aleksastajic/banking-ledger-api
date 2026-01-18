#!/usr/bin/env bash
set -euo pipefail

# Ensure we fail the script if any piped command fails (e.g., mvn failing under tee).
set -o pipefail

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

if [[ -t 1 ]]; then
  mkdir -p logs
  LOG_FILE="logs/it-$(date -u +%Y%m%dT%H%M%SZ).log"
  echo "Logging to ${LOG_FILE}"
  ./mvnw -Pit verify 2>&1 | tee "${LOG_FILE}"
else
  ./mvnw -Pit verify
fi

echo "Tests finished. You can stop postgres with: docker compose down"
