#!/usr/bin/env bash
# Execução completa: 4 algoritmos x tamanhos x 4 distribuições x (serial + 1, 2, 4, 8 threads) x 5 amostras.
# Gera resultados/brutos.csv e resultados/resumo.csv.
set -e
cd "$(dirname "$0")/.."
[ -d out ] || ./scripts/compilar.sh
java -Xmx3g -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out \
  br.unifor.ordenacao.benchmark.Benchmark \
  --amostras=5 \
  --threads=1,2,4,8 \
  --saida=resultados/brutos.csv \
  --resumo=resultados/resumo.csv "$@"
