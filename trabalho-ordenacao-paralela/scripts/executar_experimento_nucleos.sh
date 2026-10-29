#!/usr/bin/env bash
# Experimento de núcleos: repete um subconjunto do benchmark limitando a JVM a 1, 2, ... núcleos.
# taskset fixa o processo em CPUs específicas e -XX:ActiveProcessorCount informa à JVM quantos núcleos usar.
# Uso: ./scripts/executar_experimento_nucleos.sh [lista de núcleos]   (padrão: 1 e todos os disponíveis)
set -e
cd "$(dirname "$0")/.."
[ -d out ] || ./scripts/compilar.sh
TOTAL=$(nproc)
NUCLEOS=${*:-"1 $TOTAL"}
ARQS=""
for N in $NUCLEOS; do
  CPUS="0-$((N-1))"
  echo ">>> Executando com $N núcleo(s) (CPUs $CPUS)"
  taskset -c "$CPUS" java -Xmx3g -XX:ActiveProcessorCount="$N" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out \
    br.unifor.ordenacao.benchmark.Benchmark \
    --tamanhos=1000000,5000000 \
    --tamanhos-quadraticos=25000 \
    --distribuicoes=ALEATORIO,POUCOS_DISTINTOS \
    --threads=1,2,4,8 \
    --amostras=5 \
    --saida="resultados/nucleos_${N}_brutos.csv" \
    --resumo=""
  ARQS="$ARQS resultados/nucleos_${N}_brutos.csv"
done
java -cp out br.unifor.ordenacao.benchmark.Estatisticas resultados/nucleos_resumo.csv $ARQS
