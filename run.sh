#!/usr/bin/env bash
# Compila e executa o projeto. Requer JDK 17+ e lib/jocl-2.0.4.jar.
# Exemplos:
#   ./run.sh contar amostras/MobyDick-217452.txt whale
#   ./run.sh benchmark
#   ./run.sh gui
set -e
cd "$(dirname "$0")"
mkdir -p bin
javac -encoding UTF-8 -d bin -cp lib/jocl-2.0.4.jar src/br/unifor/paralela/*.java
java -Xmx3g -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -cp "bin:lib/jocl-2.0.4.jar" br.unifor.paralela.Main "$@"
