@echo off
REM Starts the PortfolioIQ AI server. Keep this window open while using the app.
cd /d "%~dp0"
if not exist .venv\Scripts\activate.bat (
    echo Run setup_server.bat first.
    pause
    exit /b 1
)
call .venv\Scripts\activate.bat
echo PortfolioIQ AI server running at http://127.0.0.1:8000  (the emulator reaches it as http://10.0.2.2:8000)
echo Press Ctrl+C to stop.
uvicorn app:app --host 127.0.0.1 --port 8000
pause
