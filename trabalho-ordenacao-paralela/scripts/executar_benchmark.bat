@echo off
rem Execucao completa do benchmark. Gera resultados\brutos.csv e resultados\resumo.csv
cd /d "%~dp0.."
if not exist out call scripts\compilar.bat
java -Xmx3g -Dfile.encoding=UTF-8 -cp out br.unifor.ordenacao.benchmark.Benchmark --amostras=5 --threads=1,2,4,8 --saida=resultados/brutos.csv --resumo=resultados/resumo.csv %*
