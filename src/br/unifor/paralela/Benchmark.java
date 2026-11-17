package br.unifor.paralela;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Framework de teste: executa os três métodos sobre vários arquivos e várias
 * configurações de threads, repete cada execução N vezes, valida que todos
 * encontraram a mesma contagem e grava os tempos em CSV.
 */
public final class Benchmark {

    /** Uma execução medida (uma linha do CSV bruto). */
    record Execucao(String palavra, String arquivo, long tamanhoBytes, String metodo, int threads,
                    int amostra, int ocorrencias, double tempoMs, double kernelMs, double transferenciaMs) {
    }

    private final Path pastaAmostras;
    private final Path pastaResultados;
    private final List<String> palavras;
    private final int amostras;
    private final int aquecimentos;
    private final List<Integer> threads;
    private final int[] fatoresReplicacao;

    public Benchmark(Path pastaAmostras, Path pastaResultados, List<String> palavras,
                     int amostras, int aquecimentos, List<Integer> threads, int[] fatoresReplicacao) {
        this.pastaAmostras = pastaAmostras;
        this.pastaResultados = pastaResultados;
        this.palavras = palavras;
        this.amostras = amostras;
        this.aquecimentos = aquecimentos;
        this.threads = threads;
        this.fatoresReplicacao = fatoresReplicacao;
    }

    /** Potências de 2 até max(8, 2 x núcleos), incluindo o número de núcleos da máquina. */
    public static List<Integer> threadsPadrao() {
        int nucleos = Runtime.getRuntime().availableProcessors();
        TreeSet<Integer> lista = new TreeSet<>();
        for (int t = 1; t <= Math.max(8, 2 * nucleos); t *= 2) {
            lista.add(t);
        }
        lista.add(nucleos);
        return new ArrayList<>(lista);
    }

    public void executar() throws IOException {
        Files.createDirectories(pastaResultados);
        List<Path> arquivos = prepararArquivos();
        int nucleos = Runtime.getRuntime().availableProcessors();

        System.out.println("Arquivos de entrada:");
        for (Path a : arquivos) {
            System.out.printf(Locale.ROOT, "  %-32s %8.2f MB%n", a.getFileName(), Files.size(a) / 1048576.0);
        }
        System.out.println("Threads testadas na ParallelCPU: " + threads + " (núcleos lógicos: " + nucleos + ")");
        System.out.println("Amostras por configuração: " + amostras + " (+" + aquecimentos + " de aquecimento, descartadas)");

        List<Execucao> execucoes = new ArrayList<>();
        try (ParallelGPU gpu = new ParallelGPU()) {
            System.out.println("Dispositivo OpenCL: " + gpu.getNomeDispositivo());
            System.out.printf(Locale.ROOT, "Preparação do OpenCL (contexto + compilação do kernel): %.1f ms%n%n",
                    gpu.getTempoPreparacaoMs());
            gravarAmbiente(gpu);

            for (String palavraOriginal : palavras) {
                String palavra = Texto.normalizarPalavra(palavraOriginal);
                for (Path arquivo : arquivos) {
                    Texto texto = Texto.carregar(arquivo);
                    execucoes.addAll(medirArquivo(texto, palavra, gpu));
                    texto = null;
                    System.gc();
                }
            }
        }

        Path csvBruto = pastaResultados.resolve("resultados_brutos.csv");
        Path csvResumo = pastaResultados.resolve("resumo.csv");
        gravarBruto(csvBruto, execucoes);
        List<Estatistica.Linha> resumo = Estatistica.resumir(execucoes, nucleos);
        Estatistica.gravarResumo(csvResumo, resumo);
        System.out.println("CSV gerados: " + csvBruto + " e " + csvResumo);

        Graficos.gerarTodos(csvResumo, pastaResultados.resolve("graficos"));
        System.out.println("Gráficos gerados em: " + pastaResultados.resolve("graficos"));
    }

    private List<Execucao> medirArquivo(Texto texto, String palavra, ParallelGPU gpu) {
        int nucleos = Runtime.getRuntime().availableProcessors();
        List<Execucao> lista = new ArrayList<>();

        // Aquecimento: deixa o JIT compilar o código e o driver OpenCL alocar recursos.
        for (int i = 0; i < aquecimentos; i++) {
            SerialCPU.contar(texto.chars, palavra);
            ParallelCPU.contar(texto.chars, palavra, nucleos);
            gpu.contar(texto.bytes, palavra);
        }

        // As amostras são intercaladas (serial, paralelas, GPU, serial, ...) para que
        // variações da máquina ao longo do tempo afetem todos os métodos igualmente.
        for (int a = 1; a <= amostras; a++) {
            long t0 = System.nanoTime();
            int c = SerialCPU.contar(texto.chars, palavra);
            double ms = (System.nanoTime() - t0) / 1e6;
            lista.add(new Execucao(palavra, texto.nome, texto.tamanhoBytes, "SerialCPU", 1, a, c, ms, 0, 0));

            for (int t : threads) {
                t0 = System.nanoTime();
                c = ParallelCPU.contar(texto.chars, palavra, t);
                ms = (System.nanoTime() - t0) / 1e6;
                lista.add(new Execucao(palavra, texto.nome, texto.tamanhoBytes, "ParallelCPU", t, a, c, ms, 0, 0));
            }

            t0 = System.nanoTime();
            c = gpu.contar(texto.bytes, palavra);
            ms = (System.nanoTime() - t0) / 1e6;
            lista.add(new Execucao(palavra, texto.nome, texto.tamanhoBytes, "ParallelGPU", 0, a, c, ms,
                    gpu.getUltimoKernelMs(), gpu.getUltimaTransferenciaMs()));
        }

        // Validação: todos os métodos precisam encontrar a mesma quantidade.
        long distintos = lista.stream().mapToInt(Execucao::ocorrencias).distinct().count();
        if (distintos != 1) {
            throw new IllegalStateException("Contagens divergentes para '" + palavra + "' em " + texto.nome);
        }

        Map<String, List<Execucao>> porConfig = lista.stream().collect(Collectors.groupingBy(
                e -> e.metodo() + (e.metodo().equals("ParallelCPU") ? " (" + e.threads() + " threads)" : ""),
                LinkedHashMap::new, Collectors.toList()));
        System.out.printf(Locale.ROOT, "[%s] %s (%.2f MB)%n", palavra, texto.nome, texto.tamanhoMB());
        porConfig.forEach((nome, es) -> {
            double media = es.stream().mapToDouble(Execucao::tempoMs).average().orElse(0);
            System.out.printf(Locale.ROOT, "    %-26s %d ocorrências em %8.2f ms (média de %d)%n",
                    nome + ":", es.get(0).ocorrencias(), media, es.size());
        });
        return lista;
    }

    /**
     * Arquivos de teste: as amostras originais e arquivos maiores gerados a partir delas
     * (concatenação das três, repetida N vezes) para observar o efeito do tamanho.
     */
    private List<Path> prepararArquivos() throws IOException {
        List<Path> originais;
        try (Stream<Path> s = Files.list(pastaAmostras)) {
            originais = s.filter(p -> p.toString().endsWith(".txt") && Files.isRegularFile(p))
                    .sorted(Comparator.comparingLong(Benchmark::tamanho))
                    .collect(Collectors.toList());
        }
        if (originais.isEmpty()) {
            throw new IOException("Nenhum .txt encontrado em " + pastaAmostras.toAbsolutePath());
        }
        List<Path> todos = new ArrayList<>(originais);
        Path pastaGeradas = pastaAmostras.resolve("geradas");
        Files.createDirectories(pastaGeradas);

        byte[] concatenado;
        try (var saida = new java.io.ByteArrayOutputStream()) {
            for (Path p : originais) {
                saida.write(Files.readAllBytes(p));
                saida.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            concatenado = saida.toByteArray();
        }
        for (int fator : fatoresReplicacao) {
            Path destino = pastaGeradas.resolve("Concatenado-x" + fator + ".txt");
            if (!Files.exists(destino) || Files.size(destino) != (long) concatenado.length * fator) {
                Files.deleteIfExists(destino);
                for (int i = 0; i < fator; i++) {
                    Files.write(destino, concatenado, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                }
            }
            todos.add(destino);
        }
        return todos;
    }

    private static long tamanho(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return 0;
        }
    }

    private void gravarBruto(Path csv, List<Execucao> execucoes) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            w.println("palavra,arquivo,tamanho_bytes,tamanho_mb,metodo,threads,amostra,ocorrencias,tempo_ms,kernel_ms,transferencia_ms");
            for (Execucao e : execucoes) {
                w.printf(Locale.ROOT, "%s,%s,%d,%.3f,%s,%d,%d,%d,%.4f,%.4f,%.4f%n",
                        e.palavra(), e.arquivo(), e.tamanhoBytes(), e.tamanhoBytes() / 1048576.0, e.metodo(),
                        e.threads(), e.amostra(), e.ocorrencias(), e.tempoMs(), e.kernelMs(), e.transferenciaMs());
            }
        }
    }

    private void gravarAmbiente(ParallelGPU gpu) throws IOException {
        Path arq = pastaResultados.resolve("ambiente.txt");
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(arq, StandardCharsets.UTF_8))) {
            w.println("Data: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            w.println("Sistema: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                    + " (" + System.getProperty("os.arch") + ")");
            w.println("Java: " + System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
            w.println("Núcleos lógicos (availableProcessors): " + Runtime.getRuntime().availableProcessors());
            w.println("Memória máxima da JVM: " + Runtime.getRuntime().maxMemory() / 1048576 + " MB");
            w.println("Dispositivo OpenCL: " + gpu.getNomeDispositivo());
            w.printf(Locale.ROOT, "Preparação do OpenCL: %.1f ms%n", gpu.getTempoPreparacaoMs());
            w.println("Palavras: " + palavras);
            w.println("Amostras por configuração: " + amostras + " | aquecimentos: " + aquecimentos);
            w.println("Threads na ParallelCPU: " + threads);
        }
    }
}
