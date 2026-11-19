# Failure demo for Windows. Start the stack first:
#   Copy-Item .env.example .env
#   docker compose up -d --build --wait
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$gateway = "http://localhost:8080"
if ($env:SERVER_PORT_GATEWAY) { $gateway = "http://localhost:$($env:SERVER_PORT_GATEWAY)" }

function Post-Demande {
    $response = Invoke-WebRequest -Uri "$gateway/api/v1/demandes" -Method Post `
        -ContentType "application/json" -InFile "tests-manuels/demande.json" -UseBasicParsing
    Write-Output "HTTP $($response.StatusCode)"
    Write-Output $response.Content
}

function Pause-Demo([string]$message) {
    Write-Output ""
    Write-Output $message
    Read-Host "Next scenario? Press enter"
}

Write-Output "1) Don & Stock stopped. A new request stays EN_ATTENTE and answers 202."
docker compose stop service-don-stock
Post-Demande
Pause-Demo "Starting Don & Stock again."

docker compose start service-don-stock
docker compose up -d --wait --wait-timeout 180 service-don-stock

Write-Output "2) CNTS slow, then reset."
$env:CNTS_LATENCY_MS = "5000"
docker compose up -d --no-deps --force-recreate service-cnts
Remove-Item Env:CNTS_LATENCY_MS
docker compose up -d --no-deps --force-recreate service-cnts
Write-Output "Watch logs with: docker compose logs -f service-cnts"
Pause-Demo "3) Repeat the same hospital request. Each HTTP create currently gets its own id."

Post-Demande
Post-Demande
if (Get-Command grpcurl -ErrorAction SilentlyContinue) {
    $payload = '{"demande_id":"demo-same-id","lieu_id":"HOP-SFX-01","groupe_sanguin":"O_NEG","composant":"CGR","quantite":1,"urgence":"HAUTE"}'
    grpcurl -plaintext -d $payload "localhost:9090" donstock.v1.DonStockService/VerifierEtReserver
    grpcurl -plaintext -d $payload "localhost:9090" donstock.v1.DonStockService/VerifierEtReserver
} else {
    Write-Output "grpcurl is not installed. Repeat VerifierEtReserver with demande_id demo-same-id once the RPC is implemented."
}

Pause-Demo "4) CNTS stopped, then started again."
docker compose stop service-cnts
Write-Output "CNTS is stopped. Finish a donation in Donneurs, then the script starts CNTS again."
docker compose start service-cnts
Write-Output "Done."
