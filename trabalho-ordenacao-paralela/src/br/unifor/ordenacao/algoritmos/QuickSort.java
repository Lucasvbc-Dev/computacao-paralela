package br.unifor.ordenacao.algoritmos;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Quick Sort com pivô pela mediana de três e particionamento de Hoare. A mediana de três evita o
 * pior caso O(n^2) em vetores ordenados/invertidos, e o esquema de Hoare (que para nos elementos
 * iguais ao pivô) mantém as partições equilibradas em vetores com muitos valores repetidos.
 * Subvetores com até {@value #CORTE_INSERCAO} elementos são finalizados com Insertion Sort.
 *
 * Paralelo: Fork/Join. Após particionar, as duas metades viram tarefas independentes
 * (não há dados compartilhados entre elas). Abaixo de {@value #LIMIAR_PARALELO} elementos,
 * a tarefa resolve o restante de forma serial para evitar sobrecarga de criação de tarefas.
 */
public class QuickSort implements AlgoritmoOrdenacao {

    static final int CORTE_INSERCAO = 32;
    static final int LIMIAR_PARALELO = 8192;

    @Override
    public String nome() {
        return "QuickSort";
    }

    @Override
    public void ordenarSerial(int[] v) {
        if (v.length > 1) {
            quick(v, 0, v.length - 1);
        }
    }

    /** Ordena o intervalo fechado [lo, hi]. Recursão no lado menor garante pilha O(log n). */
    static void quick(int[] v, int lo, int hi) {
        while (hi - lo > CORTE_INSERCAO) {
            int j = particionar(v, lo, hi);
            if (j - lo < hi - j) {
                quick(v, lo, j);
                lo = j + 1;
            } else {
                quick(v, j + 1, hi);
                hi = j;
            }
        }
        Util.insercao(v, lo, hi);
    }

    /**
     * Particionamento de Hoare. Ao final, todo elemento de v[lo..j] é menor ou igual a todo
     * elemento de v[j+1..hi]. Retorna j.
     */
    static int particionar(int[] v, int lo, int hi) {
        int pivo = medianaDeTres(v[lo], v[lo + (hi - lo) / 2], v[hi]);
        int i = lo - 1;
        int j = hi + 1;
        while (true) {
            do {
                i++;
            } while (v[i] < pivo);
            do {
                j--;
            } while (v[j] > pivo);
            if (i >= j) {
                return j;
            }
            Util.trocar(v, i, j);
        }
    }

    static int medianaDeTres(int a, int b, int c) {
        if (a < b) {
            if (b < c) {
                return b;
            }
            return (a < c) ? c : a;
        }
        if (a < c) {
            return a;
        }
        return (b < c) ? c : b;
    }

    @Override
    public void ordenarParalelo(int[] v, int numThreads) {
        if (v.length < 2) {
            return;
        }
        ForkJoinPool pool = new ForkJoinPool(numThreads);
        try {
            pool.invoke(new TarefaQuick(v, 0, v.length - 1));
        } finally {
            pool.shutdown();
        }
    }

    private static final class TarefaQuick extends RecursiveAction {
        private static final long serialVersionUID = 1L;
        private final int[] v;
        private final int lo;
        private final int hi;

        TarefaQuick(int[] v, int lo, int hi) {
            this.v = v;
            this.lo = lo;
            this.hi = hi;
        }

        @Override
        protected void compute() {
            if (hi - lo < LIMIAR_PARALELO) {
                if (hi > lo) {
                    quick(v, lo, hi);
                }
                return;
            }
            int j = particionar(v, lo, hi);
            invokeAll(new TarefaQuick(v, lo, j), new TarefaQuick(v, j + 1, hi));
        }
    }
}
