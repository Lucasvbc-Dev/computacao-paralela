package br.unifor.ordenacao.benchmark;

import java.util.Locale;

/** Uma execução cronometrada: corresponde a uma linha do CSV de resultados brutos. */
public class Medicao {

    public static final String CABECALHO =
            "algoritmo,versao,threads,nucleos,tamanho,distribuicao,amostra,tempo_ms,ordenado_ok";

    public final String algoritmo;
    public final String versao;
    public final int threads;
    public final int nucleos;
    public final int tamanho;
    public final String distribuicao;
    public final int amostra;
    public final double tempoMs;
    public final boolean ordenadoOk;

    public Medicao(String algoritmo, String versao, int threads, int nucleos, int tamanho,
                   String distribuicao, int amostra, double tempoMs, boolean ordenadoOk) {
        this.algoritmo = algoritmo;
        this.versao = versao;
        this.threads = threads;
        this.nucleos = nucleos;
        this.tamanho = tamanho;
        this.distribuicao = distribuicao;
        this.amostra = amostra;
        this.tempoMs = tempoMs;
        this.ordenadoOk = ordenadoOk;
    }

    /** Identificador da configuração: SERIAL ou P1, P2, P4, P8... */
    public String configuracao() {
        return "SERIAL".equals(versao) ? "SERIAL" : "P" + threads;
    }

    public String paraCsv() {
        return String.format(Locale.US, "%s,%s,%d,%d,%d,%s,%d,%.4f,%s",
                algoritmo, versao, threads, nucleos, tamanho, distribuicao, amostra, tempoMs, ordenadoOk);
    }

    public static Medicao deCsv(String linha) {
        String[] c = linha.split(",");
        return new Medicao(c[0], c[1], Integer.parseInt(c[2]), Integer.parseInt(c[3]),
                Integer.parseInt(c[4]), c[5], Integer.parseInt(c[6]),
                Double.parseDouble(c[7]), Boolean.parseBoolean(c[8]));
    }
}
