@echo off
cd /d "%~dp0"
py -3 server\kitty.py init
if errorlevel 1 goto failed
start "KITTY model" cmd /k call START_MODEL_FAST.bat
start "KITTY brain" cmd /k call START_KITTY.bat
echo.
echo Both windows must stay open. Wait until the model says it is listening.
echo In another PowerShell window, run: py -3 server\kitty.py doctor
echo To share with a phone outside Wi-Fi, follow docs\START_HERE_V03.md.
echo.
echo Owner pairing token - paste only into your own phone:
py -3 server\kitty.py token
goto end
:failed
echo KITTY needs Python 3.11+ and the downloaded repository. Read docs\START_HERE_V03.md.
:end
pause
