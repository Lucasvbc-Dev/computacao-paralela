package br.unifor.ordenacao.algoritmos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Counting Sort para inteiros. Custo O(n + k), em que k = (max - min + 1) é a amplitude dos valores.
 *
 * Paralelo, em quatro fases, todas divididas entre p threads:
 * 1. mínimo e máximo de cada bloco do vetor (redução final serial de p valores);
 * 2. histograma local de cada bloco (sem disputa: cada thread tem o seu próprio vetor de contagem);
 * 3. soma dos histogramas locais, com a faixa de valores [0, k) dividida entre as threads;
 * 4. reescrita do vetor: cada thread escreve os valores da sua faixa a partir de um deslocamento
 *    calculado por soma de prefixos (p valores, serial).
 */
public class CountingSort implements AlgoritmoOrdenacao {

    @Override
    public String nome() {
        return "CountingSort";
    }

    @Override
    public void ordenarSerial(int[] v) {
        if (v.length < 2) {
            return;
        }
        int min = v[0];
        int max = v[0];
        for (int x : v) {
            if (x < min) {
                min = x;
            } else if (x > max) {
                max = x;
            }
        }
        int k = max - min + 1;
        int[] cont = new int[k];
        for (int x : v) {
            cont[x - min]++;
        }
        int pos = 0;
        for (int i = 0; i < k; i++) {
            for (int c = cont[i]; c > 0; c--) {
                v[pos++] = i + min;
            }
        }
    }

    @Override
    public void ordenarParalelo(final int[] v, int numThreads) {
        if (v.length < 2) {
            return;
        }
        final int p = Math.max(1, Math.min(numThreads, v.length));
        final int[] lim = Util.limitesBlocos(v.length, p);
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        try {
            // Fase 1: mínimo e máximo por bloco
            final int[] mins = new int[p];
            final int[] maxs = new int[p];
            List<Future<?>> tarefas = new ArrayList<Future<?>>();
            for (int t = 0; t < p; t++) {
                final int id = t;
                tarefas.add(pool.submit(new Runnable() {
                    public void run() {
                        int mn = v[lim[id]];
                        int mx = mn;
                        for (int i = lim[id] + 1; i < lim[id + 1]; i++) {
                            int x = v[i];
                            if (x < mn) {
                                mn = x;
                            } else if (x > mx) {
                                mx = x;
                            }
                        }
                        mins[id] = mn;
                        maxs[id] = mx;
                    }
                }));
            }
            Util.aguardar(tarefas);
            int mn = mins[0];
            int mx = maxs[0];
            for (int t = 1; t < p; t++) {
                mn = Math.min(mn, mins[t]);
                mx = Math.max(mx, maxs[t]);
            }
            final int min = mn;
            final int k = mx - mn + 1;

            // Fase 2: histogramas locais
            final int[][] locais = new int[p][];
            tarefas.clear();
            for (int t = 0; t < p; t++) {
                final int id = t;
                tarefas.add(pool.submit(new Runnable() {
                    public void run() {
                        int[] c = new int[k];
                        for (int i = lim[id]; i < lim[id + 1]; i++) {
                            c[v[i] - min]++;
                        }
                        locais[id] = c;
                    }
                }));
            }
            Util.aguardar(tarefas);

            // Fase 3: soma dos histogramas, faixa de valores dividida entre as threads
            final int[] faixas = Util.limitesBlocos(k, p);
            final int[] total = new int[k];
            final long[] somaFaixa = new long[p];
            tarefas.clear();
            for (int t = 0; t < p; t++) {
                final int id = t;
                tarefas.add(pool.submit(new Runnable() {
                    public void run() {
                        long s = 0;
                        for (int i = faixas[id]; i < faixas[id + 1]; i++) {
                            int c = 0;
                            for (int[] local : locais) {
                                c += local[i];
                            }
                            total[i] = c;
                            s += c;
                        }
                        somaFaixa[id] = s;
                    }
                }));
            }
            Util.aguardar(tarefas);

            // Soma de prefixos sobre as p faixas: posição inicial de escrita de cada thread
            final int[] desloc = new int[p];
            long acum = 0;
            for (int t = 0; t < p; t++) {
                desloc[t] = (int) acum;
                acum += somaFaixa[t];
            }

            // Fase 4: reescrita do vetor ordenado
            tarefas.clear();
            for (int t = 0; t < p; t++) {
                final int id = t;
                tarefas.add(pool.submit(new Runnable() {
                    public void run() {
                        int pos = desloc[id];
                        for (int i = faixas[id]; i < faixas[id + 1]; i++) {
                            int valor = i + min;
                            for (int c = total[i]; c > 0; c--) {
                                v[pos++] = valor;
                            }
                        }
                    }
                }));
            }
            Util.aguardar(tarefas);
        } finally {
            pool.shutdown();
        }
    }
}
