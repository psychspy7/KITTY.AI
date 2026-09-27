@echo off
cd /d "%~dp0"
py -3 tools\download_model.py --fast
if errorlevel 1 echo KITTY needs attention. Read the message above.
pause
