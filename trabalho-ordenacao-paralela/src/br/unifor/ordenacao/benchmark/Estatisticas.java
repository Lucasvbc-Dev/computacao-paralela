package br.unifor.ordenacao.benchmark;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Análise estatística dos resultados brutos.
 * Agrupa as amostras por (núcleos, algoritmo, distribuição, tamanho, configuração) e calcula
 * média, desvio padrão amostral, mínimo, máximo, mediana, coeficiente de variação,
 * intervalo de confiança de 95% (t de Student), speedup e eficiência.
 *
 * Uso pela linha de comando:
 *   java br.unifor.ordenacao.benchmark.Estatisticas resumo.csv brutos1.csv [brutos2.csv ...]
 */
public final class Estatisticas {

    public static final String CABECALHO = "nucleos,algoritmo,distribuicao,tamanho,versao,threads,amostras,"
            + "media_ms,desvio_ms,min_ms,max_ms,mediana_ms,cv_pct,ic95_ms,speedup,eficiencia";

    /** Valores críticos t(0,975; gl) para gl = 1..30. */
    private static final double[] T_975 = {
        12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
        2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
        2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042
    };

    private Estatisticas() {
    }

    /** Linha do resumo estatístico. */
    public static final class Linha {
        public int nucleos;
        public String algoritmo;
        public String distribuicao;
        public int tamanho;
        public String versao;
        public int threads;
        public int amostras;
        public double media;
        public double desvio;
        public double min;
        public double max;
        public double mediana;
        public double cv;
        public double ic95;
        public double speedup;
        public double eficiencia;

        public String configuracao() {
            return "SERIAL".equals(versao) ? "SERIAL" : "P" + threads;
        }

        String paraCsv() {
            return String.format(Locale.US,
                    "%d,%s,%s,%d,%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.2f,%.4f,%.4f,%.4f",
                    nucleos, algoritmo, distribuicao, tamanho, versao, threads, amostras,
                    media, desvio, min, max, mediana, cv, ic95, speedup, eficiencia);
        }
    }

    public static List<Linha> calcular(List<Medicao> medicoes) {
        Map<String, List<Medicao>> grupos = new LinkedHashMap<String, List<Medicao>>();
        for (Medicao m : medicoes) {
            String chave = m.nucleos + "|" + m.algoritmo + "|" + m.distribuicao + "|" + m.tamanho + "|"
                    + m.configuracao();
            List<Medicao> g = grupos.get(chave);
            if (g == null) {
                g = new ArrayList<Medicao>();
                grupos.put(chave, g);
            }
            g.add(m);
        }

        List<Linha> linhas = new ArrayList<Linha>();
        Map<String, Double> mediaSerial = new HashMap<String, Double>();
        for (List<Medicao> g : grupos.values()) {
            Medicao ref = g.get(0);
            double[] t = new double[g.size()];
            for (int i = 0; i < t.length; i++) {
                t[i] = g.get(i).tempoMs;
            }
            Linha l = new Linha();
            l.nucleos = ref.nucleos;
            l.algoritmo = ref.algoritmo;
            l.distribuicao = ref.distribuicao;
            l.tamanho = ref.tamanho;
            l.versao = ref.versao;
            l.threads = ref.threads;
            l.amostras = t.length;
            preencherEstatisticas(l, t);
            linhas.add(l);
            if ("SERIAL".equals(l.versao)) {
                mediaSerial.put(chaveBase(l), l.media);
            }
        }

        for (Linha l : linhas) {
            Double serial = mediaSerial.get(chaveBase(l));
            if (serial != null && l.media > 0) {
                l.speedup = serial / l.media;
                l.eficiencia = l.speedup / l.threads;
            } else {
                l.speedup = Double.NaN;
                l.eficiencia = Double.NaN;
            }
        }

        Collections.sort(linhas, new Comparator<Linha>() {
            public int compare(Linha a, Linha b) {
                int c = Integer.compare(a.nucleos, b.nucleos);
                if (c == 0) c = a.algoritmo.compareTo(b.algoritmo);
                if (c == 0) c = a.distribuicao.compareTo(b.distribuicao);
                if (c == 0) c = Integer.compare(a.tamanho, b.tamanho);
                if (c == 0) c = Integer.compare("SERIAL".equals(a.versao) ? 0 : 1, "SERIAL".equals(b.versao) ? 0 : 1);
                if (c == 0) c = Integer.compare(a.threads, b.threads);
                return c;
            }
        });
        return linhas;
    }

    private static String chaveBase(Linha l) {
        return l.nucleos + "|" + l.algoritmo + "|" + l.distribuicao + "|" + l.tamanho;
    }

    private static void preencherEstatisticas(Linha l, double[] t) {
        int n = t.length;
        double soma = 0;
        for (double x : t) {
            soma += x;
        }
        double media = soma / n;
        double sq = 0;
        for (double x : t) {
            sq += (x - media) * (x - media);
        }
        double desvio = n > 1 ? Math.sqrt(sq / (n - 1)) : 0;
        double[] ord = t.clone();
        Arrays.sort(ord);
        double mediana = (n % 2 == 1) ? ord[n / 2] : (ord[n / 2 - 1] + ord[n / 2]) / 2.0;
        double tCrit = n > 1 ? (n - 1 <= T_975.length ? T_975[n - 2] : 1.96) : 0;

        l.media = media;
        l.desvio = desvio;
        l.min = ord[0];
        l.max = ord[n - 1];
        l.mediana = mediana;
        l.cv = media > 0 ? 100.0 * desvio / media : 0;
        l.ic95 = n > 1 ? tCrit * desvio / Math.sqrt(n) : 0;
    }

    public static List<Medicao> lerBrutos(String... arquivos) throws IOException {
        List<Medicao> lista = new ArrayList<Medicao>();
        for (String arq : arquivos) {
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(arq), StandardCharsets.UTF_8));
            try {
                String linha = br.readLine(); // cabeçalho
                while ((linha = br.readLine()) != null) {
                    if (!linha.trim().isEmpty()) {
                        lista.add(Medicao.deCsv(linha.trim()));
                    }
                }
            } finally {
                br.close();
            }
        }
        return lista;
    }

    public static void escreverResumo(List<Linha> linhas, String arquivo) throws IOException {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                new java.io.FileOutputStream(arquivo), StandardCharsets.UTF_8));
        try {
            pw.println(CABECALHO);
            for (Linha l : linhas) {
                pw.println(l.paraCsv());
            }
        } finally {
            pw.close();
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Uso: Estatisticas <resumo.csv> <brutos1.csv> [brutos2.csv ...]");
            System.exit(1);
        }
        String[] entradas = Arrays.copyOfRange(args, 1, args.length);
        List<Linha> linhas = calcular(lerBrutos(entradas));
        escreverResumo(linhas, args[0]);
        System.out.println("Resumo gravado em " + args[0] + " (" + linhas.size() + " linhas)");
    }
}
