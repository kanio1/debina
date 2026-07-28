#!/usr/bin/env bash
set -euo pipefail

NODE_HOME="/home/suso/.nvm/versions/node/v24.18.0"
NODE_BIN="${NODE_HOME}/bin/node"
NPX_CLI="${NODE_HOME}/lib/node_modules/npm/bin/npx-cli.js"

ENV_FILE="${HOME}/.config/debina/postgres-mcp.env"
CONFIG_FILE="${HOME}/.config/debina/dbhub.toml"

MCP_PACKAGE="@bytebase/dbhub@0.24.0"

if [[ ! -x "${NODE_BIN}" ]]; then
  echo "Missing Node executable: ${NODE_BIN}" >&2
  exit 1
fi

if [[ ! -f "${NPX_CLI}" ]]; then
  echo "Missing npx CLI: ${NPX_CLI}" >&2
  exit 1
fi

if [[ ! -r "${ENV_FILE}" ]]; then
  echo "Missing PostgreSQL MCP environment file: ${ENV_FILE}" >&2
  exit 1
fi

if [[ ! -r "${CONFIG_FILE}" ]]; then
  echo "Missing DBHub config: ${CONFIG_FILE}" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

if [[ -z "${DEBINA_MCP_POSTGRES_DSN:-}" ]]; then
  echo "DEBINA_MCP_POSTGRES_DSN is not defined" >&2
  exit 1
fi

NODE_VERSION="$("${NODE_BIN}" --version)"
NODE_ABI="$("${NODE_BIN}" -p 'process.versions.modules')"

if [[ "${NODE_VERSION}" != "v24.18.0" ]]; then
  echo "Unexpected Node version: ${NODE_VERSION}; expected v24.18.0" >&2
  exit 1
fi

if [[ "${NODE_ABI}" != "137" ]]; then
  echo "Unexpected Node ABI: ${NODE_ABI}; expected 137" >&2
  exit 1
fi

export PATH="${NODE_HOME}/bin:/usr/local/bin:/usr/bin:/bin"

CACHE_DIR="${HOME}/.cache/debina-mcp/postgres/${MCP_PACKAGE##*@}/node-v24-abi-${NODE_ABI}"
mkdir -p "${CACHE_DIR}"

export npm_config_cache="${CACHE_DIR}"
export npm_config_update_notifier=false
export npm_config_fund=false
export npm_config_audit=false
export npm_config_progress=false

echo "DBHub runtime: ${NODE_BIN}" >&2
echo "DBHub Node: ${NODE_VERSION}, ABI ${NODE_ABI}" >&2
echo "DBHub package: ${MCP_PACKAGE}" >&2
echo "DBHub cache: ${CACHE_DIR}" >&2

exec "${NODE_BIN}" "${NPX_CLI}" \
  --yes \
  "${MCP_PACKAGE}" \
  --transport stdio \
  --config="${CONFIG_FILE}"
