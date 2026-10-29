package br.unifor.ordenacao.algoritmos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Bubble Sort.
 *
 * Serial: versão clássica com parada antecipada (se uma passada não faz trocas, o vetor já está ordenado).
 *
 * Paralelo: particionamento em blocos. O vetor é dividido em p blocos contíguos; cada thread ordena
 * o seu bloco com Bubble Sort; em seguida os blocos são intercalados em árvore (log2 p níveis),
 * com os pares de cada nível intercalados em paralelo. Como o custo do Bubble Sort é O(m^2),
 * ordenar p blocos de tamanho n/p custa p * (n/p)^2 = n^2/p, o que reduz o trabalho total
 * mesmo com uma única thread física disponível.
 */
public class BubbleSort implements AlgoritmoOrdenacao {

    @Override
    public String nome() {
        return "BubbleSort";
    }

    @Override
    public void ordenarSerial(int[] v) {
        bubble(v, 0, v.length);
    }

    /** Bubble Sort no intervalo [ini, fim). */
    static void bubble(int[] v, int ini, int fim) {
        for (int limite = fim - 1; limite > ini; limite--) {
            boolean trocou = false;
            for (int i = ini; i < limite; i++) {
                if (v[i] > v[i + 1]) {
                    int t = v[i];
                    v[i] = v[i + 1];
                    v[i + 1] = t;
                    trocou = true;
                }
            }
            if (!trocou) {
                return;
            }
        }
    }

    @Override
    public void ordenarParalelo(final int[] v, int numThreads) {
        if (v.length < 2) {
            return;
        }
        int blocos = Math.max(1, Math.min(numThreads, v.length));
        final int[] lim = Util.limitesBlocos(v.length, blocos);
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        try {
            List<Future<?>> tarefas = new ArrayList<Future<?>>();
            for (int b = 0; b < blocos; b++) {
                final int ini = lim[b];
                final int fim = lim[b + 1];
                tarefas.add(pool.submit(new Runnable() {
                    public void run() {
                        bubble(v, ini, fim);
                    }
                }));
            }
            Util.aguardar(tarefas);
            Util.intercalarBlocos(v, lim, pool);
        } finally {
            pool.shutdown();
        }
    }
}
