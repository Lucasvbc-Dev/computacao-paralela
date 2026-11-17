package br.unifor.paralela;

/**
 * Versão serial na CPU: um loop simples percorre o texto palavra por palavra
 * e compara cada uma com a palavra buscada.
 */
public final class SerialCPU {

    private SerialCPU() {
    }

    public static int contar(char[] texto, String palavra) {
        return contarIntervalo(texto, 0, texto.length, palavra.toCharArray());
    }

    /**
     * Percorre as palavras que começam em [inicio, fim) e conta as iguais a {@code palavra}.
     * Também é usado por cada thread da versão paralela, que recebe um pedaço do texto.
     */
    static int contarIntervalo(char[] texto, int inicio, int fim, char[] palavra) {
        int contagem = 0;
        int i = inicio;
        final int m = palavra.length;
        while (i < fim) {
            // pula separadores
            while (i < fim && !Texto.ehCaractereDePalavra(texto[i])) {
                i++;
            }
            if (i >= fim) {
                break;
            }
            // encontra o fim da palavra atual (pode passar de 'fim', a palavra é deste pedaço)
            int inicioPalavra = i;
            while (i < texto.length && Texto.ehCaractereDePalavra(texto[i])) {
                i++;
            }
            if (i - inicioPalavra == m && iguais(texto, inicioPalavra, palavra)) {
                contagem++;
            }
        }
        return contagem;
    }

    private static boolean iguais(char[] texto, int pos, char[] palavra) {
        for (int k = 0; k < palavra.length; k++) {
            if (texto[pos + k] != palavra[k]) {
                return false;
            }
        }
        return true;
    }
}
