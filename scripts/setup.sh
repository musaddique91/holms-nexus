#!/usr/bin/env bash
# Creates .env from .env.example with freshly generated secrets, and config/holmes.yml.
# Safe to re-run: an existing .env is never overwritten.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env ]; then
  echo ".env already exists; leaving it untouched. Delete it first to generate a new one."
  exit 0
fi
command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }

rand() { openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c "$1"; }

ADMIN_PASSWORD="$(rand 20)"
JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
POSTGRES_PASSWORD="$(rand 28)"
WEBHOOK_TOKEN="$(rand 40)"
# OpenObserve requires upper case, lower case, a digit and a special character.
OPENOBSERVE_PASSWORD="$(rand 20)aA1!"

set_var() { # set_var KEY VALUE: replaces the KEY=... line in .env
  local key="$1" value="$2" tmp
  tmp="$(mktemp)"
  awk -v k="$key" -v v="$value" 'BEGIN{FS=OFS="="} $1==k {print k "=" v; next} {print}' .env > "$tmp"
  mv "$tmp" .env
}

cp .env.example .env
chmod 600 .env
set_var HOLMES_ADMIN_PASSWORD "$ADMIN_PASSWORD"
set_var HOLMES_JWT_SECRET "$JWT_SECRET"
set_var POSTGRES_PASSWORD "$POSTGRES_PASSWORD"
set_var HOLMES_WEBHOOK_TOKEN "$WEBHOOK_TOKEN"
set_var OPENOBSERVE_PASSWORD "$OPENOBSERVE_PASSWORD"

[ -f config/holmes.yml ] || cp config/holmes.example.yml config/holmes.yml

cat <<EOF

.env created with generated secrets.

  MVX Nexus      http://localhost:8080    user: admin              password: $ADMIN_PASSWORD
  OpenObserve    http://localhost:5080    user: admin@example.com  password: $OPENOBSERVE_PASSWORD

These are shown once; they are also in .env (HOLMES_ADMIN_PASSWORD, OPENOBSERVE_PASSWORD).

Next:
  docker compose up -d --build
EOF
