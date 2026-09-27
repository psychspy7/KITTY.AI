@echo off
cd /d "%~dp0"
py -3 tools\optimize_low_memory.py
if errorlevel 1 echo KITTY needs attention. Read the message above.
pause
