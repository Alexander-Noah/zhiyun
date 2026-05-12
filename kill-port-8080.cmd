@echo off
echo Looking for process using port 8080...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr :8080 ^| findstr LISTENING') do (
    echo Found process %%a using port 8080. Killing it...
    taskkill /F /PID %%a
    echo Done!
    pause
    exit /b
)
echo No process found listening on port 8080.
pause
