#!/usr/bin/env bash
# Failure demo. Start the stack first: cp .env.example .env && docker compose up -d --build --wait
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
gateway="http://localhost:${SERVER_PORT_GATEWAY:-8080}"

pause() {
  read -r -p "Next scenario? Press enter. "
}

post_demande() {
  curl -sS -w "\nHTTP %{http_code}\n" -X POST "$gateway/api/v1/demandes" \
    -H "Content-Type: application/json" \
    --data-binary "@tests-manuels/demande.json"
}

echo "1) Don & Stock stopped. A new request stays EN_ATTENTE and answers 202."
docker compose stop service-don-stock
post_demande
pause

echo "Starting Don & Stock again."
docker compose start service-don-stock
docker compose up -d --wait --wait-timeout 180 service-don-stock
pause

echo "2) CNTS slow. Recreate CNTS with CNTS_LATENCY_MS=5000, then watch its logs."
CNTS_LATENCY_MS=5000 docker compose up -d --no-deps --force-recreate service-cnts
echo "Follow logs in another terminal: docker compose logs -f service-cnts"
echo "Resetting CNTS latency to the value in .env."
docker compose up -d --no-deps --force-recreate service-cnts
pause

echo "3) Repeat the same hospital request."
echo "Each HTTP create currently receives its own id. gRPC idempotency uses the same demande_id."
post_demande
post_demande
if command -v grpcurl >/dev/null 2>&1; then
  grpcurl -plaintext -d '{"demande_id":"demo-same-id","lieu_id":"HOP-SFX-01","groupe_sanguin":"O_NEG","composant":"CGR","quantite":1,"urgence":"HAUTE"}' \
    "localhost:${GRPC_PORT:-9090}" donstock.v1.DonStockService/VerifierEtReserver || true
  grpcurl -plaintext -d '{"demande_id":"demo-same-id","lieu_id":"HOP-SFX-01","groupe_sanguin":"O_NEG","composant":"CGR","quantite":1,"urgence":"HAUTE"}' \
    "localhost:${GRPC_PORT:-9090}" donstock.v1.DonStockService/VerifierEtReserver || true
else
  echo "grpcurl is not installed. Repeat VerifierEtReserver with demande_id demo-same-id once the RPC is implemented."
fi
pause

echo "4) CNTS stopped. Restart it afterwards so the retry job can finish a certificate."
docker compose stop service-cnts
echo "CNTS is stopped. Finish a donation in Donneurs while it is down, then:"
echo "  docker compose start service-cnts"
docker compose start service-cnts
echo "Done."
