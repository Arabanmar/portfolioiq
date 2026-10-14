@echo off
REM One-time setup for the PortfolioIQ AI server (TradingAgents + Gemini).
REM Uses Python 3.13 (or 3.12 / 3.14 / 3.11): some packages TradingAgents
REM needs have no ready-made Windows build for newer Python versions yet,
REM so pip would try to compile them and fail.
cd /d "%~dp0"

set "PYCMD="
for %%V in (3.13 3.12 3.14 3.11) do (
    if not defined PYCMD (
        py -V:%%V --version >nul 2>&1 && set "PYCMD=py -V:%%V"
    )
)
if not defined PYCMD (
    echo No suitable Python found. The server needs Python 3.13.
    echo Open Command Prompt and run:   py install 3.13
    echo then double-click setup_server.bat again.
    pause
    exit /b 1
)
echo Using: %PYCMD%
%PYCMD% --version

if exist .venv (
    echo Removing the old Python environment ...
    rmdir /s /q .venv
)
echo Creating the Python environment in ai-server\.venv ...
%PYCMD% -m venv .venv || goto :failed
call .venv\Scripts\activate.bat
python -m pip install --upgrade pip
echo Installing TradingAgents and the server. This takes a few minutes ...
pip install --only-binary=zstandard,numpy,pandas -r requirements.txt || goto :failed
if not exist .env copy .env.example .env >nul
echo.
echo Setup finished.
echo Next: open ai-server\.env in Notepad, paste your Gemini key after GOOGLE_API_KEY=, save,
echo then double-click start_server.bat.
pause
exit /b 0
:failed
echo.
echo Setup failed. Take a screenshot of the error above and send it to the team.
pause
exit /b 1
