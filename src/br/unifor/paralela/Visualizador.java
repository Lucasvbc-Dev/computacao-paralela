package br.unifor.paralela;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Interface Swing (item EXTRA): executa a contagem num arquivo escolhido e mostra
 * os gráficos gerados pelo benchmark, um por aba.
 */
public final class Visualizador {

    private Visualizador() {
    }

    public static void abrir() {
        SwingUtilities.invokeLater(Visualizador::montar);
    }

    private static void montar() {
        JFrame janela = new JFrame("Contagem de palavras — Serial x Paralelo (CPU/GPU)");
        janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JTabbedPane abas = new JTabbedPane();
        abas.addTab("Executar", painelExecucao());

        Path pasta = Path.of("resultados", "graficos");
        if (Files.isDirectory(pasta)) {
            try (Stream<Path> s = Files.list(pasta)) {
                List<Path> pngs = s.filter(p -> p.toString().endsWith(".png")).sorted().collect(Collectors.toList());
                for (Path png : pngs) {
                    Image img = ImageIO.read(png.toFile());
                    abas.addTab(png.getFileName().toString().replace(".png", ""),
                            new JScrollPane(new JLabel(new ImageIcon(img))));
                }
            } catch (Exception e) {
                abas.addTab("Erro", new JLabel("Não foi possível ler os gráficos: " + e.getMessage()));
            }
        }
        janela.add(abas);
        janela.setSize(1180, 760);
        janela.setLocationRelativeTo(null);
        janela.setVisible(true);
    }

    private static JPanel painelExecucao() {
        JPanel painel = new JPanel(new BorderLayout(8, 8));
        JPanel topo = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField campoArquivo = new JTextField("amostras/MobyDick-217452.txt", 34);
        JButton escolher = new JButton("Escolher...");
        JTextField campoPalavra = new JTextField("whale", 10);
        JSpinner campoThreads = new JSpinner(new SpinnerNumberModel(
                Runtime.getRuntime().availableProcessors(), 1, 256, 1));
        JButton executar = new JButton("Contar");
        topo.add(new JLabel("Arquivo:"));
        topo.add(campoArquivo);
        topo.add(escolher);
        topo.add(new JLabel("Palavra:"));
        topo.add(campoPalavra);
        topo.add(new JLabel("Threads:"));
        topo.add(campoThreads);
        topo.add(executar);

        JTextArea saida = new JTextArea();
        saida.setEditable(false);
        saida.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        painel.add(topo, BorderLayout.NORTH);
        painel.add(new JScrollPane(saida), BorderLayout.CENTER);

        escolher.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(new File("amostras"));
            if (fc.showOpenDialog(painel) == JFileChooser.APPROVE_OPTION) {
                campoArquivo.setText(fc.getSelectedFile().getPath());
            }
        });

        executar.addActionListener(e -> {
            executar.setEnabled(false);
            saida.setText("Executando...\n");
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    String palavra = Texto.normalizarPalavra(campoPalavra.getText());
                    int threads = (Integer) campoThreads.getValue();
                    Texto texto = Texto.carregar(Path.of(campoArquivo.getText()));
                    StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                            "%s (%.2f MB) — palavra \"%s\"%n%n", texto.nome, texto.tamanhoMB(), palavra));
                    long t0 = System.nanoTime();
                    int c = SerialCPU.contar(texto.chars, palavra);
                    sb.append(String.format("SerialCPU: %d ocorrências em %d ms%n", c, (System.nanoTime() - t0) / 1_000_000));
                    t0 = System.nanoTime();
                    c = ParallelCPU.contar(texto.chars, palavra, threads);
                    sb.append(String.format("ParallelCPU: %d ocorrências em %d ms (%d threads)%n", c,
                            (System.nanoTime() - t0) / 1_000_000, threads));
                    try (ParallelGPU gpu = new ParallelGPU()) {
                        t0 = System.nanoTime();
                        c = gpu.contar(texto.bytes, palavra);
                        sb.append(String.format("ParallelGPU: %d ocorrências em %d ms%n", c, (System.nanoTime() - t0) / 1_000_000));
                        sb.append("\nDispositivo OpenCL: ").append(gpu.getNomeDispositivo());
                    }
                    return sb.toString();
                }

                @Override
                protected void done() {
                    try {
                        saida.setText(get());
                    } catch (Exception ex) {
                        saida.setText("Erro: " + (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                    }
                    executar.setEnabled(true);
                }
            }.execute();
        });
        return painel;
    }
}
