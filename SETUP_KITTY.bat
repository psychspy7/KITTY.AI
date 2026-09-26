@echo off
cd /d "%~dp0"
py -3 server\kitty.py init
if errorlevel 1 goto end
py -3 server\kitty.py doctor
echo.
echo Pairing token - paste into KITTY settings on your phone; keep it private.
py -3 server\kitty.py token
:end
pause
