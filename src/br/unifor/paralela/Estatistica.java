package br.unifor.paralela;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Análise estatística das amostras: média, desvio padrão, coeficiente de variação,
 * mínimo, máximo, intervalo de confiança de 95% (t de Student), speedup e eficiência.
 */
public final class Estatistica {

    private Estatistica() {
    }

    /** Uma linha do resumo.csv. */
    public record Linha(String palavra, String arquivo, double tamanhoMb, String metodo, int threads,
                        int amostras, int ocorrencias, double mediaMs, double desvioMs, double cvPercent,
                        double minMs, double maxMs, double ic95Ms, double speedup, double eficiencia,
                        double kernelMs, double transferenciaMs) {

        /** Nome curto para legendas: "SerialCPU", "ParallelCPU 4T", "ParallelGPU". */
        public String rotulo() {
            return metodo.equals("ParallelCPU") ? metodo + " " + threads + "T" : metodo;
        }
    }

    static List<Linha> resumir(List<Benchmark.Execucao> execucoes, int nucleos) {
        Map<String, List<Benchmark.Execucao>> grupos = new LinkedHashMap<>();
        for (Benchmark.Execucao e : execucoes) {
            String chave = e.palavra() + "|" + e.arquivo() + "|" + e.metodo() + "|" + e.threads();
            grupos.computeIfAbsent(chave, k -> new ArrayList<>()).add(e);
        }

        // média serial de cada (palavra, arquivo) = referência para o speedup
        Map<String, Double> serial = new LinkedHashMap<>();
        grupos.values().stream().filter(g -> g.get(0).metodo().equals("SerialCPU")).forEach(g ->
                serial.put(g.get(0).palavra() + "|" + g.get(0).arquivo(), media(tempos(g))));

        List<Linha> linhas = new ArrayList<>();
        for (List<Benchmark.Execucao> g : grupos.values()) {
            Benchmark.Execucao p = g.get(0);
            double[] t = tempos(g);
            double media = media(t);
            double desvio = desvio(t, media);
            double ic = tStudent95(t.length - 1) * desvio / Math.sqrt(t.length);
            double sp = serial.get(p.palavra() + "|" + p.arquivo()) / media;
            // eficiência = speedup / nº de unidades usadas (só faz sentido para a ParallelCPU)
            double ef = p.metodo().equals("ParallelCPU") ? sp / Math.min(p.threads(), nucleos)
                    : p.metodo().equals("SerialCPU") ? 1.0 : Double.NaN;
            double kernel = g.stream().mapToDouble(Benchmark.Execucao::kernelMs).average().orElse(0);
            double transf = g.stream().mapToDouble(Benchmark.Execucao::transferenciaMs).average().orElse(0);
            linhas.add(new Linha(p.palavra(), p.arquivo(), p.tamanhoBytes() / 1048576.0, p.metodo(), p.threads(),
                    t.length, p.ocorrencias(), media, desvio, 100 * desvio / media, min(t), max(t), ic, sp, ef,
                    kernel, transf));
        }
        return linhas;
    }

    static void gravarResumo(Path csv, List<Linha> linhas) throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            w.println("palavra,arquivo,tamanho_mb,metodo,threads,amostras,ocorrencias,media_ms,desvio_padrao_ms,"
                    + "cv_percent,min_ms,max_ms,ic95_ms,speedup,eficiencia,kernel_ms,transferencia_ms");
            for (Linha l : linhas) {
                w.printf(Locale.ROOT, "%s,%s,%.3f,%s,%d,%d,%d,%.4f,%.4f,%.2f,%.4f,%.4f,%.4f,%.4f,%s,%.4f,%.4f%n",
                        l.palavra(), l.arquivo(), l.tamanhoMb(), l.metodo(), l.threads(), l.amostras(),
                        l.ocorrencias(), l.mediaMs(), l.desvioMs(), l.cvPercent(), l.minMs(), l.maxMs(), l.ic95Ms(),
                        l.speedup(), Double.isNaN(l.eficiencia()) ? "" : String.format(Locale.ROOT, "%.4f", l.eficiencia()),
                        l.kernelMs(), l.transferenciaMs());
            }
        }
    }

    /** Lê o resumo.csv (usado para gerar os gráficos sem precisar rodar tudo de novo). */
    public static List<Linha> lerResumo(Path csv) throws IOException {
        List<String> ls = Files.readAllLines(csv, StandardCharsets.UTF_8);
        List<Linha> linhas = new ArrayList<>();
        for (int i = 1; i < ls.size(); i++) {
            if (ls.get(i).isBlank()) {
                continue;
            }
            String[] c = ls.get(i).split(",", -1);
            linhas.add(new Linha(c[0], c[1], d(c[2]), c[3], Integer.parseInt(c[4]), Integer.parseInt(c[5]),
                    Integer.parseInt(c[6]), d(c[7]), d(c[8]), d(c[9]), d(c[10]), d(c[11]), d(c[12]), d(c[13]),
                    c[14].isEmpty() ? Double.NaN : d(c[14]), d(c[15]), d(c[16])));
        }
        return linhas;
    }

    private static double d(String s) {
        return Double.parseDouble(s);
    }

    private static double[] tempos(List<Benchmark.Execucao> g) {
        return g.stream().mapToDouble(Benchmark.Execucao::tempoMs).toArray();
    }

    static double media(double[] v) {
        double s = 0;
        for (double x : v) {
            s += x;
        }
        return s / v.length;
    }

    /** Desvio padrão amostral (n - 1). */
    static double desvio(double[] v, double media) {
        if (v.length < 2) {
            return 0;
        }
        double s = 0;
        for (double x : v) {
            s += (x - media) * (x - media);
        }
        return Math.sqrt(s / (v.length - 1));
    }

    private static double min(double[] v) {
        double m = Double.MAX_VALUE;
        for (double x : v) {
            m = Math.min(m, x);
        }
        return m;
    }

    private static double max(double[] v) {
        double m = -Double.MAX_VALUE;
        for (double x : v) {
            m = Math.max(m, x);
        }
        return m;
    }

    /** Valor crítico bicaudal de 95% da distribuição t de Student. */
    private static double tStudent95(int gl) {
        double[] tabela = {Double.NaN, 12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
                2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086};
        if (gl <= 0) {
            return 0;
        }
        return gl < tabela.length ? tabela[gl] : 1.96;
    }
}
