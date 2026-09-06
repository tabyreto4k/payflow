#!/usr/bin/env bash
# Self-signed сертификат для nginx на локальном стенде. В репозиторий серты не попадают.
#
#   ./scripts/gen-certs.sh && docker compose up -d --wait
set -euo pipefail

CERTS=$(cd "$(dirname "$0")/.." && pwd)/nginx/certs
DAYS=${CERT_DAYS:-365}

if [ -f "$CERTS/server.crt" ] && [ -f "$CERTS/server.key" ]; then
    echo "Сертификат уже есть: $CERTS/server.crt"
    exit 0
fi

mkdir -p "$CERTS"
openssl req -x509 -nodes -newkey rsa:2048 \
    -days "$DAYS" \
    -keyout "$CERTS/server.key" \
    -out "$CERTS/server.crt" \
    -subj '/CN=localhost' \
    -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' 2>/dev/null

chmod 600 "$CERTS/server.key"
echo "Сертификат на $DAYS дней: $CERTS/server.crt"
