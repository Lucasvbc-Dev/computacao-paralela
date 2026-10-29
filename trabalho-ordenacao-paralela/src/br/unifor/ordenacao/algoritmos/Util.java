package br.unifor.ordenacao.algoritmos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Rotinas auxiliares compartilhadas pelos algoritmos. */
final class Util {

    private Util() {
    }

    /** Insertion Sort no intervalo fechado [lo, hi]. Usado para subvetores pequenos. */
    static void insercao(int[] v, int lo, int hi) {
        for (int i = lo + 1; i <= hi; i++) {
            int chave = v[i];
            int j = i - 1;
            while (j >= lo && v[j] > chave) {
                v[j + 1] = v[j];
                j--;
            }
            v[j + 1] = chave;
        }
    }

    static void trocar(int[] v, int i, int j) {
        int t = v[i];
        v[i] = v[j];
        v[j] = t;
    }

    /**
     * Divide [0, n) em {@code partes} blocos contíguos de tamanho quase igual.
     * Retorna um vetor de limites com partes + 1 posições: o bloco k é [lim[k], lim[k+1]).
     */
    static int[] limitesBlocos(int n, int partes) {
        int[] lim = new int[partes + 1];
        for (int k = 0; k <= partes; k++) {
            lim[k] = (int) ((long) n * k / partes);
        }
        return lim;
    }

    /** Intercala os trechos ordenados v[ini, meio) e v[meio, fim) usando aux no mesmo intervalo. */
    static void intercalar(int[] v, int[] aux, int ini, int meio, int fim) {
        System.arraycopy(v, ini, aux, ini, fim - ini);
        int i = ini;
        int j = meio;
        int k = ini;
        while (i < meio && j < fim) {
            v[k++] = (aux[j] < aux[i]) ? aux[j++] : aux[i++];
        }
        while (i < meio) {
            v[k++] = aux[i++];
        }
        while (j < fim) {
            v[k++] = aux[j++];
        }
    }

    /**
     * Intercala, em árvore, blocos já ordenados definidos por {@code lim}.
     * A cada nível, os pares de blocos vizinhos são intercalados em paralelo no pool.
     */
    static void intercalarBlocos(int[] v, int[] lim, ExecutorService pool) {
        int[] aux = new int[v.length];
        int[] limites = lim;
        while (limites.length > 2) {
            int blocos = limites.length - 1;
            List<Future<?>> tarefas = new ArrayList<Future<?>>();
            int novosBlocos = (blocos + 1) / 2;
            int[] novos = new int[novosBlocos + 1];
            for (int b = 0, nb = 0; b < blocos; b += 2, nb++) {
                novos[nb] = limites[b];
                if (b + 1 < blocos) {
                    final int ini = limites[b];
                    final int meio = limites[b + 1];
                    final int fim = limites[b + 2];
                    final int[] auxF = aux;
                    tarefas.add(pool.submit(new Runnable() {
                        public void run() {
                            intercalar(v, auxF, ini, meio, fim);
                        }
                    }));
                }
            }
            novos[novosBlocos] = limites[blocos];
            aguardar(tarefas);
            limites = novos;
        }
    }

    /** Aguarda todas as tarefas e propaga qualquer exceção ocorrida nas threads. */
    static void aguardar(List<Future<?>> tarefas) {
        for (Future<?> f : tarefas) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Execução interrompida", e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Falha em tarefa paralela", e.getCause());
            }
        }
    }
}
