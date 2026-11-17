package br.unifor.paralela;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Ponto de entrada.
 *
 * <pre>
 *   contar &lt;arquivo.txt&gt; &lt;palavra&gt;   executa os três métodos uma vez e mostra contagem e tempo
 *   benchmark [opções]                executa a bateria completa de testes e gera CSV + gráficos
 *   graficos                          regenera os gráficos a partir de resultados/resumo.csv
 *   gui                               abre a interface Swing (extra)
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            uso();
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "contar" -> {
                if (args.length < 3) {
                    uso();
                    return;
                }
                contar(Path.of(args[1]), args[2]);
            }
            case "benchmark" -> benchmark(Arrays.copyOfRange(args, 1, args.length));
            case "graficos" -> {
                Graficos.gerarTodos(Path.of("resultados", "resumo.csv"), Path.of("resultados", "graficos"));
                System.out.println("Gráficos gerados em resultados/graficos");
            }
            case "gui" -> Visualizador.abrir();
            default -> {
                // atalho: Main <arquivo.txt> <palavra>
                if (args.length >= 2 && Files.isRegularFile(Path.of(args[0]))) {
                    contar(Path.of(args[0]), args[1]);
                } else {
                    uso();
                }
            }
        }
    }

    /** Uma execução de cada método, com a saída no formato do enunciado. */
    static void contar(Path arquivo, String palavraDigitada) throws Exception {
        String palavra = Texto.normalizarPalavra(palavraDigitada);
        Texto texto = Texto.carregar(arquivo);
        System.out.printf(Locale.ROOT, "Arquivo: %s (%.2f MB) | palavra: \"%s\"%n%n",
                texto.nome, texto.tamanhoMB(), palavra);

        ParallelGPU gpu = new ParallelGPU();
        // Uma rodada de aquecimento de cada método (JIT da JVM e driver OpenCL), igual para os três.
        SerialCPU.contar(texto.chars, palavra);
        ParallelCPU.contar(texto.chars, palavra);
        gpu.contar(texto.bytes, palavra);

        long t0 = System.nanoTime();
        int c = SerialCPU.contar(texto.chars, palavra);
        System.out.printf("SerialCPU: %d ocorrências em %d ms%n", c, (System.nanoTime() - t0) / 1_000_000);

        t0 = System.nanoTime();
        c = ParallelCPU.contar(texto.chars, palavra);
        System.out.printf("ParallelCPU: %d ocorrências em %d ms%n", c, (System.nanoTime() - t0) / 1_000_000);

        try (gpu) {
            t0 = System.nanoTime();
            c = gpu.contar(texto.bytes, palavra);
            System.out.printf("ParallelGPU: %d ocorrências em %d ms%n", c, (System.nanoTime() - t0) / 1_000_000);
            System.out.printf(Locale.ROOT, "%n(ParallelCPU com %d threads; dispositivo OpenCL: %s;"
                            + " preparação do OpenCL: %.0f ms, não incluída)%n",
                    Runtime.getRuntime().availableProcessors(), gpu.getNomeDispositivo(), gpu.getTempoPreparacaoMs());
        }
    }

    /**
     * Opções: --palavras the,whale  --amostras 10 --aquecimento 3  --threads 1,2,4,8  --replicar 1,5,20
     */
    static void benchmark(String[] args) throws Exception {
        List<String> palavras = List.of("the", "whale");
        int amostras = 10;
        int aquecimento = 3;
        List<Integer> threads = Benchmark.threadsPadrao();
        int[] replicar = {1, 5, 20};
        for (int i = 0; i + 1 < args.length; i += 2) {
            String v = args[i + 1];
            switch (args[i]) {
                case "--palavras" -> palavras = Arrays.asList(v.split(","));
                case "--amostras" -> amostras = Math.max(3, Integer.parseInt(v));
                case "--aquecimento" -> aquecimento = Integer.parseInt(v);
                case "--threads" -> {
                    threads = new ArrayList<>();
                    for (String s : v.split(",")) {
                        threads.add(Integer.parseInt(s.trim()));
                    }
                }
                case "--replicar" -> replicar = Arrays.stream(v.split(",")).mapToInt(s -> Integer.parseInt(s.trim())).toArray();
                default -> throw new IllegalArgumentException("Opção desconhecida: " + args[i]);
            }
        }
        new Benchmark(Path.of("amostras"), Path.of("resultados"), palavras, amostras, aquecimento, threads, replicar)
                .executar();
    }

    private static void uso() {
        System.out.println("""
                Uso:
                  contar <arquivo.txt> <palavra>   conta a palavra com SerialCPU, ParallelCPU e ParallelGPU
                  benchmark [opções]               bateria completa de testes -> resultados/*.csv e gráficos
                      --palavras the,whale   --amostras 10  --aquecimento 3
                      --threads 1,2,4,8      --replicar 1,5,20
                  graficos                         regenera os gráficos a partir de resultados/resumo.csv
                  gui                              interface gráfica (Swing)
                Exemplo:
                  contar amostras/MobyDick-217452.txt whale""");
    }
}
