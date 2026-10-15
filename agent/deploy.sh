#!/usr/bin/env bash
# 本机执行：打包 Agent，把 admin-agent.jar 和 install.sh 上传到服务器的 /opt/admin-agent-dist（nginx 在 /agent/ 下提供下载）。
# 已安装的 Agent 不会自动升级，需要在各服务器上重新执行安装命令。用法：agent/deploy.sh [SSH 主机，默认 gz]
set -euo pipefail

HOST="${1:-gz}"
JAVA21="${JAVA_HOME_21:-/usr/local/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home}"
cd "$(dirname "$0")"

JAVA_HOME="$JAVA21" mvn -q -o clean package
ssh "$HOST" 'mkdir -p /opt/admin-agent-dist'
rsync -az target/admin-agent.jar install.sh "$HOST":/opt/admin-agent-dist/
ssh "$HOST" 'chmod 644 /opt/admin-agent-dist/*; ls -l /opt/admin-agent-dist'
