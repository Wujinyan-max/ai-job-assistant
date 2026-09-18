#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# 职得 JobPath 一键部署脚本
# 用法：
#   首次部署：  bash deploy.sh init your-domain.com
#   更新代码：  bash deploy.sh update
#   查看日志：  bash deploy.sh logs
#   重启服务：  bash deploy.sh restart
# ---------------------------------------------------------------------------

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DEPLOY_DIR"

CMD="${1:-help}"

check_env() {
  if [ ! -f .env ]; then
    echo "❌ .env 不存在，先复制模板并填写配置："
    echo "   cp .env.example .env && nano .env"
    exit 1
  fi
  source .env
  if [ -z "${DB_ROOT_PASSWORD:-}" ] || [ -z "${JWT_SECRET:-}" ] || [ -z "${AI_CONFIG_ENCRYPTION_KEY:-}" ]; then
    echo "❌ .env 中 DB_ROOT_PASSWORD / JWT_SECRET / AI_CONFIG_ENCRYPTION_KEY 不能为空"
    exit 1
  fi
}

case "$CMD" in

  # 首次部署：申请证书 + 启动服务
  init)
    DOMAIN="${2:-}"
    if [ -z "$DOMAIN" ]; then
      echo "用法：bash deploy.sh init your-domain.com"
      exit 1
    fi
    check_env

    echo "==> 1. 安装 certbot（如果未安装）"
    if ! command -v certbot &>/dev/null; then
      apt-get install -y certbot 2>/dev/null || yum install -y certbot 2>/dev/null || true
    fi

    echo "==> 2. 申请 SSL 证书：$DOMAIN"
    mkdir -p ssl
    # 先临时启动 nginx（仅 80 端口）用于域名验证
    docker compose up -d frontend --no-deps 2>/dev/null || true
    certbot certonly --standalone -d "$DOMAIN" --agree-tos --email "admin@$DOMAIN" --non-interactive
    cp "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" ssl/
    cp "/etc/letsencrypt/live/$DOMAIN/privkey.pem"  ssl/
    chmod 644 ssl/fullchain.pem
    chmod 600 ssl/privkey.pem
    echo "   证书已保存到 deploy/ssl/"

    echo "==> 3. 更新 nginx server_name 为 $DOMAIN"
    sed -i "s/server_name _;/server_name $DOMAIN;/g" nginx.conf

    echo "==> 4. 启动所有服务"
    docker compose up -d --build
    echo ""
    echo "✅ 部署完成：https://$DOMAIN"
    ;;

  # 更新代码：重新构建并重启
  update)
    check_env
    echo "==> 拉取最新代码（如使用 git）"
    git -C .. pull 2>/dev/null || true
    echo "==> 重新构建镜像并重启"
    docker compose up -d --build
    echo "✅ 更新完成"
    ;;

  # 查看日志
  logs)
    docker compose logs -f --tail=100 "${2:-}"
    ;;

  # 重启
  restart)
    check_env
    docker compose restart
    echo "✅ 重启完成"
    ;;

  # 停止
  stop)
    docker compose down
    echo "已停止"
    ;;

  # 证书续期
  renew-cert)
    DOMAIN=$(grep -oP 'server_name \K[^;]+' nginx.conf | head -1 | tr -d ' ')
    if [ -z "$DOMAIN" ] || [ "$DOMAIN" = "_" ]; then
      echo "❌ 未找到域名，请先运行 init"
      exit 1
    fi
    certbot renew --quiet
    cp "/etc/letsencrypt/live/$DOMAIN/fullchain.pem" ssl/
    cp "/etc/letsencrypt/live/$DOMAIN/privkey.pem"  ssl/
    docker compose restart frontend
    echo "✅ 证书已续期并重启 nginx"
    ;;

  help|*)
    echo "职得 JobPath 部署脚本"
    echo ""
    echo "用法："
    echo "  bash deploy.sh init your-domain.com   首次部署（申请证书+启动）"
    echo "  bash deploy.sh update                 更新代码并重启"
    echo "  bash deploy.sh logs [service]         查看日志（backend/frontend/mysql）"
    echo "  bash deploy.sh restart                重启所有服务"
    echo "  bash deploy.sh stop                   停止所有服务"
    echo "  bash deploy.sh renew-cert             手动续期 SSL 证书"
    ;;
esac
