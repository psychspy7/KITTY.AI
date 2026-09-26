@echo off
cd /d "%~dp0"
py -3 server\kitty.py serve
pause
