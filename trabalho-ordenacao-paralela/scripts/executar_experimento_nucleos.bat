@echo off
rem Experimento de nucleos no Windows: 1 nucleo (mascara 1), 2 nucleos (mascara 3) e 4 nucleos (mascara F).
rem Ajuste a lista conforme a quantidade de nucleos da sua maquina.
cd /d "%~dp0.."
if not exist out call scripts\compilar.bat
set ARGS=--tamanhos=1000000,5000000 --tamanhos-quadraticos=25000 --distribuicoes=ALEATORIO,POUCOS_DISTINTOS --threads=1,2,4,8 --amostras=5 --resumo=
start "" /affinity 1 /wait java -Xmx3g -XX:ActiveProcessorCount=1 -cp out br.unifor.ordenacao.benchmark.Benchmark %ARGS% --saida=resultados/nucleos_1_brutos.csv
start "" /affinity 3 /wait java -Xmx3g -XX:ActiveProcessorCount=2 -cp out br.unifor.ordenacao.benchmark.Benchmark %ARGS% --saida=resultados/nucleos_2_brutos.csv
start "" /affinity F /wait java -Xmx3g -XX:ActiveProcessorCount=4 -cp out br.unifor.ordenacao.benchmark.Benchmark %ARGS% --saida=resultados/nucleos_4_brutos.csv
java -cp out br.unifor.ordenacao.benchmark.Estatisticas resultados/nucleos_resumo.csv resultados/nucleos_1_brutos.csv resultados/nucleos_2_brutos.csv resultados/nucleos_4_brutos.csv
