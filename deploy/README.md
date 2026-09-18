# 职得 JobPath 部署指南

## 前置条件

- 一台 Linux 服务器（推荐 2C4G，Ubuntu 22.04 / Debian 12）
- 一个域名，已解析到服务器 IP
- 安装 Docker 和 Docker Compose

## 安装 Docker

```bash
# Ubuntu / Debian
curl -fsSL https://get.docker.com | bash
sudo usermod -aG docker $USER && newgrp docker
```

## 部署步骤

### 1. 上传代码

```bash
git clone <你的仓库地址> jobpath
cd jobpath/deploy
```

### 2. 配置环境变量

```bash
cp .env.example .env
nano .env
```

必填三项（生成方式见文件注释）：
- `DB_ROOT_PASSWORD` — MySQL root 密码
- `JWT_SECRET` — JWT 签名密钥（`openssl rand -base64 48`）
- `AI_CONFIG_ENCRYPTION_KEY` — AI 配置加密密钥（`openssl rand -hex 32`）

### 3. 一键部署

```bash
bash deploy.sh init your-domain.com
```

脚本会自动：申请 SSL 证书 → 配置 nginx → 构建镜像 → 启动服务。

### 4. 初始化演示数据（可选）

```bash
cd ../scripts
bash seed-demo.ps1 -BaseUrl https://your-domain.com/api
```

## 日常维护

```bash
bash deploy.sh update       # 拉代码 + 重新构建 + 重启
bash deploy.sh logs         # 查看所有服务日志
bash deploy.sh logs backend # 只看后端日志
bash deploy.sh renew-cert   # 手动续期 SSL 证书
bash deploy.sh stop         # 停止所有服务
```

## 证书自动续期

证书有效期 90 天，建议加 crontab 自动续期：

```bash
crontab -e
# 添加以下行（每天凌晨 2 点检查续期）
0 2 * * * cd /path/to/jobpath/deploy && bash deploy.sh renew-cert >> /var/log/certbot-renew.log 2>&1
```

## AI 功能

不填 `AI_API_KEY` 也能正常运行（自动走本地规则引擎）。  
填入 OpenAI 兼容接口的 Key 后即可使用完整 AI 能力（JD 解析 / 简历匹配 / 面试题生成）。
