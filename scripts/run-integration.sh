#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

TS=$(date +%Y%m%d_%H%M%S)
LOG_DIR="$(pwd)/logs"
mkdir -p "$LOG_DIR"

: "${USE_TESTCONTAINERS:=0}"

if [[ "$USE_TESTCONTAINERS" == "1" ]]; then
	echo "Running integration tests with Testcontainers (requires Docker)..."
	# Ensure docker-java/Testcontainers sees the server API version
	if command -v docker >/dev/null 2>&1; then
		: "${DOCKER_API_VERSION:=$(docker version --format '{{.Server.APIVersion}}' 2>/dev/null || true)}"
		if [[ -n "${DOCKER_API_VERSION:-}" ]]; then
			export DOCKER_API_VERSION
			echo "Exported DOCKER_API_VERSION=$DOCKER_API_VERSION"
		fi
	fi
	./mvnw -Pit -Dit.useTestcontainers=true -Ddocker.api.version=1.44 verify 2>&1 | tee "$LOG_DIR/mvn_verify_it_testcontainers_${TS}.log"

	MVN_EXIT=${PIPESTATUS[0]:-0}

	# If Docker is available, collect logs from Testcontainers-created containers
	if command -v docker >/dev/null 2>&1; then
		# containers started by Testcontainers are labeled; collect their logs
		CONTAINER_IDS=$(docker ps -q --filter "label=org.testcontainers=true" || true)
		if [[ -n "${CONTAINER_IDS}" ]]; then
			for cid in $CONTAINER_IDS; do
				name=$(docker inspect --format '{{.Name}}' "$cid" | sed 's:^/::')
				out="$LOG_DIR/testcontainer_${TS}_${name}_${cid}.log"
				echo "Saving docker logs for $name ($cid) to $out"
				docker logs --since 0 "$cid" >"$out" 2>&1 || true
			done
		fi
	fi

	exit $MVN_EXIT
fi

echo "Running integration tests against external Postgres (docker compose)..."
./scripts/run_with_external_postgres.sh 2>&1 | tee "$LOG_DIR/mvn_verify_it_external_${TS}.log"
exit ${PIPESTATUS[0]:-0}
