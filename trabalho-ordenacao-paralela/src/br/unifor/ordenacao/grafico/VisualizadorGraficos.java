package br.unifor.ordenacao.grafico;

import br.unifor.ordenacao.benchmark.Estatisticas;
import br.unifor.ordenacao.benchmark.Medicao;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Visualizador de gráficos dinâmicos (Swing).
 *
 * Lê um ou mais CSVs de resultados brutos, calcula as estatísticas em memória e exibe gráficos
 * que mudam conforme as seleções (visão, algoritmo, distribuição, núcleos, escala). Com a opção
 * "Atualizar automaticamente" ligada, o arquivo é relido a cada 2 segundos, de modo que os gráficos
 * acompanham em tempo real um benchmark em execução.
 *
 * Uso: java -cp out br.unifor.ordenacao.grafico.VisualizadorGraficos [resultados/brutos.csv ...]
 */
public class VisualizadorGraficos extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final String V_TEMPO = "Tempo médio x Tamanho da entrada";
    private static final String V_SPEEDUP = "Speedup x Número de threads";
    private static final String V_EFICIENCIA = "Eficiência x Número de threads";
    private static final String V_ALGORITMOS = "Comparação entre algoritmos (serial)";
    private static final String V_DISTRIBUICAO = "Efeito da distribuição (serial)";
    private static final String V_NUCLEOS = "Speedup por quantidade de núcleos";

    private final GraficoLinhas grafico = new GraficoLinhas();
    private final JComboBox<String> cbVisao = new JComboBox<String>(new String[] {
        V_TEMPO, V_SPEEDUP, V_EFICIENCIA, V_ALGORITMOS, V_DISTRIBUICAO, V_NUCLEOS});
    private final JComboBox<String> cbAlgoritmo = new JComboBox<String>();
    private final JComboBox<String> cbDistribuicao = new JComboBox<String>();
    private final JComboBox<String> cbNucleos = new JComboBox<String>();
    private final JComboBox<String> cbTamanho = new JComboBox<String>();
    private final JCheckBox ckLog = new JCheckBox("Escala log", true);
    private final JCheckBox ckAuto = new JCheckBox("Atualizar automaticamente", true);
    private final JLabel status = new JLabel(" ");
    private final DefaultTableModel tabela = new DefaultTableModel(new String[] {
        "Núcleos", "Algoritmo", "Distribuição", "Tamanho", "Config.", "Amostras", "Média (ms)",
        "Desvio (ms)", "IC95 (ms)", "CV (%)", "Speedup", "Eficiência"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };

    private List<File> arquivos = new ArrayList<File>();
    private long ultimaModificacao;
    private List<Estatisticas.Linha> dados = new ArrayList<Estatisticas.Linha>();
    private boolean atualizandoCombos;

    public VisualizadorGraficos(List<File> iniciais) {
        super("Ordenação serial x paralela — Gráficos dinâmicos");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel linha1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JPanel linha2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton abrir = new JButton("Abrir CSV...");
        JButton exportar = new JButton("Exportar PNG...");
        linha1.add(abrir);
        linha1.add(new JLabel("Visão:"));
        linha1.add(cbVisao);
        linha1.add(ckLog);
        linha1.add(ckAuto);
        linha1.add(exportar);
        linha2.add(new JLabel("Algoritmo:"));
        linha2.add(cbAlgoritmo);
        linha2.add(new JLabel("Distribuição:"));
        linha2.add(cbDistribuicao);
        linha2.add(new JLabel("Tamanho:"));
        linha2.add(cbTamanho);
        linha2.add(new JLabel("Núcleos:"));
        linha2.add(cbNucleos);
        JPanel topo = new JPanel(new GridLayout(2, 1));
        topo.add(linha1);
        topo.add(linha2);

        JTable jt = new JTable(tabela);
        jt.setAutoCreateRowSorter(true);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, grafico, new JScrollPane(jt));
        split.setResizeWeight(0.72);

        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        getContentPane().add(topo, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);
        getContentPane().add(status, BorderLayout.SOUTH);

        ActionListener redesenhar = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!atualizandoCombos) {
                    atualizarGrafico();
                }
            }
        };
        cbVisao.addActionListener(redesenhar);
        cbAlgoritmo.addActionListener(redesenhar);
        cbDistribuicao.addActionListener(redesenhar);
        cbNucleos.addActionListener(redesenhar);
        cbTamanho.addActionListener(redesenhar);
        ckLog.addActionListener(redesenhar);

        abrir.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                escolherArquivos();
            }
        });
        exportar.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                exportarPng();
            }
        });

        new Timer(2000, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (ckAuto.isSelected() && modificacaoAtual() != ultimaModificacao) {
                    carregar();
                }
            }
        }).start();

        setSize(1280, 860);
        setLocationRelativeTo(null);
        arquivos = iniciais;
        carregar();
    }

    private long modificacaoAtual() {
        long m = 0;
        for (File f : arquivos) {
            m += f.lastModified() + f.length();
        }
        return m;
    }

    private void escolherArquivos() {
        JFileChooser fc = new JFileChooser(new File("resultados"));
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            arquivos = new ArrayList<File>();
            for (File f : fc.getSelectedFiles()) {
                arquivos.add(f);
            }
            carregar();
        }
    }

    private void carregar() {
        ultimaModificacao = modificacaoAtual();
        List<String> caminhos = new ArrayList<String>();
        for (File f : arquivos) {
            if (f.exists()) {
                caminhos.add(f.getPath());
            }
        }
        if (caminhos.isEmpty()) {
            status.setText("Nenhum CSV carregado. Use \"Abrir CSV...\" e selecione um arquivo de resultados brutos.");
            dados = new ArrayList<Estatisticas.Linha>();
            atualizarGrafico();
            return;
        }
        try {
            List<Medicao> medicoes = Estatisticas.lerBrutos(caminhos.toArray(new String[0]));
            dados = Estatisticas.calcular(medicoes);
            status.setText(String.format(Locale.US, "%d medições carregadas de %s", medicoes.size(), caminhos));
        } catch (IOException | RuntimeException ex) {
            status.setText("Erro ao ler CSV: " + ex.getMessage());
            return;
        }
        atualizarCombos();
        atualizarGrafico();
    }

    private void atualizarCombos() {
        atualizandoCombos = true;
        try {
            TreeSet<String> algs = new TreeSet<String>();
            TreeSet<String> dists = new TreeSet<String>();
            TreeSet<Integer> nucs = new TreeSet<Integer>();
            TreeSet<Integer> tams = new TreeSet<Integer>();
            for (Estatisticas.Linha l : dados) {
                algs.add(l.algoritmo);
                dists.add(l.distribuicao);
                nucs.add(l.nucleos);
                tams.add(l.tamanho);
            }
            repor(cbAlgoritmo, algs);
            repor(cbDistribuicao, dists);
            boolean primeiraVez = cbNucleos.getItemCount() == 0;
            repor(cbNucleos, nucs);
            if (primeiraVez && !nucs.isEmpty()) {
                cbNucleos.setSelectedItem(String.valueOf(nucs.last()));
            }
            List<String> t = new ArrayList<String>();
            t.add("Maior disponível");
            for (Integer x : tams) {
                t.add(String.valueOf(x));
            }
            repor(cbTamanho, t);
        } finally {
            atualizandoCombos = false;
        }
    }

    private static <T> void repor(JComboBox<String> cb, Iterable<T> itens) {
        Object sel = cb.getSelectedItem();
        cb.removeAllItems();
        for (T i : itens) {
            cb.addItem(String.valueOf(i));
        }
        if (sel != null) {
            cb.setSelectedItem(sel);
        }
    }

    private String sel(JComboBox<String> cb) {
        Object o = cb.getSelectedItem();
        return o == null ? "" : o.toString();
    }

    private int nucleosSel() {
        try {
            return Integer.parseInt(sel(cbNucleos));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Tamanho escolhido ou, se "Maior disponível", o maior tamanho medido para o algoritmo
     * com todas as quantidades de núcleos carregadas (para que as séries sejam comparáveis).
     */
    private int tamanhoPara(String algoritmo) {
        String s = sel(cbTamanho);
        if (!s.isEmpty() && Character.isDigit(s.charAt(0))) {
            return Integer.parseInt(s);
        }
        Map<Integer, TreeSet<Integer>> porNucleos = new LinkedHashMap<Integer, TreeSet<Integer>>();
        for (Estatisticas.Linha l : dados) {
            if (l.algoritmo.equals(algoritmo)) {
                TreeSet<Integer> t = porNucleos.get(l.nucleos);
                if (t == null) {
                    t = new TreeSet<Integer>();
                    porNucleos.put(l.nucleos, t);
                }
                t.add(l.tamanho);
            }
        }
        TreeSet<Integer> comuns = null;
        for (TreeSet<Integer> t : porNucleos.values()) {
            if (comuns == null) {
                comuns = new TreeSet<Integer>(t);
            } else {
                comuns.retainAll(t);
            }
        }
        return (comuns == null || comuns.isEmpty()) ? -1 : comuns.last();
    }

    private void atualizarGrafico() {
        String visao = sel(cbVisao);
        String alg = sel(cbAlgoritmo);
        String dist = sel(cbDistribuicao);
        int nuc = nucleosSel();
        boolean log = ckLog.isSelected();
        Map<String, GraficoLinhas.Serie> series = new LinkedHashMap<String, GraficoLinhas.Serie>();
        List<Estatisticas.Linha> usadas = new ArrayList<Estatisticas.Linha>();

        if (V_TEMPO.equals(visao)) {
            for (Estatisticas.Linha l : dados) {
                if (l.algoritmo.equals(alg) && l.distribuicao.equals(dist) && l.nucleos == nuc) {
                    serie(series, rotuloConfig(l)).adicionar(l.tamanho, l.media, l.ic95);
                    usadas.add(l);
                }
            }
            grafico.definir(alg + " — " + dist + " — " + nuc + " núcleo(s)", "Tamanho da entrada (n)",
                    "Tempo médio (ms)", new ArrayList<GraficoLinhas.Serie>(series.values()), log, log, false);
        } else if (V_SPEEDUP.equals(visao) || V_EFICIENCIA.equals(visao)) {
            boolean sp = V_SPEEDUP.equals(visao);
            for (Estatisticas.Linha l : dados) {
                if (l.algoritmo.equals(alg) && l.distribuicao.equals(dist) && l.nucleos == nuc
                        && "PARALELO".equals(l.versao)) {
                    serie(series, "n = " + GraficoLinhas.formatar(l.tamanho))
                            .adicionar(l.threads, sp ? l.speedup : l.eficiencia, 0);
                    usadas.add(l);
                }
            }
            grafico.definir(alg + " — " + dist + " — " + nuc + " núcleo(s)", "Número de threads",
                    sp ? "Speedup (T serial / T paralelo)" : "Eficiência (speedup / threads)",
                    new ArrayList<GraficoLinhas.Serie>(series.values()), false, false, sp);
        } else if (V_ALGORITMOS.equals(visao)) {
            for (Estatisticas.Linha l : dados) {
                if (l.distribuicao.equals(dist) && l.nucleos == nuc && "SERIAL".equals(l.versao)) {
                    serie(series, l.algoritmo).adicionar(l.tamanho, l.media, l.ic95);
                    usadas.add(l);
                }
            }
            grafico.definir("Versões seriais — " + dist, "Tamanho da entrada (n)", "Tempo médio (ms)",
                    new ArrayList<GraficoLinhas.Serie>(series.values()), log, log, false);
        } else if (V_DISTRIBUICAO.equals(visao)) {
            for (Estatisticas.Linha l : dados) {
                if (l.algoritmo.equals(alg) && l.nucleos == nuc && "SERIAL".equals(l.versao)) {
                    serie(series, l.distribuicao).adicionar(l.tamanho, l.media, l.ic95);
                    usadas.add(l);
                }
            }
            grafico.definir(alg + " serial — efeito da distribuição", "Tamanho da entrada (n)", "Tempo médio (ms)",
                    new ArrayList<GraficoLinhas.Serie>(series.values()), log, log, false);
        } else {
            int tam = tamanhoPara(alg);
            for (Estatisticas.Linha l : dados) {
                if (l.algoritmo.equals(alg) && l.distribuicao.equals(dist) && "PARALELO".equals(l.versao)
                        && l.tamanho == tam) {
                    serie(series, l.nucleos + " núcleo(s)").adicionar(l.threads, l.speedup, 0);
                    usadas.add(l);
                }
            }
            grafico.definir(alg + " — " + dist + " — n = " + GraficoLinhas.formatar(tam)
                    + " — speedup por núcleos disponíveis", "Número de threads",
                    "Speedup (T serial / T paralelo)", new ArrayList<GraficoLinhas.Serie>(series.values()),
                    false, false, true);
        }
        preencherTabela(usadas);
    }

    private static String rotuloConfig(Estatisticas.Linha l) {
        return "SERIAL".equals(l.versao) ? "Serial" : "Paralelo " + l.threads + " thread(s)";
    }

    private static GraficoLinhas.Serie serie(Map<String, GraficoLinhas.Serie> m, String nome) {
        GraficoLinhas.Serie s = m.get(nome);
        if (s == null) {
            s = new GraficoLinhas.Serie(nome);
            m.put(nome, s);
        }
        return s;
    }

    private void preencherTabela(List<Estatisticas.Linha> linhas) {
        tabela.setRowCount(0);
        for (Estatisticas.Linha l : linhas) {
            tabela.addRow(new Object[] {
                l.nucleos, l.algoritmo, l.distribuicao, l.tamanho, l.configuracao(), l.amostras,
                String.format(Locale.US, "%.3f", l.media), String.format(Locale.US, "%.3f", l.desvio),
                String.format(Locale.US, "%.3f", l.ic95), String.format(Locale.US, "%.1f", l.cv),
                String.format(Locale.US, "%.2f", l.speedup), String.format(Locale.US, "%.2f", l.eficiencia)
            });
        }
    }

    private void exportarPng() {
        JFileChooser fc = new JFileChooser(new File("graficos"));
        fc.setSelectedFile(new File("grafico.png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        int w = Math.max(grafico.getWidth(), 900);
        int h = Math.max(grafico.getHeight(), 520);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        grafico.desenhar(g, w, h);
        g.dispose();
        try {
            File f = fc.getSelectedFile();
            if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
                f = new File(f.getPath() + ".png");
            }
            ImageIO.write(img, "png", f);
            status.setText("Gráfico exportado para " + f.getPath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Falha ao exportar: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        final List<File> arqs = new ArrayList<File>();
        if (args.length == 0) {
            // Padrão: resultados principais + execuções do experimento de núcleos com quantidades de
            // núcleos que não aparecem nos resultados principais (para não misturar amostras).
            File principal = new File("resultados/brutos.csv");
            arqs.add(principal);
            TreeSet<Integer> presentes = new TreeSet<Integer>();
            try {
                if (principal.exists()) {
                    for (Medicao m : Estatisticas.lerBrutos(principal.getPath())) {
                        presentes.add(m.nucleos);
                    }
                }
            } catch (IOException ignorada) {
                // segue apenas com o arquivo principal
            }
            File[] lista = new File("resultados").listFiles();
            if (lista != null) {
                for (File f : lista) {
                    String nome = f.getName();
                    if (nome.matches("nucleos_\\d+_brutos\\.csv")) {
                        int n = Integer.parseInt(nome.replaceAll("\\D", ""));
                        if (!presentes.contains(n)) {
                            arqs.add(f);
                        }
                    }
                }
            }
        } else {
            for (String a : args) {
                arqs.add(new File(a));
            }
        }
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new VisualizadorGraficos(arqs).setVisible(true);
            }
        });
    }
}
