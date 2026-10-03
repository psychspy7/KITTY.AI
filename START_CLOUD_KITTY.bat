@echo off
cd /d "%~dp0"
py -3 -m pip install -r requirements-cloud.txt
if errorlevel 1 goto failed
py -3 server\kitty.py init
if errorlevel 1 goto failed
py -3 server\kitty.py serve
:failed
pause
