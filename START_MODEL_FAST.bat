@echo off
cd /d "%~dp0"
py -3 tools\start_model.py --threads 6
pause
