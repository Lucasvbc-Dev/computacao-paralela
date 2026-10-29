@echo off
rem Abre o visualizador de graficos dinamicos (Swing)
cd /d "%~dp0.."
if not exist out call scripts\compilar.bat
java -Dfile.encoding=UTF-8 -cp out br.unifor.ordenacao.grafico.VisualizadorGraficos %*
