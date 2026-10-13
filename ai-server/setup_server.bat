@echo off
REM One-time setup for the PortfolioIQ AI server (TradingAgents + Gemini).
cd /d "%~dp0"
python --version >nul 2>&1
if errorlevel 1 (
    echo Python is not installed, or not on PATH.
    echo Install Python 3.13 from python.org and tick "Add python.exe to PATH".
    pause
    exit /b 1
)
echo Creating the Python environment in ai-server\.venv ...
python -m venv .venv || goto :failed
call .venv\Scripts\activate.bat
python -m pip install --upgrade pip
echo Installing TradingAgents and the server. This takes a few minutes ...
pip install -r requirements.txt || goto :failed
if not exist .env copy .env.example .env >nul
echo.
echo Setup finished.
echo Next: open ai-server\.env in Notepad, paste your Gemini key after GOOGLE_API_KEY=, save,
echo then double-click start_server.bat.
pause
exit /b 0
:failed
echo.
echo Setup failed. Copy the red error text above and send it to the team.
pause
exit /b 1
