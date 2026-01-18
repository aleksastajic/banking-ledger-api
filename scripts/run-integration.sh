#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

TS=$(date +%Y%m%d_%H%M%S)
LOG_DIR="$(pwd)/logs"
mkdir -p "$LOG_DIR"

: "${USE_TESTCONTAINERS:=0}"

if [[ "$USE_TESTCONTAINERS" == "1" ]]; then
	echo "Running integration tests with Testcontainers (requires Docker)..."
	./mvnw -Pit -Dit.useTestcontainers=true verify 2>&1 | tee "$LOG_DIR/mvn_verify_it_testcontainers_${TS}.log"
	exit ${PIPESTATUS[0]:-0}
fi

echo "Running integration tests against external Postgres (docker compose)..."
./scripts/run_with_external_postgres.sh 2>&1 | tee "$LOG_DIR/mvn_verify_it_external_${TS}.log"
exit ${PIPESTATUS[0]:-0}
