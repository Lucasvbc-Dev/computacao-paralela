# Análise de Desempenho de Algoritmos de Ordenação em Ambientes Concorrentes e Paralelos

Estudo comparativo em Java das versões serial e paralela de quatro algoritmos de ordenação:
Bubble Sort, Quick Sort, Merge Sort e Counting Sort.

Aluno: Lucas de Vasconcelos Barreira Carvalho (2410424) — Professor: Izequiel Pereira de Norões

Repositório: https://github.com/Lucasvbc-Dev/computacao-paralela

## Estrutura

```
src/br/unifor/ordenacao/
  algoritmos/   AlgoritmoOrdenacao (interface), BubbleSort, QuickSort, MergeSort, CountingSort, Util
  dados/        Distribuicao (natureza da entrada) e GeradorDados (vetores reproduzíveis por semente)
  benchmark/    Benchmark (framework de teste), Medicao (linha do CSV), Estatisticas (análise estatística)
  grafico/      VisualizadorGraficos e GraficoLinhas (gráficos dinâmicos em Swing — ponto extra)
scripts/        compilar.sh, executar_benchmark.sh, executar_experimento_nucleos.sh, *.bat (Windows)
analise/        gerar_graficos.py (gráficos estáticos do relatório)
resultados/     CSVs gerados (brutos, resumo, experimento de núcleos) e logs das execuções
graficos/       Figuras usadas no relatório
relatorio/      Relatório (DOCX e PDF)
```

## Requisitos

- Java 8 ou superior (o código não usa recursos posteriores ao Java 8).
- Opcional: Python 3 com `matplotlib` e `pandas` para regenerar as figuras do relatório.

## Como executar

Linux/macOS:

```bash
./scripts/compilar.sh
./scripts/executar_benchmark.sh              # execução completa (≈6 min em 2 núcleos)
./scripts/executar_experimento_nucleos.sh    # repete um subconjunto com 1 núcleo e com todos os núcleos
java -cp out br.unifor.ordenacao.grafico.VisualizadorGraficos   # gráficos dinâmicos
python3 analise/gerar_graficos.py            # figuras PNG do relatório
```

Windows:

```bat
scripts\compilar.bat
scripts\executar_benchmark.bat
scripts\executar_experimento_nucleos.bat
scripts\visualizar.bat
```

Também é possível abrir a pasta `src` em qualquer IDE (IntelliJ, Eclipse, NetBeans) e executar as classes
`Benchmark` e `VisualizadorGraficos` diretamente.

### Parâmetros do benchmark

Todos opcionais, no formato `--nome=valor`:

| Parâmetro | Padrão |
|---|---|
| `--algoritmos` | `BubbleSort,QuickSort,MergeSort,CountingSort` |
| `--tamanhos` (Quick, Merge, Counting) | `1000,10000,50000,100000,500000,1000000,2000000,5000000` |
| `--tamanhos-quadraticos` (Bubble) | `1000,5000,10000,25000,50000` |
| `--distribuicoes` | `ALEATORIO,ORDENADO,INVERSO,POUCOS_DISTINTOS` |
| `--threads` | `1,2,4,8` |
| `--amostras` | `5` |
| `--saida` | `resultados/brutos.csv` |
| `--resumo` | `resultados/resumo.csv` |

Exemplo: `java -cp out br.unifor.ordenacao.benchmark.Benchmark --algoritmos=MergeSort --threads=2,4,8,16 --amostras=10`

## Arquivos CSV

`brutos.csv` — uma linha por execução cronometrada:
`algoritmo,versao,threads,nucleos,tamanho,distribuicao,amostra,tempo_ms,ordenado_ok`

`resumo.csv` — uma linha por configuração (estatísticas sobre as amostras):
`nucleos,algoritmo,distribuicao,tamanho,versao,threads,amostras,media_ms,desvio_ms,min_ms,max_ms,mediana_ms,cv_pct,ic95_ms,speedup,eficiencia`

- `speedup` = tempo médio serial / tempo médio da configuração (mesmo algoritmo, distribuição, tamanho e núcleos)
- `eficiencia` = speedup / threads
- `ic95_ms` = meia-largura do intervalo de confiança de 95% da média (t de Student)

`nucleos_N_brutos.csv` / `nucleos_resumo.csv` — experimento com a JVM limitada a N núcleos.

## Gráficos dinâmicos (ponto extra)

`VisualizadorGraficos` (Swing, sem bibliotecas externas) lê os CSVs brutos, calcula as estatísticas em memória e
permite alternar entre seis visões (tempo x tamanho, speedup x threads, eficiência x threads, comparação entre
algoritmos, efeito da distribuição e speedup por quantidade de núcleos), filtrando por algoritmo, distribuição,
tamanho e núcleos, com escala log, barras de IC 95%, tooltip sobre os pontos, tabela com os números e exportação
para PNG. Com "Atualizar automaticamente" ligado, o arquivo é relido a cada 2 s: abrindo o visualizador enquanto
o benchmark roda, os gráficos são atualizados em tempo real.

## Limitar núcleos

- Linux: `taskset -c 0 java -XX:ActiveProcessorCount=1 ...` (feito pelo script `executar_experimento_nucleos.sh`)
- Windows: `start /affinity 1 /wait java -XX:ActiveProcessorCount=1 ...` (máscara 1 = CPU 0; 3 = CPUs 0 e 1; F = CPUs 0–3)
