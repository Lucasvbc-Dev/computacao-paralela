package br.unifor.ordenacao.dados;

import java.util.Arrays;
import java.util.Random;

/** Gera vetores de entrada reproduzíveis a partir de uma semente. */
public final class GeradorDados {

    public static final int VALOR_MAXIMO = 1_000_000;
    public static final int VALOR_MAXIMO_POUCOS_DISTINTOS = 100;

    private GeradorDados() {
    }

    public static int[] gerar(Distribuicao d, int n, long semente) {
        Random r = new Random(semente);
        int[] v = new int[n];
        switch (d) {
            case ALEATORIO:
                preencher(v, r, VALOR_MAXIMO);
                break;
            case ORDENADO:
                preencher(v, r, VALOR_MAXIMO);
                Arrays.sort(v);
                break;
            case INVERSO:
                preencher(v, r, VALOR_MAXIMO);
                Arrays.sort(v);
                inverter(v);
                break;
            case POUCOS_DISTINTOS:
                preencher(v, r, VALOR_MAXIMO_POUCOS_DISTINTOS);
                break;
            default:
                throw new IllegalArgumentException("Distribuição desconhecida: " + d);
        }
        return v;
    }

    private static void preencher(int[] v, Random r, int limite) {
        for (int i = 0; i < v.length; i++) {
            v[i] = r.nextInt(limite);
        }
    }

    private static void inverter(int[] v) {
        for (int i = 0, j = v.length - 1; i < j; i++, j--) {
            int t = v[i];
            v[i] = v[j];
            v[j] = t;
        }
    }
}
