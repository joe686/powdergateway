# PowerGateway Frontend · v0.3.9 · nginx 镜像(CHG-050)
# 多阶段构建:builder(node) → runtime(nginx)
#
# 构建:
#   cd frontend
#   docker build -t powergateway-frontend:0.3.9 .
#
# 单独运行:
#   docker run -d --name pg-frontend -p 5173:80 powergateway-frontend:0.3.9
#
# 推荐 docker-compose 一键起(见项目根 docker-compose.yml)

# ─── 阶段 1:builder ─────────────────────────────────────────────
FROM node:18-alpine AS builder

WORKDIR /app

# 先 copy package 触发 npm ci 层缓存 · 变动 src 时不用重装依赖
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund

# 再 copy 源码
COPY . .

# 生产构建 · 输出到 dist/
RUN npm run build

# ─── 阶段 2:nginx runtime ──────────────────────────────────────
FROM nginx:1.25-alpine

LABEL maintainer="PowerGateway Dev"
LABEL version="0.3.9"

# 时区 · 中国大陆
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 覆盖默认配置 · SPA history + /api 代理 backend
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 从 builder 阶段复制静态产物
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
