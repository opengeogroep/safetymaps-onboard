@echo off
cd %~dp0\..
:run
for /f %%i in ('dir bin\*.jar /b') do set JAR=%%i
set /p CL=<commandline.txt
java -jar %CL%

if not %errorlevel% == 99 goto exit

echo Updating safetymaps-onboard
rmdir /q /s bak 2>nul
xcopy /e /q /i bin bak
xcopy /e /q /y update bin
echo Running updated version
goto run

:exit

