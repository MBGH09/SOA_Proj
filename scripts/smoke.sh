#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

cleanup() {
  docker compose down -v --remove-orphans
}
trap cleanup EXIT

if [ ! -f .env ]; then
  cp .env.example .env
fi

envfile="$(mktemp)"
tr -d '\r' < .env > "$envfile"
set -a
# shellcheck disable=SC1091
. "$envfile"
set +a
rm -f "$envfile"

gateway="http://localhost:${SERVER_PORT_GATEWAY:-8080}"

docker compose up -d --build --wait --wait-timeout 300 --remove-orphans

curl -fsS "$gateway/actuator/health"
echo
curl -fsS -X POST "$gateway/api/v1/donneurs" \
  -H "Content-Type: application/json" \
  --data-binary "@tests-manuels/donneur.json"
echo
curl -fsS -X POST "$gateway/api/v1/demandes" \
  -H "Content-Type: application/json" \
  --data-binary "@tests-manuels/demande.json"
echo
