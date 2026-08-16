#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"
PROJECT_NAME="${SMOKE_PROJECT_NAME:-linkforge-smoke}"
ENV_FILE="${SMOKE_ENV_FILE:-}"
KEEP_STACK="${SMOKE_KEEP_STACK:-false}"

if [[ "${PROJECT_NAME}" == "linkforge" ]]; then
  echo "SMOKE_PROJECT_NAME must not target the regular linkforge development stack" >&2
  exit 2
fi

export JWT_SECRET="${JWT_SECRET:-smoke-jwt-secret-that-is-longer-than-thirty-two-bytes}"
export API_KEY_CURRENT_KEY_ID="${API_KEY_CURRENT_KEY_ID:-smoke-v1}"
export API_KEY_CURRENT_PEPPER="${API_KEY_CURRENT_PEPPER:-smoke-api-key-pepper-that-is-independent-and-long}"
export API_KEY_LEGACY_JWT_FALLBACK_ENABLED="${API_KEY_LEGACY_JWT_FALLBACK_ENABLED:-false}"
export ANALYTICS_SALT="${ANALYTICS_SALT:-smoke-analytics-salt-that-is-not-a-production-secret}"
export APP_STRICT_CONFIG="${APP_STRICT_CONFIG:-true}"
export LINKFORGE_HTTP_BIND="${LINKFORGE_HTTP_BIND:-127.0.0.1}"
export LINKFORGE_HTTP_PORT="${LINKFORGE_HTTP_PORT:-18080}"

COMPOSE=(docker compose -p "${PROJECT_NAME}" -f "${COMPOSE_FILE}")
if [[ -n "${ENV_FILE}" ]]; then
  COMPOSE+=(--env-file "${ENV_FILE}")
fi

cleanup() {
  local status=$?
  if (( status != 0 )); then
    "${COMPOSE[@]}" ps || true
    "${COMPOSE[@]}" logs --no-color --tail=200 || true
  fi
  if [[ "${KEEP_STACK}" != "true" ]]; then
    "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  fi
  exit "${status}"
}
trap cleanup EXIT

cd "${REPO_ROOT}"
"${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" up --build --detach --wait --wait-timeout "${SMOKE_WAIT_SECONDS:-360}"

BASE_URL="${SMOKE_BASE_URL:-http://127.0.0.1:${LINKFORGE_HTTP_PORT}}"
curl --fail --silent --show-error "${BASE_URL}/" | grep -q '<div id="app"></div>'
curl --fail --silent --show-error "${BASE_URL}/healthz" | grep -q '"status":"UP"'
curl --fail --silent --show-error "${BASE_URL}/api/v1/auth/csrf" | grep -q '"code":0'

echo "Compose smoke passed at ${BASE_URL}"
