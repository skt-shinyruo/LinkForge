#!/usr/bin/env bash
set -euo pipefail

# MySQL official image: scripts in /docker-entrypoint-initdb.d
# only run when the data directory is empty (fresh volume).
#
# Purpose:
# - Create a dedicated DB user for Edge service (public-facing) with minimal privileges (read-only).
#
# Env vars (optional):
# - MYSQL_DATABASE (default: linkforge)
# - MYSQL_EDGE_USER / MYSQL_EDGE_PASSWORD
# - MYSQL_ROOT_PASSWORD (required by image)

db="${MYSQL_DATABASE:-linkforge}"
edge_user="${MYSQL_EDGE_USER:-linkforge_edge}"
edge_password="${MYSQL_EDGE_PASSWORD:-linkforge_edge}"

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<EOSQL
CREATE USER IF NOT EXISTS '${edge_user}'@'%' IDENTIFIED BY '${edge_password}';
GRANT SELECT ON \`${db}\`.\`short_links\` TO '${edge_user}'@'%';
FLUSH PRIVILEGES;
EOSQL

