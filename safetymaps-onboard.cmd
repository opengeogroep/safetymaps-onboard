@echo off
rem Let op! Bewerken van dit bestand kan lopende clients breken, omdat na updaten
rem Windows bij een lopend script verder gaat op dezelfde byte-positie van het script!
cd %~dp0\..
:run
for /f %%i in ('dir bin\*.jar /b') do set JAR=%%i
set /p CL=<bin\commandline.txt
java -jar bin\%JAR% %CL%

if not %errorlevel% == 99 goto exit

echo Updating safetymaps-onboard
rmdir /q /s bak 2>nul
xcopy /e /q /i bin bak
robocopy /mir  update bin
echo Running updated version
goto run

:exit


