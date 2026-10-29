@echo off
rem Compila o projeto (Java 8 ou superior) para a pasta out\
cd /d "%~dp0.."
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > fontes.txt
javac -encoding UTF-8 -d out @fontes.txt
del fontes.txt
echo Compilado em out\
