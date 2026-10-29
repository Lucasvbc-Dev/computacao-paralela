package br.unifor.ordenacao.dados;

/** Natureza do conjunto de dados de entrada. */
public enum Distribuicao {
    /** Valores uniformes em [0, 1.000.000). */
    ALEATORIO,
    /** Os mesmos valores de ALEATORIO, já em ordem crescente (melhor caso para vários algoritmos). */
    ORDENADO,
    /** Os mesmos valores de ALEATORIO, em ordem decrescente (pior caso do Bubble Sort). */
    INVERSO,
    /** Valores uniformes em [0, 100): muitas repetições e amplitude pequena. */
    POUCOS_DISTINTOS
}
