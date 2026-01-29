@echo off
chcp 65001 >nul
cd /d "%~dp0"

git init
git remote add origin https://github.com/HollyHully666/googleSPY.git 2>nul
git branch -M main

git add .
git status
git commit -m "chore: phase 1 infrastructure (server stub, docs, android, nginx examples)"
if errorlevel 1 echo Nothing to commit or commit failed.
git push -u origin main

pause
