package br.unifor.ordenacao.benchmark;

import br.unifor.ordenacao.algoritmos.AlgoritmoOrdenacao;
import br.unifor.ordenacao.algoritmos.BubbleSort;
import br.unifor.ordenacao.algoritmos.CountingSort;
import br.unifor.ordenacao.algoritmos.MergeSort;
import br.unifor.ordenacao.algoritmos.QuickSort;
import br.unifor.ordenacao.dados.Distribuicao;
import br.unifor.ordenacao.dados.GeradorDados;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Framework de teste. Para cada algoritmo, tamanho, distribuição e amostra, gera um vetor de entrada
 * (mesma semente para todas as configurações da amostra), executa a versão serial e a versão paralela
 * com cada quantidade de threads, cronometra com System.nanoTime(), confere o resultado contra uma
 * ordenação de referência e grava uma linha no CSV de resultados brutos (gravação imediata, linha a linha).
 * Ao final, gera o CSV de resumo estatístico.
 *
 * Parâmetros (todos opcionais, no formato --nome=valor):
 *   --algoritmos=BubbleSort,QuickSort,MergeSort,CountingSort
 *   --tamanhos=1000,10000,...            tamanhos para QuickSort, MergeSort e CountingSort
 *   --tamanhos-quadraticos=1000,5000,... tamanhos para BubbleSort (O(n^2))
 *   --distribuicoes=ALEATORIO,ORDENADO,INVERSO,POUCOS_DISTINTOS
 *   --threads=1,2,4,8
 *   --amostras=5
 *   --saida=resultados/brutos.csv
 *   --resumo=resultados/resumo.csv
 */
public final class Benchmark {

    private static final long SEMENTE_BASE = 20261029L;

    private Benchmark() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> p = lerArgumentos(args);
        List<AlgoritmoOrdenacao> algoritmos = algoritmos(p.get("algoritmos"));
        int[] tamanhos = inteiros(p.get("tamanhos"));
        int[] tamanhosQuadraticos = inteiros(p.get("tamanhos-quadraticos"));
        int[] threads = inteiros(p.get("threads"));
        int amostras = Integer.parseInt(p.get("amostras"));
        List<Distribuicao> distribuicoes = new ArrayList<Distribuicao>();
        for (String d : p.get("distribuicoes").split(",")) {
            distribuicoes.add(Distribuicao.valueOf(d.trim()));
        }
        String saida = p.get("saida");
        String resumo = p.get("resumo");
        int nucleos = Runtime.getRuntime().availableProcessors();

        System.out.println("=== Benchmark de ordenação serial x paralela ===");
        System.out.println("Java " + System.getProperty("java.version") + " | núcleos disponíveis: " + nucleos
                + " | heap máx: " + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " MB");
        System.out.println("Threads: " + Arrays.toString(threads) + " | amostras: " + amostras);

        aquecer(algoritmos, threads);

        File arq = new File(saida);
        if (arq.getParentFile() != null) {
            arq.getParentFile().mkdirs();
        }
        List<Medicao> todas = new ArrayList<Medicao>();
        PrintWriter csv = new PrintWriter(new OutputStreamWriter(new FileOutputStream(arq), StandardCharsets.UTF_8));
        try {
            csv.println(Medicao.CABECALHO);
            csv.flush();
            long inicio = System.nanoTime();
            for (AlgoritmoOrdenacao alg : algoritmos) {
                int[] tams = (alg instanceof BubbleSort) ? tamanhosQuadraticos : tamanhos;
                for (int n : tams) {
                    for (Distribuicao d : distribuicoes) {
                        for (int a = 1; a <= amostras; a++) {
                            long semente = SEMENTE_BASE + 1000L * a + n;
                            int[] original = GeradorDados.gerar(d, n, semente);
                            int[] referencia = original.clone();
                            Arrays.sort(referencia);

                            todas.add(medir(csv, alg, "SERIAL", 1, nucleos, n, d, a, original, referencia));
                            for (int t : threads) {
                                todas.add(medir(csv, alg, "PARALELO", t, nucleos, n, d, a, original, referencia));
                            }
                        }
                        System.out.printf(Locale.US, "[%6.1fs] %-12s n=%-9d %-16s ok%n",
                                (System.nanoTime() - inicio) / 1e9, alg.nome(), n, d);
                    }
                }
            }
        } finally {
            csv.close();
        }

        for (Medicao m : todas) {
            if (!m.ordenadoOk) {
                System.err.println("ATENÇÃO: resultado incorreto em " + m.paraCsv());
            }
        }
        if (resumo != null && !resumo.isEmpty()) {
            Estatisticas.escreverResumo(Estatisticas.calcular(todas), resumo);
            System.out.println("Resumo estatístico: " + resumo);
        }
        System.out.println("Resultados brutos: " + saida + " (" + todas.size() + " medições)");
    }

    private static Medicao medir(PrintWriter csv, AlgoritmoOrdenacao alg, String versao, int threads, int nucleos,
                                 int n, Distribuicao d, int amostra, int[] original, int[] referencia) {
        int[] v = original.clone();
        System.gc();
        long t0 = System.nanoTime();
        if ("SERIAL".equals(versao)) {
            alg.ordenarSerial(v);
        } else {
            alg.ordenarParalelo(v, threads);
        }
        long t1 = System.nanoTime();
        boolean ok = Arrays.equals(v, referencia);
        Medicao m = new Medicao(alg.nome(), versao, threads, nucleos, n, d.name(), amostra, (t1 - t0) / 1e6, ok);
        csv.println(m.paraCsv());
        csv.flush();
        return m;
    }

    /** Executa cada algoritmo algumas vezes antes das medições para que o compilador JIT otimize o código. */
    private static void aquecer(List<AlgoritmoOrdenacao> algoritmos, int[] threads) {
        System.out.print("Aquecendo a JVM...");
        for (AlgoritmoOrdenacao alg : algoritmos) {
            int n = (alg instanceof BubbleSort) ? 3000 : 200_000;
            for (int r = 0; r < 5; r++) {
                int[] base = GeradorDados.gerar(Distribuicao.ALEATORIO, n, r);
                alg.ordenarSerial(base.clone());
                for (int t : threads) {
                    alg.ordenarParalelo(base.clone(), t);
                }
            }
        }
        System.out.println(" ok");
    }

    private static List<AlgoritmoOrdenacao> algoritmos(String nomes) {
        List<AlgoritmoOrdenacao> lista = new ArrayList<AlgoritmoOrdenacao>();
        for (String nome : nomes.split(",")) {
            String s = nome.trim();
            if (s.equalsIgnoreCase("BubbleSort")) {
                lista.add(new BubbleSort());
            } else if (s.equalsIgnoreCase("QuickSort")) {
                lista.add(new QuickSort());
            } else if (s.equalsIgnoreCase("MergeSort")) {
                lista.add(new MergeSort());
            } else if (s.equalsIgnoreCase("CountingSort")) {
                lista.add(new CountingSort());
            } else {
                throw new IllegalArgumentException("Algoritmo desconhecido: " + s);
            }
        }
        return lista;
    }

    private static int[] inteiros(String csv) {
        String[] partes = csv.split(",");
        int[] r = new int[partes.length];
        for (int i = 0; i < partes.length; i++) {
            r[i] = Integer.parseInt(partes[i].trim());
        }
        return r;
    }

    private static Map<String, String> lerArgumentos(String[] args) {
        Map<String, String> p = new LinkedHashMap<String, String>();
        p.put("algoritmos", "BubbleSort,QuickSort,MergeSort,CountingSort");
        p.put("tamanhos", "1000,10000,50000,100000,500000,1000000,2000000,5000000");
        p.put("tamanhos-quadraticos", "1000,5000,10000,25000,50000");
        p.put("distribuicoes", "ALEATORIO,ORDENADO,INVERSO,POUCOS_DISTINTOS");
        p.put("threads", "1,2,4,8");
        p.put("amostras", "5");
        p.put("saida", "resultados/brutos.csv");
        p.put("resumo", "resultados/resumo.csv");
        for (String a : args) {
            if (!a.startsWith("--") || !a.contains("=")) {
                throw new IllegalArgumentException("Argumento inválido: " + a + " (use --nome=valor)");
            }
            int eq = a.indexOf('=');
            p.put(a.substring(2, eq), a.substring(eq + 1));
        }
        return p;
    }
}
