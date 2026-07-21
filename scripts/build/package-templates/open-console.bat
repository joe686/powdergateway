@echo off
chcp 65001 > nul
echo 打开 PowerGateway H2 控制台...
start "" "http://localhost:8080/h2-console"
