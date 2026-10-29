package br.unifor.ordenacao.algoritmos;

/**
 * Contrato comum a todos os algoritmos do estudo.
 * Cada algoritmo oferece uma versão serial e uma versão paralela,
 * ambas ordenando o vetor in-place em ordem crescente.
 */
public interface AlgoritmoOrdenacao {

    /** Nome usado nos arquivos CSV e nos gráficos. */
    String nome();

    /** Ordena o vetor usando uma única thread. */
    void ordenarSerial(int[] v);

    /**
     * Ordena o vetor usando até {@code numThreads} threads de trabalho.
     * A criação e o encerramento do pool de threads fazem parte do custo medido.
     */
    void ordenarParalelo(int[] v, int numThreads);
}
