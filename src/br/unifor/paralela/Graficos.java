package br.unifor.paralela;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

/**
 * Gera os gráficos (PNG) a partir do resumo.csv usando apenas Java2D (sem bibliotecas externas).
 */
public final class Graficos {

    // Paleta: superfície clara, texto em cinza escuro, séries em ordem fixa.
    static final Color FUNDO = new Color(0xfcfcfb);
    static final Color TEXTO = new Color(0x0b0b0b);
    static final Color TEXTO_2 = new Color(0x52514e);
    static final Color GRADE = new Color(0xe4e3df);
    static final Color EIXO = new Color(0xa3a29c);
    static final Color[] SERIES = {
            new Color(0x2a78d6), new Color(0xeb6834), new Color(0x1baf7a), new Color(0xeda100),
            new Color(0xe87ba4), new Color(0x008300), new Color(0x6250d6), new Color(0xe34948)};

    static final int LARGURA = 1100;
    static final int ALTURA = 620;
    static final int M_ESQ = 90;
    static final int M_DIR = 230;
    static final int M_TOPO = 80;
    static final int M_BASE = 90;

    private Graficos() {
    }

    public static void gerarTodos(Path resumoCsv, Path pasta) throws IOException {
        Files.createDirectories(pasta);
        List<Estatistica.Linha> linhas = Estatistica.lerResumo(resumoCsv);
        Set<String> palavras = linhas.stream().map(Estatistica.Linha::palavra)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        for (String palavra : palavras) {
            List<Estatistica.Linha> ls = linhas.stream().filter(l -> l.palavra().equals(palavra))
                    .collect(Collectors.toList());
            tempoVsTamanho(ls, palavra, pasta.resolve("tempo_vs_tamanho_" + palavra + ".png"));
            speedupPorArquivo(ls, palavra, pasta.resolve("speedup_por_arquivo_" + palavra + ".png"));
            speedupPorThreads(ls, palavra, pasta.resolve("speedup_threads_" + palavra + ".png"));
            decomposicaoGpu(ls, palavra, pasta.resolve("gpu_decomposicao_" + palavra + ".png"));
        }
    }

    /** Threads da configuração "principal" da ParallelCPU: o nº de núcleos lógicos (ou o maior testado). */
    static int threadsPrincipal(List<Estatistica.Linha> ls) {
        int nucleos = Runtime.getRuntime().availableProcessors();
        TreeSet<Integer> ts = ls.stream().filter(l -> l.metodo().equals("ParallelCPU"))
                .map(Estatistica.Linha::threads).collect(Collectors.toCollection(TreeSet::new));
        if (ts.contains(nucleos)) {
            return nucleos;
        }
        Integer abaixo = ts.floor(nucleos);
        return abaixo != null ? abaixo : ts.first();
    }

    /** Ordem dos arquivos: por tamanho. */
    static List<String> arquivosPorTamanho(List<Estatistica.Linha> ls) {
        Map<String, Double> tam = new LinkedHashMap<>();
        ls.forEach(l -> tam.put(l.arquivo(), l.tamanhoMb()));
        return tam.entrySet().stream().sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).collect(Collectors.toList());
    }

    static String nomeCurto(String arquivo) {
        String s = arquivo.replace(".txt", "");
        int traco = s.indexOf('-');
        return traco > 0 && !s.startsWith("Concatenado") ? s.substring(0, traco) : s.replace("Concatenado", "Concat");
    }

    // ------------------------------------------------------------------ gráficos

    private static void tempoVsTamanho(List<Estatistica.Linha> ls, String palavra, Path destino) throws IOException {
        int t = threadsPrincipal(ls);
        List<String> arqs = arquivosPorTamanho(ls);
        String[] nomes = {"SerialCPU", "ParallelCPU (" + t + " threads)", "ParallelGPU"};
        List<double[][]> series = new ArrayList<>();
        for (int s = 0; s < 3; s++) {
            double[][] pts = new double[arqs.size()][];
            for (int i = 0; i < arqs.size(); i++) {
                String a = arqs.get(i);
                final int si = s;
                Estatistica.Linha l = ls.stream().filter(x -> x.arquivo().equals(a)
                        && (si == 0 ? x.metodo().equals("SerialCPU")
                        : si == 1 ? x.metodo().equals("ParallelCPU") && x.threads() == t
                        : x.metodo().equals("ParallelGPU"))).findFirst().orElseThrow();
                pts[i] = new double[]{l.tamanhoMb(), l.mediaMs()};
            }
            series.add(pts);
        }
        Tela tela = new Tela("Tempo médio x tamanho do arquivo — palavra \"" + palavra + "\"",
                "Média das amostras; eixos em escala logarítmica");
        tela.linhasLogLog(series, nomes, "Tamanho do arquivo (MB)", "Tempo médio (ms)", null);
        tela.salvar(destino);
    }

    private static void speedupPorArquivo(List<Estatistica.Linha> ls, String palavra, Path destino) throws IOException {
        int t = threadsPrincipal(ls);
        List<String> arqs = arquivosPorTamanho(ls);
        String[] nomes = {"ParallelCPU (" + t + " threads)", "ParallelGPU"};
        double[][] valores = new double[2][arqs.size()];
        String[] categorias = new String[arqs.size()];
        for (int i = 0; i < arqs.size(); i++) {
            String a = arqs.get(i);
            Estatistica.Linha cpu = ls.stream().filter(x -> x.arquivo().equals(a) && x.metodo().equals("ParallelCPU")
                    && x.threads() == t).findFirst().orElseThrow();
            Estatistica.Linha gpu = ls.stream().filter(x -> x.arquivo().equals(a) && x.metodo().equals("ParallelGPU"))
                    .findFirst().orElseThrow();
            valores[0][i] = cpu.speedup();
            valores[1][i] = gpu.speedup();
            categorias[i] = nomeCurto(a) + String.format(Locale.ROOT, " (%.1f MB)", cpu.tamanhoMb());
        }
        Tela tela = new Tela("Speedup em relação à SerialCPU — palavra \"" + palavra + "\"",
                "Speedup = tempo médio serial / tempo médio do método; acima de 1 = mais rápido que o serial");
        tela.barrasAgrupadas(categorias, valores, nomes, new Color[]{SERIES[1], SERIES[2]}, "Speedup (x)", 1.0,
                "SerialCPU = 1");
        tela.salvar(destino);
    }

    private static void speedupPorThreads(List<Estatistica.Linha> ls, String palavra, Path destino) throws IOException {
        List<String> arqs = arquivosPorTamanho(ls);
        List<Integer> threads = ls.stream().filter(l -> l.metodo().equals("ParallelCPU"))
                .map(Estatistica.Linha::threads).distinct().sorted().collect(Collectors.toList());
        List<double[][]> series = new ArrayList<>();
        String[] nomes = new String[arqs.size()];
        for (int i = 0; i < arqs.size(); i++) {
            String a = arqs.get(i);
            double[][] pts = new double[threads.size()][];
            for (int k = 0; k < threads.size(); k++) {
                int t = threads.get(k);
                Estatistica.Linha l = ls.stream().filter(x -> x.arquivo().equals(a) && x.metodo().equals("ParallelCPU")
                        && x.threads() == t).findFirst().orElseThrow();
                pts[k] = new double[]{t, l.speedup()};
            }
            series.add(pts);
            nomes[i] = nomeCurto(a);
        }
        int nucleos = Runtime.getRuntime().availableProcessors();
        Tela tela = new Tela("ParallelCPU: speedup x número de threads — palavra \"" + palavra + "\"",
                "Máquina com " + nucleos + " núcleos lógicos; linha tracejada = speedup ideal (limitado aos núcleos)");
        tela.linhasThreads(series, nomes, threads, nucleos);
        tela.salvar(destino);
    }

    private static void decomposicaoGpu(List<Estatistica.Linha> ls, String palavra, Path destino) throws IOException {
        List<String> arqs = arquivosPorTamanho(ls);
        String[] categorias = new String[arqs.size()];
        double[][] partes = new double[3][arqs.size()];
        for (int i = 0; i < arqs.size(); i++) {
            String a = arqs.get(i);
            Estatistica.Linha g = ls.stream().filter(x -> x.arquivo().equals(a) && x.metodo().equals("ParallelGPU"))
                    .findFirst().orElseThrow();
            double resto = Math.max(0, g.mediaMs() - g.kernelMs() - g.transferenciaMs());
            partes[0][i] = 100 * g.transferenciaMs() / g.mediaMs();
            partes[1][i] = 100 * g.kernelMs() / g.mediaMs();
            partes[2][i] = 100 * resto / g.mediaMs();
            categorias[i] = nomeCurto(a) + String.format(Locale.ROOT, " (%.1f ms)", g.mediaMs());
        }
        Tela tela = new Tela("ParallelGPU: para onde vai o tempo — palavra \"" + palavra + "\"",
                "Percentual do tempo total de cada contagem (tempo médio entre parênteses)");
        tela.barrasEmpilhadas100(categorias, partes,
                new String[]{"Cópia do texto", "Execução do kernel", "Alocação e leitura"},
                new Color[]{SERIES[0], SERIES[2], SERIES[3]});
        tela.salvar(destino);
    }

    // ------------------------------------------------------------------ desenho

    /** Área de desenho com título, eixos e legenda. */
    static final class Tela {
        final BufferedImage img = new BufferedImage(LARGURA, ALTURA, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = img.createGraphics();
        final int x0 = M_ESQ;
        final int x1 = LARGURA - M_DIR;
        final int y0 = ALTURA - M_BASE;
        final int y1 = M_TOPO;

        Tela(String titulo, String subtitulo) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(FUNDO);
            g.fillRect(0, 0, LARGURA, ALTURA);
            g.setColor(TEXTO);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 19));
            g.drawString(titulo, x0 - 50, 34);
            g.setColor(TEXTO_2);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.drawString(subtitulo, x0 - 50, 56);
        }

        void salvar(Path destino) throws IOException {
            g.dispose();
            ImageIO.write(img, "png", destino.toFile());
        }

        private void fonte(int tam) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, tam));
        }

        private void textoCentro(String s, double cx, double y) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, (float) (cx - fm.stringWidth(s) / 2.0), (float) y);
        }

        private void textoDireita(String s, double xd, double y) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, (float) (xd - fm.stringWidth(s)), (float) y);
        }

        private void tituloEixoY(String s) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(TEXTO_2);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g2.rotate(-Math.PI / 2);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(s, -(y0 + y1) / 2f - fm.stringWidth(s) / 2f, 24);
            g2.dispose();
        }

        private void tituloEixoX(String s) {
            g.setColor(TEXTO_2);
            fonte(13);
            textoCentro(s, (x0 + x1) / 2.0, ALTURA - 22);
        }

        private void linhaGradeY(double y, String rotulo) {
            g.setColor(GRADE);
            g.setStroke(new BasicStroke(1f));
            g.draw(new Line2D.Double(x0, y, x1, y));
            g.setColor(TEXTO_2);
            fonte(12);
            textoDireita(rotulo, x0 - 8, y + 4);
        }

        private void eixoBase() {
            g.setColor(EIXO);
            g.setStroke(new BasicStroke(1f));
            g.draw(new Line2D.Double(x0, y0, x1, y0));
        }

        private void legenda(String[] nomes, Color[] cores, boolean linha) {
            fonte(13);
            int lx = x1 + 24;
            int ly = y1 + 6;
            for (int i = 0; i < nomes.length; i++) {
                g.setColor(cores[i]);
                if (linha) {
                    g.setStroke(new BasicStroke(2f));
                    g.draw(new Line2D.Double(lx, ly + i * 26, lx + 22, ly + i * 26));
                    g.fill(new Ellipse2D.Double(lx + 7, ly + i * 26 - 4, 8, 8));
                } else {
                    g.fill(new RoundRectangle2D.Double(lx, ly + i * 26 - 7, 14, 14, 4, 4));
                }
                g.setColor(TEXTO);
                g.drawString(nomes[i], lx + 30, ly + i * 26 + 5);
            }
        }

        /** Linhas com eixos X e Y logarítmicos. */
        void linhasLogLog(List<double[][]> series, String[] nomes, String eixoX, String eixoY, Color[] cores) {
            double minX = Double.MAX_VALUE, maxX = 0, minY = Double.MAX_VALUE, maxY = 0;
            for (double[][] s : series) {
                for (double[] p : s) {
                    minX = Math.min(minX, p[0]);
                    maxX = Math.max(maxX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxY = Math.max(maxY, p[1]);
                }
            }
            double lx0 = Math.floor(Math.log10(minX) * 2) / 2 - 0.1, lx1 = Math.log10(maxX) + 0.15;
            double ly0 = Math.floor(Math.log10(minY)), ly1 = Math.ceil(Math.log10(maxY));
            if (ly1 - ly0 < 1) {
                ly1 = ly0 + 1;
            }
            final double fx0 = lx0, fx1 = lx1, fy0 = ly0, fy1 = ly1;
            java.util.function.DoubleUnaryOperator px = v -> x0 + (Math.log10(v) - fx0) / (fx1 - fx0) * (x1 - x0);
            java.util.function.DoubleUnaryOperator py = v -> y0 - (Math.log10(v) - fy0) / (fy1 - fy0) * (y0 - y1);

            for (int e = (int) ly0; e <= (int) ly1; e++) {
                for (int mult : new int[]{1, 2, 5}) {
                    double v = mult * Math.pow(10, e);
                    if (Math.log10(v) > ly1 + 1e-9) {
                        continue;
                    }
                    linhaGradeY(py.applyAsDouble(v), formatar(v));
                }
            }
            eixoBase();
            fonte(12);
            for (int e = (int) Math.floor(lx0); e <= (int) Math.ceil(lx1); e++) {
                for (int mult : new int[]{1, 2, 5}) {
                    double v = mult * Math.pow(10, e);
                    double lv = Math.log10(v);
                    if (lv < lx0 || lv > lx1) {
                        continue;
                    }
                    double x = px.applyAsDouble(v);
                    g.setColor(EIXO);
                    g.draw(new Line2D.Double(x, y0, x, y0 + 5));
                    g.setColor(TEXTO_2);
                    textoCentro(formatar(v), x, y0 + 20);
                }
            }
            tituloEixoX(eixoX);
            tituloEixoY(eixoY);

            Color[] cs = cores != null ? cores : SERIES;
            for (int s = 0; s < series.size(); s++) {
                desenharSerie(series.get(s), cs[s], px, py);
            }
            legenda(nomes, cs, true);
        }

        private void desenharSerie(double[][] pts, Color cor, java.util.function.DoubleUnaryOperator px,
                                   java.util.function.DoubleUnaryOperator py) {
            Path2D caminho = new Path2D.Double();
            for (int i = 0; i < pts.length; i++) {
                double x = px.applyAsDouble(pts[i][0]), y = py.applyAsDouble(pts[i][1]);
                if (i == 0) {
                    caminho.moveTo(x, y);
                } else {
                    caminho.lineTo(x, y);
                }
            }
            g.setColor(cor);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(caminho);
            for (double[] p : pts) {
                double x = px.applyAsDouble(p[0]), y = py.applyAsDouble(p[1]);
                g.setColor(FUNDO);
                g.fill(new Ellipse2D.Double(x - 6, y - 6, 12, 12));
                g.setColor(cor);
                g.fill(new Ellipse2D.Double(x - 4.5, y - 4.5, 9, 9));
            }
        }

        /** Speedup x threads (eixo X em log2), com a linha de speedup ideal. */
        void linhasThreads(List<double[][]> series, String[] nomes, List<Integer> threads, int nucleos) {
            double maxY = 0;
            for (double[][] s : series) {
                for (double[] p : s) {
                    maxY = Math.max(maxY, p[1]);
                }
            }
            maxY = Math.max(maxY, Math.min(nucleos, threads.get(threads.size() - 1)));
            double topo = Math.ceil(maxY * 1.15 * 2) / 2;
            double lmin = Math.log(threads.get(0)) / Math.log(2), lmax = Math.log(threads.get(threads.size() - 1)) / Math.log(2);
            final double fl0 = lmin - 0.3, fl1 = lmax + 0.3, ft = topo;
            java.util.function.DoubleUnaryOperator px = v -> x0 + (Math.log(v) / Math.log(2) - fl0) / (fl1 - fl0) * (x1 - x0);
            java.util.function.DoubleUnaryOperator py = v -> y0 - v / ft * (y0 - y1);

            double passo = topo <= 3 ? 0.5 : topo <= 8 ? 1 : 2;
            for (double v = 0; v <= topo + 1e-9; v += passo) {
                linhaGradeY(py.applyAsDouble(v), formatar(v) + "x");
            }
            eixoBase();
            fonte(12);
            for (int t : threads) {
                double x = px.applyAsDouble(t);
                g.setColor(EIXO);
                g.draw(new Line2D.Double(x, y0, x, y0 + 5));
                g.setColor(TEXTO_2);
                textoCentro(String.valueOf(t), x, y0 + 20);
            }
            tituloEixoX("Número de threads no pool");
            tituloEixoY("Speedup sobre a SerialCPU (x)");

            // ideal: min(threads, núcleos)
            Path2D ideal = new Path2D.Double();
            for (int i = 0; i < threads.size(); i++) {
                int t = threads.get(i);
                double x = px.applyAsDouble(t), y = py.applyAsDouble(Math.min(t, nucleos));
                if (i == 0) {
                    ideal.moveTo(x, y);
                } else {
                    ideal.lineTo(x, y);
                }
            }
            g.setColor(EIXO);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{6, 5}, 0));
            g.draw(ideal);

            for (int s = 0; s < series.size(); s++) {
                desenharSerie(series.get(s), SERIES[s % SERIES.length], px, py);
            }
            String[] nomesLeg = new String[nomes.length + 1];
            Color[] coresLeg = new Color[nomes.length + 1];
            for (int i = 0; i < nomes.length; i++) {
                nomesLeg[i] = nomes[i];
                coresLeg[i] = SERIES[i % SERIES.length];
            }
            nomesLeg[nomes.length] = "Ideal";
            coresLeg[nomes.length] = EIXO;
            legenda(nomesLeg, coresLeg, true);
        }

        /** Barras agrupadas com rótulo de valor e linha de referência opcional. */
        void barrasAgrupadas(String[] categorias, double[][] valores, String[] nomes, Color[] cores, String eixoY,
                             double referencia, String rotuloReferencia) {
            double maxV = referencia;
            for (double[] s : valores) {
                for (double v : s) {
                    maxV = Math.max(maxV, v);
                }
            }
            double topo = Math.ceil(maxV * 1.15 * 2) / 2;
            final double ft = topo;
            java.util.function.DoubleUnaryOperator py = v -> y0 - v / ft * (y0 - y1);
            double passo = topo <= 3 ? 0.5 : topo <= 8 ? 1 : 2;
            for (double v = 0; v <= topo + 1e-9; v += passo) {
                linhaGradeY(py.applyAsDouble(v), formatar(v) + "x");
            }
            eixoBase();
            if (referencia > 0) {
                double yr = py.applyAsDouble(referencia);
                g.setColor(TEXTO_2);
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{6, 5}, 0));
                g.draw(new Line2D.Double(x0, yr, x1, yr));
                fonte(12);
                g.drawString(rotuloReferencia, x1 + 6, (float) yr + 4);
            }

            int nCat = categorias.length, nSer = valores.length;
            double larguraCat = (x1 - x0) / (double) nCat;
            double larguraBarra = Math.min(46, larguraCat * 0.7 / nSer);
            for (int c = 0; c < nCat; c++) {
                double cx = x0 + larguraCat * (c + 0.5);
                double inicio = cx - larguraBarra * nSer / 2.0;
                for (int s = 0; s < nSer; s++) {
                    double v = valores[s][c];
                    double bx = inicio + s * larguraBarra + 1;
                    double by = py.applyAsDouble(v);
                    g.setColor(cores[s]);
                    barraArredondada(bx, by, larguraBarra - 2, y0 - by);
                    fonte(11);
                    String rot = String.format(Locale.ROOT, "%.2f", v);
                    int lw = g.getFontMetrics().stringWidth(rot);
                    g.setColor(FUNDO);
                    g.fillRect((int) (bx + (larguraBarra - 2) / 2 - lw / 2.0 - 2), (int) by - 17, lw + 4, 14);
                    g.setColor(TEXTO);
                    textoCentro(rot, bx + (larguraBarra - 2) / 2, by - 5);
                }
                g.setColor(TEXTO_2);
                fonte(12);
                String[] partes = categorias[c].split(" \\(");
                textoCentro(partes[0], cx, y0 + 20);
                if (partes.length > 1) {
                    textoCentro("(" + partes[1], cx, y0 + 36);
                }
            }
            tituloEixoY(eixoY);
            legenda(nomes, cores, false);
        }

        /** Barras 100% empilhadas, com 2px de separação entre segmentos. */
        void barrasEmpilhadas100(String[] categorias, double[][] partes, String[] nomes, Color[] cores) {
            java.util.function.DoubleUnaryOperator py = v -> y0 - v / 100.0 * (y0 - y1);
            for (int v = 0; v <= 100; v += 20) {
                linhaGradeY(py.applyAsDouble(v), v + "%");
            }
            eixoBase();
            int nCat = categorias.length;
            double larguraCat = (x1 - x0) / (double) nCat;
            double larguraBarra = Math.min(70, larguraCat * 0.6);
            for (int c = 0; c < nCat; c++) {
                double cx = x0 + larguraCat * (c + 0.5);
                double acumulado = 0;
                for (int s = 0; s < partes.length; s++) {
                    double v = partes[s][c];
                    double yTopo = py.applyAsDouble(acumulado + v);
                    double yBase = py.applyAsDouble(acumulado);
                    double h = yBase - yTopo - 2;
                    if (h > 0.5) {
                        g.setColor(cores[s]);
                        g.fill(new java.awt.geom.Rectangle2D.Double(cx - larguraBarra / 2, yTopo, larguraBarra, h));
                        if (h > 16) {
                            g.setColor(Color.WHITE);
                            fonte(11);
                            textoCentro(String.format(Locale.ROOT, "%.0f%%", v), cx, yTopo + h / 2 + 4);
                        }
                    }
                    acumulado += v;
                }
                g.setColor(TEXTO_2);
                fonte(12);
                String[] p = categorias[c].split(" \\(");
                textoCentro(p[0], cx, y0 + 20);
                if (p.length > 1) {
                    textoCentro("(" + p[1], cx, y0 + 36);
                }
            }
            tituloEixoY("% do tempo da contagem");
            legenda(nomes, cores, false);
        }

        /** Barra com cantos de 4px arredondados só no topo (a base fica reta, presa ao eixo). */
        private void barraArredondada(double x, double y, double w, double h) {
            if (h <= 0) {
                return;
            }
            double r = Math.min(4, h);
            Path2D p = new Path2D.Double();
            p.moveTo(x, y + h);
            p.lineTo(x, y + r);
            p.quadTo(x, y, x + r, y);
            p.lineTo(x + w - r, y);
            p.quadTo(x + w, y, x + w, y + r);
            p.lineTo(x + w, y + h);
            p.closePath();
            g.fill(p);
        }

        static String formatar(double v) {
            if (v == 0) {
                return "0";
            }
            if (v >= 1000) {
                return String.format(Locale.ROOT, "%,.0f", v).replace(',', '.');
            }
            if (v >= 1 && v == Math.rint(v)) {
                return String.valueOf((long) v);
            }
            if (v >= 0.1) {
                return String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
            }
            return String.format(Locale.ROOT, "%.2f", v).replace('.', ',');
        }
    }
}
