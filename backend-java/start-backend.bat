@echo off
cd /d E:\VScodeProject\health-app-IOS\backend-java
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
set "APP_PORT=3302"
set DB_URL=jdbc:mysql://127.0.0.1:3306/health_monitoring?useSSL=false^&allowPublicKeyRetrieval=true^&serverTimezone=Asia/Shanghai^&characterEncoding=utf8mb4
set "DB_USERNAME=root"
set "DB_PASSWORD=123456"
"C:\Users\12774\.m2\wrapper\dists\apache-maven-3.8.8\fc52dd14fbe4fc8a08c15dbe0b63b4b7a3e650ed5d4c23330d67865154f0421c\bin\mvn.cmd" spring-boot:run