@echo off
chcp 65001 > nul
title PowerGateway 标准版

set BASE=%~dp0..

if not exist "%BASE%\config\application-prod.yml" (
    echo 错误：%BASE%\config\application-prod.yml 不存在
    echo 请先复制 application-prod.yml.example 为 application-prod.yml 并填写数据库连接信息
    pause
    exit /b 1
)

echo ================================================
echo  PowerGateway 标准版启动中...
echo  配置文件: %BASE%\config\application-prod.yml
echo ================================================

java -Xms512m -Xmx2g -Dfile.encoding=UTF-8 ^
    -Dspring.profiles.active=prod ^
    -Dspring.config.location="file:%BASE%\config\application-prod.yml" ^
    -Dloader.path="%BASE%\lib" ^
    -jar "%BASE%\powergateway-backend.jar"

pause
