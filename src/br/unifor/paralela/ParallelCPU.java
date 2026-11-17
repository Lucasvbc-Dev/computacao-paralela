package br.unifor.paralela;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Versão paralela na CPU: um pool de threads divide o texto em partes e cada
 * thread conta as ocorrências na sua parte; no fim as contagens parciais são somadas.
 *
 * Para nenhuma palavra ser cortada ao meio, cada fronteira entre partes é
 * empurrada para frente até cair num separador. Assim cada palavra pertence a
 * exatamente uma parte e o resultado é idêntico ao da versão serial.
 */
public final class ParallelCPU {

    private ParallelCPU() {
    }

    public static int contar(char[] texto, String palavra) {
        return contar(texto, palavra, Runtime.getRuntime().availableProcessors());
    }

    public static int contar(char[] texto, String palavra, int numThreads) {
        final char[] alvo = palavra.toCharArray();
        final int n = texto.length;
        final int partes = Math.max(1, Math.min(numThreads, n));

        int[] limites = new int[partes + 1];
        limites[0] = 0;
        limites[partes] = n;
        for (int p = 1; p < partes; p++) {
            int corte = (int) ((long) n * p / partes);
            corte = Math.max(corte, limites[p - 1]);
            while (corte < n && Texto.ehCaractereDePalavra(texto[corte])) {
                corte++;
            }
            limites[p] = corte;
        }

        ExecutorService pool = Executors.newFixedThreadPool(partes);
        try {
            List<Future<Integer>> parciais = new ArrayList<>(partes);
            for (int p = 0; p < partes; p++) {
                final int ini = limites[p];
                final int fim = limites[p + 1];
                parciais.add(pool.submit(() -> SerialCPU.contarIntervalo(texto, ini, fim, alvo)));
            }
            int total = 0;
            for (Future<Integer> f : parciais) {
                total += f.get();
            }
            return total;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Contagem interrompida", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Falha em uma das threads", e.getCause());
        } finally {
            pool.shutdown();
        }
    }
}
