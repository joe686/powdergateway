@echo off
chcp 65001 > nul
title PowerGateway 便携版

set BASE=%~dp0
set JAVA=%BASE%jre\bin\java.exe

if not exist "%JAVA%" (
  echo [WARN] 内嵌 JRE 未找到，尝试使用系统 Java
  set JAVA=java
)

echo ================================================
echo  PowerGateway 便携版启动中...
echo  数据目录: %BASE%data
echo  访问地址: http://localhost:8080
echo  默认账号: admin / Admin@123
echo  H2 控制台: http://localhost:8080/h2-console
echo ================================================
echo.
echo  关闭本窗口即停止服务
echo.

"%JAVA%" -Xms256m -Xmx1g -Dfile.encoding=UTF-8 ^
  -Dspring.profiles.active=standalone ^
  -Dloader.path="%BASE%backend\lib" ^
  -jar "%BASE%backend\powergateway.jar"

pause
