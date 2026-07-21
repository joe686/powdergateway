PowerGateway 便携版 · 使用说明
===================================

【一键启动】
  Windows: 双击 start.bat
  Linux:   ./start.sh

启动后浏览器打开 http://localhost:8080
默认账号: admin / Admin@123

【停止服务】
  Windows: 双击 stop.bat（或直接关闭 start.bat 窗口）
  Linux:   ./stop.sh（或在 start.sh 终端 Ctrl+C）

【数据存储】
  所有数据存放在 data/h2/ 目录（H2 数据库文件）
  升级到新版本：停旧版 → 复制 data/ 到新版目录 → 启新版

【H2 控制台】
  http://localhost:8080/h2-console
  JDBC URL: jdbc:h2:file:./data/h2/pg_config
  User:     sa
  Password: （空）

【添加 Oracle / OceanBase 等驱动】
  1. 到官网下载对应 JDBC jar
  2. 复制到 backend/lib/ 目录
  3. 重启服务

【日志】
  控制台输出即日志，若需要文件日志请重定向 stdout

【端口占用】
  默认使用 8080 端口，如被占用请修改 backend/config/application-standalone.yml
  里的 server.port，或用命令行覆盖：
    java -jar backend/powergateway.jar --server.port=8081 --spring.profiles.active=standalone

【技术支持】
  https://github.com/xxx/powergateway
