#!/usr/bin/env bash
# Abre o visualizador de gráficos dinâmicos (Swing)
cd "$(dirname "$0")/.."
[ -d out ] || ./scripts/compilar.sh
java -Dfile.encoding=UTF-8 -cp out br.unifor.ordenacao.grafico.VisualizadorGraficos "$@"
