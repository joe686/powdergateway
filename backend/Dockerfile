# PowerGateway Backend · v0.3.7 · Docker 镜像(REL-1 补齐)
# 用于 docker-compose 编排 · 依赖预打包 jar(mvn clean package -DskipTests)
#
# 构建:
#   cd backend
#   mvn clean package -DskipTests
#   docker build -t powergateway-backend:0.3.7 .
#
# 运行(独立):
#   docker run -d --name pg-backend -p 8080:8080 \
#     -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/pgw?useSSL=false \
#     -e SPRING_REDIS_HOST=host.docker.internal \
#     powergateway-backend:0.3.7
#
# 推荐 docker-compose 一键起(见项目根 docker-compose.yml)

FROM eclipse-temurin:8-jre-alpine

LABEL maintainer="PowerGateway Dev"
LABEL version="0.3.7"

WORKDIR /app

COPY target/backend-1.0.0-SNAPSHOT.jar app.jar

# 时区 · 中国大陆
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

EXPOSE 8080

# JVM 内存 · 单容器 512M · 生产按需覆盖
ENV JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai"
ENV SPRING_PROFILES_ACTIVE="prod"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
