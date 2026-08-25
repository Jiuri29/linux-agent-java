#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME=ops-agent
APP_HOME=/opt/kylin-aiops-agent
DATA_DIR=/var/lib/kylin-aiops-agent
LOG_DIR=/var/log/kylin-aiops-agent
ENV_FILE=/etc/default/kylin-aiops-agent

if [[ $EUID -ne 0 ]]; then
  echo "please run as root" >&2
  exit 1
fi

systemctl disable --now "${SERVICE_NAME}.service" 2>/dev/null || true
rm -f /etc/systemd/system/${SERVICE_NAME}.service
systemctl daemon-reload || true

rm -rf "$APP_HOME" "$ENV_FILE"
echo "data preserved at ${DATA_DIR}; remove manually if desired: rm -rf ${DATA_DIR} ${LOG_DIR}"
echo "uninstalled."
