@echo off
setlocal
cd /d "c:\Users\Aryan Ruthwik\OneDrive\Documents\MyFamDoctor\backend"
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
set PORT=8084
set SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_HOST:5432/YOUR_DB?sslmode=require
set SPRING_DATASOURCE_USERNAME=your_db_user
set SPRING_DATASOURCE_PASSWORD=your_db_password
"C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" spring-boot:run
