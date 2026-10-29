package br.unifor.ordenacao.grafico;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gráfico de linhas desenhado com Java2D, sem bibliotecas externas.
 * Suporta escala logarítmica nos eixos, barras de erro (IC 95%), linha de referência
 * e tooltip com o valor do ponto mais próximo do cursor.
 */
public class GraficoLinhas extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Uma série de dados do gráfico. */
    public static final class Serie {
        final String nome;
        final List<double[]> pontos = new ArrayList<double[]>(); // {x, y, erro}

        public Serie(String nome) {
            this.nome = nome;
        }

        public void adicionar(double x, double y, double erro) {
            pontos.add(new double[] {x, y, erro});
        }
    }

    private static final Color[] CORES = {
        new Color(0x1f, 0x4e, 0x79), new Color(0xc0, 0x50, 0x1e), new Color(0x2e, 0x7d, 0x32),
        new Color(0x6a, 0x1b, 0x9a), new Color(0x8d, 0x6e, 0x00), new Color(0x00, 0x83, 0x8f),
        new Color(0xad, 0x14, 0x57), new Color(0x45, 0x45, 0x45)
    };
    private static final float[][] TRACOS = {
        null, {8f, 4f}, {2f, 3f}, {10f, 3f, 2f, 3f}, {5f, 5f}, null, {8f, 4f}, {2f, 3f}
    };

    private final List<Serie> series = new ArrayList<Serie>();
    private String titulo = "";
    private String rotuloX = "";
    private String rotuloY = "";
    private boolean logX;
    private boolean logY;
    private boolean linhaIdeal;

    private double xMin, xMax, yMin, yMax;
    private int esq, dir, topo, base;

    public GraficoLinhas() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(900, 520));
        setToolTipText("");
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                setToolTipText(textoPontoProximo(e.getX(), e.getY()));
            }
        });
    }

    public void definir(String titulo, String rotuloX, String rotuloY, List<Serie> series,
                        boolean logX, boolean logY, boolean linhaIdeal) {
        this.titulo = titulo;
        this.rotuloX = rotuloX;
        this.rotuloY = rotuloY;
        this.series.clear();
        this.series.addAll(series);
        this.logX = logX;
        this.logY = logY;
        this.linhaIdeal = linhaIdeal;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        desenhar((Graphics2D) g0, getWidth(), getHeight());
    }

    /** Desenha o gráfico em qualquer contexto gráfico (tela ou imagem PNG). */
    public void desenhar(Graphics2D g, int largura, int altura) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, largura, altura);

        esq = 80;
        dir = largura - 30;
        topo = 50;
        base = altura - 60;

        g.setColor(Color.BLACK);
        g.setFont(getFont().deriveFont(Font.BOLD, 15f));
        centralizar(g, titulo, largura / 2, 28);

        if (!calcularLimites()) {
            g.setFont(getFont().deriveFont(13f));
            centralizar(g, "Sem dados para a seleção atual", largura / 2, altura / 2);
            return;
        }

        desenharEixos(g);

        if (linhaIdeal) {
            g.setColor(Color.GRAY);
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {4f, 4f}, 0f));
            double a = Math.max(xMin, yMin);
            double b = Math.min(xMax, yMax);
            if (b > a) {
                g.drawLine(px(a), py(a), px(b), py(b));
                g.setFont(getFont().deriveFont(11f));
                g.drawString("ideal (linear)", px(b) - 75, py(b) - 6);
            }
        }

        for (int s = 0; s < series.size(); s++) {
            Serie serie = series.get(s);
            Color cor = CORES[s % CORES.length];
            float[] traco = TRACOS[s % TRACOS.length];
            Stroke st = traco == null ? new BasicStroke(2f)
                    : new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, traco, 0f);
            g.setColor(cor);
            int xa = -1;
            int ya = -1;
            for (double[] p : serie.pontos) {
                if (!valido(p[0], logX) || !valido(p[1], logY)) {
                    continue;
                }
                int x = px(p[0]);
                int y = py(p[1]);
                if (p[2] > 0) {
                    g.setStroke(new BasicStroke(1f));
                    int y1 = py(Math.max(p[1] - p[2], logY ? p[1] / 10 : yMin));
                    int y2 = py(p[1] + p[2]);
                    g.drawLine(x, y1, x, y2);
                    g.drawLine(x - 3, y1, x + 3, y1);
                    g.drawLine(x - 3, y2, x + 3, y2);
                }
                if (xa >= 0) {
                    g.setStroke(st);
                    g.drawLine(xa, ya, x, y);
                }
                g.setStroke(new BasicStroke(1.5f));
                marcador(g, s, x, y);
                xa = x;
                ya = y;
            }
        }
        desenharLegenda(g);
    }

    private boolean valido(double v, boolean log) {
        return !Double.isNaN(v) && !Double.isInfinite(v) && (!log || v > 0);
    }

    private boolean calcularLimites() {
        xMin = Double.MAX_VALUE;
        xMax = -Double.MAX_VALUE;
        yMin = Double.MAX_VALUE;
        yMax = -Double.MAX_VALUE;
        boolean algum = false;
        for (Serie s : series) {
            for (double[] p : s.pontos) {
                if (!valido(p[0], logX) || !valido(p[1], logY)) {
                    continue;
                }
                algum = true;
                xMin = Math.min(xMin, p[0]);
                xMax = Math.max(xMax, p[0]);
                yMin = Math.min(yMin, p[1] - (logY ? 0 : p[2]));
                yMax = Math.max(yMax, p[1] + p[2]);
            }
        }
        if (!algum) {
            return false;
        }
        if (linhaIdeal) {
            yMax = Math.max(yMax, xMax);
        }
        if (logX) {
            xMin = Math.pow(10, Math.floor(Math.log10(xMin)));
            xMax = Math.pow(10, Math.ceil(Math.log10(xMax)));
        } else if (xMax == xMin) {
            xMax = xMin + 1;
        }
        if (logY) {
            yMin = Math.pow(10, Math.floor(Math.log10(yMin)));
            yMax = Math.pow(10, Math.ceil(Math.log10(yMax)));
        } else {
            yMin = Math.min(0, yMin);
            yMax = yMax * 1.08;
            if (yMax == yMin) {
                yMax = yMin + 1;
            }
        }
        return true;
    }

    private int px(double x) {
        double f = logX ? (Math.log10(x) - Math.log10(xMin)) / (Math.log10(xMax) - Math.log10(xMin))
                : (x - xMin) / (xMax - xMin);
        return (int) Math.round(esq + f * (dir - esq));
    }

    private int py(double y) {
        double f = logY ? (Math.log10(y) - Math.log10(yMin)) / (Math.log10(yMax) - Math.log10(yMin))
                : (y - yMin) / (yMax - yMin);
        return (int) Math.round(base - f * (base - topo));
    }

    private void desenharEixos(Graphics2D g) {
        g.setFont(getFont().deriveFont(11f));
        FontMetrics fm = g.getFontMetrics();
        Color grade = new Color(225, 225, 225);

        for (double t : marcas(yMin, yMax, logY)) {
            int y = py(t);
            g.setColor(grade);
            g.setStroke(new BasicStroke(1f));
            g.drawLine(esq, y, dir, y);
            g.setColor(Color.DARK_GRAY);
            String s = formatar(t);
            g.drawString(s, esq - 8 - fm.stringWidth(s), y + 4);
        }
        for (double t : marcas(xMin, xMax, logX)) {
            int x = px(t);
            g.setColor(grade);
            g.drawLine(x, topo, x, base);
            g.setColor(Color.DARK_GRAY);
            centralizar(g, formatar(t), x, base + 18);
        }
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(esq, base, dir, base);
        g.drawLine(esq, topo, esq, base);

        g.setFont(getFont().deriveFont(12f));
        centralizar(g, rotuloX, (esq + dir) / 2, base + 42);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.rotate(-Math.PI / 2);
        centralizar(g2, rotuloY, -(topo + base) / 2, 20);
        g2.dispose();
    }

    private List<Double> marcas(double min, double max, boolean log) {
        List<Double> r = new ArrayList<Double>();
        if (log) {
            for (double d = min; d <= max * 1.0001; d *= 10) {
                r.add(d);
            }
            return r;
        }
        double faixa = max - min;
        double passoBruto = faixa / 8;
        double mag = Math.pow(10, Math.floor(Math.log10(passoBruto)));
        double[] opcoes = {1, 2, 2.5, 5, 10};
        double passo = mag;
        for (double o : opcoes) {
            if (o * mag >= passoBruto) {
                passo = o * mag;
                break;
            }
        }
        for (double v = Math.ceil(min / passo) * passo; v <= max + 1e-9; v += passo) {
            r.add(v);
        }
        return r;
    }

    static String formatar(double v) {
        double a = Math.abs(v);
        if (a >= 1_000_000 && v % 1_000_000 == 0) {
            return String.format(Locale.US, "%.0fM", v / 1_000_000);
        }
        if (a >= 1000 && v % 1000 == 0) {
            return String.format(Locale.US, "%.0fk", v / 1000);
        }
        if (a >= 100 || v == Math.rint(v)) {
            return String.format(Locale.US, "%.0f", v);
        }
        if (a >= 1) {
            return String.format(Locale.US, "%.1f", v);
        }
        return String.format(Locale.US, "%.3g", v);
    }

    private void marcador(Graphics2D g, int s, int x, int y) {
        int r = 4;
        switch (s % 4) {
            case 0:
                g.fillOval(x - r, y - r, 2 * r, 2 * r);
                break;
            case 1:
                g.fillRect(x - r, y - r, 2 * r, 2 * r);
                break;
            case 2:
                g.fillPolygon(new int[] {x, x - r - 1, x + r + 1}, new int[] {y - r - 1, y + r, y + r}, 3);
                break;
            default:
                g.drawOval(x - r, y - r, 2 * r, 2 * r);
                break;
        }
    }

    private void desenharLegenda(Graphics2D g) {
        if (series.isEmpty()) {
            return;
        }
        g.setFont(getFont().deriveFont(11f));
        FontMetrics fm = g.getFontMetrics();
        int larg = 0;
        for (Serie s : series) {
            larg = Math.max(larg, fm.stringWidth(s.nome));
        }
        int w = larg + 50;
        int h = series.size() * 18 + 10;
        int x0 = esq + 12;
        int y0 = topo + 8;
        g.setColor(new Color(255, 255, 255, 230));
        g.fillRect(x0, y0, w, h);
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x0, y0, w, h);
        for (int s = 0; s < series.size(); s++) {
            int y = y0 + 14 + s * 18;
            Color cor = CORES[s % CORES.length];
            float[] traco = TRACOS[s % TRACOS.length];
            g.setColor(cor);
            g.setStroke(traco == null ? new BasicStroke(2f)
                    : new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, traco, 0f));
            g.drawLine(x0 + 6, y - 4, x0 + 32, y - 4);
            g.setStroke(new BasicStroke(1.5f));
            marcador(g, s, x0 + 19, y - 4);
            g.setColor(Color.BLACK);
            g.drawString(series.get(s).nome, x0 + 40, y);
        }
    }

    private String textoPontoProximo(int mx, int my) {
        if (series.isEmpty() || !calcularLimites()) {
            return null;
        }
        double melhor = 15 * 15;
        String texto = null;
        for (Serie s : series) {
            for (double[] p : s.pontos) {
                if (!valido(p[0], logX) || !valido(p[1], logY)) {
                    continue;
                }
                double dx = px(p[0]) - mx;
                double dy = py(p[1]) - my;
                double d = dx * dx + dy * dy;
                if (d < melhor) {
                    melhor = d;
                    texto = String.format(Locale.US, "<html><b>%s</b><br>%s = %s<br>%s = %.3f ± %.3f</html>",
                            s.nome, rotuloX, formatar(p[0]), rotuloY, p[1], p[2]);
                }
            }
        }
        return texto;
    }

    private static void centralizar(Graphics2D g, String s, int x, int y) {
        g.drawString(s, x - g.getFontMetrics().stringWidth(s) / 2, y);
    }
}
