#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${BASE_URL:-http://127.0.0.1:18080}
SMOKE_PASSWORD=${OPS_AGENT_SMOKE_PASSWORD:-AdminStage17!}
COOKIE=$(mktemp)
trap 'rm -f "$COOKIE"' EXIT

echo "==> health"
curl -fsS "$BASE_URL/api/health" | grep -q '"status":"ok"'

echo "==> bootstrap / init-admin"
INITIALIZED=$(curl -fsS "$BASE_URL/api/auth/bootstrap" | grep -o '"initialized":[a-z]*' | cut -d: -f2)
if [[ "$INITIALIZED" == "false" ]]; then
  curl -fsS -c "$COOKIE" -H 'Content-Type: application/json' \
    -d "{\"username\":\"admin\",\"password\":\"${SMOKE_PASSWORD}\",\"display_name\":\"Smoke Admin\"}" \
    "$BASE_URL/api/auth/init-admin" > /dev/null
else
  curl -fsS -c "$COOKIE" -H 'Content-Type: application/json' \
    -d "{\"username\":\"admin\",\"password\":\"${SMOKE_PASSWORD}\"}" \
    "$BASE_URL/api/auth/login" > /dev/null
fi

CSRF=$(curl -fsS -b "$COOKIE" "$BASE_URL/api/auth/me" 2>/dev/null | grep -o '"csrf_token":"[^"]*"' | cut -d'"' -f4 || true)
if [[ -z "$CSRF" ]]; then
  CSRF=$(curl -fsS -b "$COOKIE" -H 'Content-Type: application/json' \
    -d "{\"username\":\"admin\",\"password\":\"${SMOKE_PASSWORD}\"}" \
    "$BASE_URL/api/auth/login" | grep -o '"csrf_token":"[^"]*"' | cut -d'"' -f4)
  curl -fsS -c "$COOKIE" -H 'Content-Type: application/json' \
    -d "{\"username\":\"admin\",\"password\":\"${SMOKE_PASSWORD}\"}" \
    "$BASE_URL/api/auth/login" > /dev/null
fi

echo "==> authenticated probe"
curl -fsS -b "$COOKIE" "$BASE_URL/api/v1/runtime/context" | grep -q '"hostname"'
curl -fsS -b "$COOKIE" -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' \
  -d '{"intent":"smoke","commands":["df -kP"]}' \
  "$BASE_URL/api/guardrails/evaluate" | grep -q '"overall"'

echo "==> acceptance run"
RUN=$(curl -fsS -b "$COOKIE" -H "X-CSRF-Token: $CSRF" -H 'Content-Type: application/json' \
  -d '{}' "$BASE_URL/api/tests/run")
echo "$RUN" | grep -q '"failed":0'

echo "ALL SMOKE TESTS PASSED"
