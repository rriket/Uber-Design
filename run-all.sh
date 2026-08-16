#!/usr/bin/env bash
# Convenience script: starts all backing services + all microservices.
#
# Usage:
#   ./run-all.sh          # foreground (Ctrl-C stops everything)
#   ./run-all.sh --detach # background, logs written to ./logs/
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p logs

echo "▶ Starting infrastructure (Postgres, Redis, Keycloak)…"
docker compose up -d
echo "  ✔ Waiting 20s for Keycloak realm import…"
sleep 20

DETACH=${1:-}
services=(api-gateway ride-service location-service matching-service notification-service ratings-service payments-service)

if [[ "$DETACH" == "--detach" ]]; then
  for s in "${services[@]}"; do
    echo "▶ [$s] starting (detached, logs → logs/$s.log)"
    nohup ./mvnw -pl "$s" spring-boot:run > "logs/$s.log" 2>&1 &
    echo $! > "logs/$s.pid"
    sleep 4
  done
  echo "All services launched. tail -f logs/*.log to watch them."
else
  echo "▶ Launching services in the foreground (Ctrl-C to stop all)…"
  trap 'jobs -p | xargs -r kill' EXIT
  for s in "${services[@]}"; do
    (./mvnw -pl "$s" spring-boot:run 2>&1 | sed "s/^/[$s] /") &
    sleep 4
  done
  wait
fi
