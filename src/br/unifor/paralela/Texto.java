package br.unifor.paralela;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Carrega e prepara um arquivo de texto para a contagem.
 *
 * Regras de contagem (iguais nos três métodos):
 * - a busca não diferencia maiúsculas de minúsculas;
 * - conta-se a palavra inteira: "the" não conta dentro de "there" ou "other";
 * - caractere de palavra = letra ou dígito do Latin-1 (a-z, 0-9, À-ÿ);
 *   todo o resto (espaço, pontuação, apóstrofo, quebra de linha) é separador.
 *
 * A leitura do disco e a conversão para minúsculas são feitas uma única vez,
 * fora da medição de tempo, para que os três métodos meçam apenas a contagem.
 */
public final class Texto {

    public final String nome;
    public final long tamanhoBytes;
    /** Texto em minúsculas, usado pelas versões em CPU. */
    public final char[] chars;
    /** Mesmo texto em ISO-8859-1 (1 byte por caractere), enviado para a GPU. */
    public final byte[] bytes;

    private Texto(String nome, long tamanhoBytes, String conteudoMinusculo) {
        this.nome = nome;
        this.tamanhoBytes = tamanhoBytes;
        this.chars = conteudoMinusculo.toCharArray();
        // Caracteres fora do Latin-1 viram '?', que é separador nas duas representações.
        this.bytes = conteudoMinusculo.getBytes(StandardCharsets.ISO_8859_1);
    }

    public static Texto carregar(Path arquivo) throws IOException {
        String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        return new Texto(arquivo.getFileName().toString(), Files.size(arquivo),
                conteudo.toLowerCase(Locale.ROOT));
    }

    /** Normaliza e valida a palavra buscada. */
    public static String normalizarPalavra(String palavra) {
        String p = palavra.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty()) {
            throw new IllegalArgumentException("A palavra buscada não pode ser vazia.");
        }
        for (int i = 0; i < p.length(); i++) {
            if (!ehCaractereDePalavra(p.charAt(i))) {
                throw new IllegalArgumentException(
                        "A palavra deve conter apenas letras ou dígitos: \"" + palavra + "\"");
            }
        }
        return p;
    }

    /** Mesma regra usada no kernel OpenCL (função eh_palavra). */
    public static boolean ehCaractereDePalavra(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || (c >= 0xC0 && c <= 0xFF && c != 0xD7 && c != 0xF7);
    }

    public double tamanhoMB() {
        return tamanhoBytes / (1024.0 * 1024.0);
    }
}
