package br.unifor.ordenacao.algoritmos;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Merge Sort top-down com vetor auxiliar alocado uma única vez.
 * Subvetores com até {@value #CORTE_INSERCAO} elementos são ordenados com Insertion Sort, e a
 * intercalação é pulada quando as duas metades já estão em ordem (v[meio-1] <= v[meio]).
 *
 * Paralelo: Fork/Join. As duas metades são ordenadas como tarefas independentes e depois
 * intercaladas pela tarefa pai. A intercalação de cada nível é serial, então a última
 * intercalação (n elementos) limita o speedup máximo (Lei de Amdahl).
 */
public class MergeSort implements AlgoritmoOrdenacao {

    static final int CORTE_INSERCAO = 32;
    static final int LIMIAR_PARALELO = 8192;

    @Override
    public String nome() {
        return "MergeSort";
    }

    @Override
    public void ordenarSerial(int[] v) {
        int[] aux = new int[v.length];
        merge(v, aux, 0, v.length);
    }

    /** Ordena o intervalo [ini, fim). */
    static void merge(int[] v, int[] aux, int ini, int fim) {
        if (fim - ini <= CORTE_INSERCAO) {
            Util.insercao(v, ini, fim - 1);
            return;
        }
        int meio = (ini + fim) >>> 1;
        merge(v, aux, ini, meio);
        merge(v, aux, meio, fim);
        if (v[meio - 1] <= v[meio]) {
            return;
        }
        Util.intercalar(v, aux, ini, meio, fim);
    }

    @Override
    public void ordenarParalelo(int[] v, int numThreads) {
        if (v.length < 2) {
            return;
        }
        int[] aux = new int[v.length];
        ForkJoinPool pool = new ForkJoinPool(numThreads);
        try {
            pool.invoke(new TarefaMerge(v, aux, 0, v.length));
        } finally {
            pool.shutdown();
        }
    }

    private static final class TarefaMerge extends RecursiveAction {
        private static final long serialVersionUID = 1L;
        private final int[] v;
        private final int[] aux;
        private final int ini;
        private final int fim;

        TarefaMerge(int[] v, int[] aux, int ini, int fim) {
            this.v = v;
            this.aux = aux;
            this.ini = ini;
            this.fim = fim;
        }

        @Override
        protected void compute() {
            if (fim - ini <= LIMIAR_PARALELO) {
                merge(v, aux, ini, fim);
                return;
            }
            int meio = (ini + fim) >>> 1;
            invokeAll(new TarefaMerge(v, aux, ini, meio), new TarefaMerge(v, aux, meio, fim));
            if (v[meio - 1] > v[meio]) {
                Util.intercalar(v, aux, ini, meio, fim);
            }
        }
    }
}
