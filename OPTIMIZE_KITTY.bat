@echo off
cd /d "%~dp0"
py -3 tools\optimize_low_memory.py
pause

