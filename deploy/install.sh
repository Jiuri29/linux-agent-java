#!/usr/bin/env bash
set -euo pipefail

APP_HOME=/opt/linux-aiops-agent
DATA_DIR=/var/lib/linux-aiops-agent
LOG_DIR=/var/log/linux-aiops-agent
ENV_FILE=/etc/default/linux-aiops-agent
SERVICE_NAME=ops-agent

if [[ $EUID -ne 0 ]]; then
  echo "please run as root" >&2
  exit 1
fi

echo "==> creating service account"
id -u linux-agent &>/dev/null || useradd --system --no-create-home --shell /sbin/nologin linux-agent

echo "==> creating directories"
mkdir -p "$APP_HOME" "$DATA_DIR" "$LOG_DIR"
install -m 644 deploy/ops-agent.service /etc/systemd/system/${SERVICE_NAME}.service

JAR=$(ls target/linux-aiops-agent-*.jar 2>/dev/null | head -n1 || true)
[[ -n "$JAR" ]] && install -m 644 "$JAR" "$APP_HOME/linux-aiops-agent.jar"

if [[ -d frontend/build ]]; then
  rm -rf "$APP_HOME/frontend"
  cp -r frontend/build "$APP_HOME/frontend"
fi

echo "==> writing ${ENV_FILE}"
cat > "$ENV_FILE" <<EOF
OPS_AGENT_DATA_DIR=${DATA_DIR}
OPS_AGENT_FRONTEND_DIR=${APP_HOME}/frontend
OPS_AGENT_PORT=18080
OPS_AGENT_BIND=127.0.0.1
EOF
chmod 600 "$ENV_FILE"

chown -R linux-agent:linux-agent "$APP_HOME" "$DATA_DIR" "$LOG_DIR"

systemctl daemon-reload
systemctl enable --now "${SERVICE_NAME}.service"

sleep 2
systemctl --no-pager status "${SERVICE_NAME}.service" || true
curl -fsS http://127.0.0.1:18080/api/health && echo " <- health OK"
