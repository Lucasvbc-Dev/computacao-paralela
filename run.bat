@echo off
REM Compila e executa o projeto. Requer JDK 17+ e lib\jocl-2.0.4.jar.
REM Exemplos:
REM   run.bat contar amostras\MobyDick-217452.txt whale
REM   run.bat benchmark
REM   run.bat gui
cd /d "%~dp0"
if not exist bin mkdir bin
javac -encoding UTF-8 -d bin -cp lib\jocl-2.0.4.jar src\br\unifor\paralela\*.java || exit /b 1
java -Xmx3g -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -cp "bin;lib\jocl-2.0.4.jar" br.unifor.paralela.Main %*
