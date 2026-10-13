@echo off
REM Runs the AI server's automated tests (fake mode, no Gemini calls, no key needed).
cd /d "%~dp0"
call .venv\Scripts\activate.bat
python -m pytest -v
pause
