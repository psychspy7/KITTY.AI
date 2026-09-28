#!/usr/bin/env bash
set -euo pipefail

APP_ROOT="${APP_ROOT:-/opt/kitty-ai}"
LLAMA_ROOT="${LLAMA_ROOT:-/opt/llama.cpp}"
PYTHON="${APP_ROOT}/.venv/bin/python"

if [ "$(id -u)" -ne 0 ]; then
  echo "Run this script with sudo: sudo bash cloud/oracle/install.sh" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends git ca-certificates python3 python3-venv python3-pip build-essential cmake pkg-config libcurl4-openssl-dev

test -f "${APP_ROOT}/server/kitty.py" || { echo "KITTY source was not found at ${APP_ROOT}" >&2; exit 1; }

if [ ! -x "${PYTHON}" ]; then
  python3 -m venv "${APP_ROOT}/.venv"
fi
"${PYTHON}" -m pip install --upgrade pip
"${PYTHON}" -m pip install -r "${APP_ROOT}/requirements-turso.txt"

if [ ! -x "${LLAMA_ROOT}/build/bin/llama-server" ]; then
  if [ ! -d "${LLAMA_ROOT}/.git" ]; then
    git clone --depth 1 https://github.com/ggml-org/llama.cpp.git "${LLAMA_ROOT}"
  else
    git -C "${LLAMA_ROOT}" pull --ff-only
  fi
  cmake -S "${LLAMA_ROOT}" -B "${LLAMA_ROOT}/build" -DGGML_NATIVE=OFF -DLLAMA_CURL=OFF
  cmake --build "${LLAMA_ROOT}/build" --config Release --target llama-server -j2
fi

cd "${APP_ROOT}"
"${PYTHON}" server/kitty.py --home "${APP_ROOT}/data" init
"${PYTHON}" tools/download_model.py --fast

MODEL_PATH="$(find "${APP_ROOT}/models" -maxdepth 1 -type f -name '*Q4_K_M.gguf' -print -quit)"
test -n "${MODEL_PATH}" || { echo "The downloaded GGUF was not found" >&2; exit 1; }
sed "s|__KITTY_MODEL_PATH__|${MODEL_PATH}|g" "${APP_ROOT}/cloud/oracle/kitty-model.service" > /etc/systemd/system/kitty-model.service
install -m 0644 "${APP_ROOT}/cloud/oracle/kitty-gateway.service" /etc/systemd/system/kitty-gateway.service
install -d -m 0700 /etc/kitty
install -m 0600 "${APP_ROOT}/cloud/oracle/turso.env.example" /etc/kitty/turso.env.example
systemctl daemon-reload
systemctl enable kitty-model kitty-gateway
systemctl restart kitty-model
sleep 2
systemctl restart kitty-gateway

echo
echo "KITTY is installed. Pair the phone after you configure HTTPS."
echo "Token:"
"${PYTHON}" server/kitty.py --home "${APP_ROOT}/data" token
