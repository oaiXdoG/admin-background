#!/usr/bin/env bash
# 在业务服务器上以 root 执行（命令由后台「服务器管理 → 服务器」生成）：
#   curl -fsSL <后台地址>/agent/install.sh | bash -s -- <后台地址> <安装令牌>
# 安装到 /opt/admin-agent，注册为 systemd 服务 admin-agent（开机自启）。重复执行即重新安装并更换密钥。
set -euo pipefail

BASE_URL="${1:?缺少后台地址}"
TOKEN="${2:?缺少安装令牌}"
DIR=/opt/admin-agent

if [ "$(id -u)" != "0" ]; then
  echo "请用 root 执行" >&2
  exit 1
fi
JAVA="$(command -v java || true)"
if [ -z "$JAVA" ]; then
  echo "没有找到 java，需要 Java 17 及以上" >&2
  exit 1
fi
JAVA_VERSION="$("$JAVA" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
if [ "$JAVA_VERSION" -lt 17 ] 2>/dev/null; then
  echo "Java 版本是 $JAVA_VERSION，需要 17 及以上" >&2
  exit 1
fi

mkdir -p "$DIR"
curl -fsSL "$BASE_URL/agent/admin-agent.jar" -o "$DIR/admin-agent.jar.tmp"
mv "$DIR/admin-agent.jar.tmp" "$DIR/admin-agent.jar"

umask 077
cat > "$DIR/agent.properties" <<CONFIG
baseUrl=$BASE_URL
token=$TOKEN
CONFIG

cat > /etc/systemd/system/admin-agent.service <<UNIT
[Unit]
Description=admin-agent（服务器管理 Agent）
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=$JAVA -Xms32m -Xmx128m -jar $DIR/admin-agent.jar $DIR/agent.properties
Restart=always
RestartSec=5
# 注册失败（令牌无效或过期）时不自动重启，需要在后台重新生成安装命令
RestartPreventExitStatus=2

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable admin-agent >/dev/null
systemctl restart admin-agent
sleep 8
if systemctl is-active --quiet admin-agent; then
  echo "admin-agent 已安装并运行，几秒后后台会显示这台服务器在线"
else
  echo "admin-agent 启动失败：" >&2
  journalctl -u admin-agent -n 20 --no-pager >&2
  exit 1
fi
