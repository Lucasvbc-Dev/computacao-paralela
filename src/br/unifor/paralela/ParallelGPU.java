package br.unifor.paralela;

import static org.jocl.CL.*;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_event;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;

/**
 * Versão paralela na GPU com OpenCL (biblioteca JOCL 2.0.4).
 *
 * O texto é dividido em blocos de {@link #BLOCO} posições e cada work-item verifica
 * as posições do seu bloco: se ali começa a palavra buscada (com separador antes e
 * depois), conta 1. Como todo o texto já está na memória do dispositivo, o work-item
 * pode olhar além do fim do bloco, então nenhuma palavra se perde na fronteira.
 * Cada work-item soma suas ocorrências localmente e faz no máximo um atomic_add
 * no contador global.
 *
 * A preparação do OpenCL (escolher dispositivo, criar contexto, compilar o kernel)
 * é feita uma vez no construtor e medida à parte ({@link #getTempoPreparacaoMs()}).
 * O tempo de cada contagem inclui a cópia do texto para a GPU, o kernel e a
 * leitura do resultado de volta.
 */
public final class ParallelGPU implements AutoCloseable {

    /** Quantidade de posições consecutivas que cada work-item verifica. */
    private static final int BLOCO = Integer.getInteger("gpu.bloco", 64);

    private static final String KERNEL =
            "inline int eh_palavra(uchar c) {\n"
          + "    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')\n"
          + "        || (c >= 0xC0 && c != 0xD7 && c != 0xF7);\n"
          + "}\n"
          + "__kernel void contar(__global const uchar* texto, const int n,\n"
          + "                     __global const uchar* palavra, const int m,\n"
          + "                     __global int* total, const int bloco) {\n"
          + "    int inicio = get_global_id(0) * bloco;\n"
          + "    if (inicio >= n) return;\n"
          + "    int fim = min(inicio + bloco, n - m + 1);\n"
          + "    uchar primeira = palavra[0];\n"
          + "    int contagem = 0;\n"
          + "    for (int i = inicio; i < fim; i++) {\n"
          + "        if (texto[i] != primeira) continue;              /* filtro barato: 1a letra */\n"
          + "        if (i > 0 && eh_palavra(texto[i - 1])) continue;  /* precisa começar palavra */\n"
          + "        int ok = 1;\n"
          + "        for (int k = 1; k < m; k++) {\n"
          + "            if (texto[i + k] != palavra[k]) { ok = 0; break; }\n"
          + "        }\n"
          + "        if (ok && i + m < n && eh_palavra(texto[i + m])) ok = 0; /* precisa terminar palavra */\n"
          + "        contagem += ok;\n"
          + "    }\n"
          + "    if (contagem > 0) atomic_add(total, contagem);\n"
          + "}\n";

    private final cl_context contexto;
    private final cl_command_queue fila;
    private final cl_program programa;
    private final cl_kernel kernel;
    private final String nomeDispositivo;
    private final boolean ehGpu;
    private final long tamanhoGrupo;
    private final double tempoPreparacaoMs;

    private double ultimoKernelMs;
    private double ultimaTransferenciaMs;

    public ParallelGPU() {
        long t0 = System.nanoTime();
        CL.setExceptionsEnabled(true);

        // 1) Procura uma GPU em todas as plataformas; se não houver, usa o primeiro dispositivo OpenCL.
        int[] qtd = new int[1];
        clGetPlatformIDs(0, null, qtd);
        if (qtd[0] == 0) {
            throw new IllegalStateException("Nenhuma plataforma OpenCL encontrada. Instale o driver da placa de vídeo.");
        }
        cl_platform_id[] plataformas = new cl_platform_id[qtd[0]];
        clGetPlatformIDs(plataformas.length, plataformas, null);

        cl_platform_id plataformaEscolhida = null;
        cl_device_id dispositivo = null;
        boolean gpu = false;
        for (cl_platform_id p : plataformas) {
            cl_device_id d = primeiroDispositivo(p, CL_DEVICE_TYPE_GPU);
            if (d != null) {
                plataformaEscolhida = p;
                dispositivo = d;
                gpu = true;
                break;
            }
        }
        if (dispositivo == null) {
            for (cl_platform_id p : plataformas) {
                cl_device_id d = primeiroDispositivo(p, CL_DEVICE_TYPE_ALL);
                if (d != null) {
                    plataformaEscolhida = p;
                    dispositivo = d;
                    break;
                }
            }
        }
        if (dispositivo == null) {
            throw new IllegalStateException("Nenhum dispositivo OpenCL encontrado.");
        }
        this.ehGpu = gpu;
        this.nomeDispositivo = textoInfo(dispositivo, CL_DEVICE_NAME).trim();

        // 2) Contexto, fila (com profiling para separar tempo de kernel e de cópia) e kernel.
        cl_context_properties props = new cl_context_properties();
        props.addProperty(CL_CONTEXT_PLATFORM, plataformaEscolhida);
        contexto = clCreateContext(props, 1, new cl_device_id[]{dispositivo}, null, null, null);
        @SuppressWarnings("deprecation")
        cl_command_queue q = clCreateCommandQueue(contexto, dispositivo, CL_QUEUE_PROFILING_ENABLE, null);
        fila = q;
        programa = clCreateProgramWithSource(contexto, 1, new String[]{KERNEL}, null, null);
        clBuildProgram(programa, 0, null, null, null, null);
        kernel = clCreateKernel(programa, "contar", null);

        long[] maxGrupo = new long[1];
        clGetKernelWorkGroupInfo(kernel, dispositivo, CL_KERNEL_WORK_GROUP_SIZE,
                Sizeof.size_t, Pointer.to(maxGrupo), null);
        this.tamanhoGrupo = Math.max(1, Math.min(64, maxGrupo[0]));
        this.tempoPreparacaoMs = (System.nanoTime() - t0) / 1e6;
    }

    /** Conta as ocorrências de {@code palavra} no texto (já em minúsculas, ISO-8859-1). */
    public int contar(byte[] texto, String palavra) {
        byte[] alvo = palavra.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int n = texto.length;
        int m = alvo.length;
        if (n == 0 || m > n) {
            return 0;
        }
        int[] total = new int[1];

        cl_mem bTexto = clCreateBuffer(contexto, CL_MEM_READ_ONLY, n, null, null);
        cl_mem bPalavra = clCreateBuffer(contexto, CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR,
                m, Pointer.to(alvo), null);
        cl_mem bTotal = clCreateBuffer(contexto, CL_MEM_READ_WRITE | CL_MEM_COPY_HOST_PTR,
                Sizeof.cl_int, Pointer.to(total), null);
        try {
            cl_event evCopia = new cl_event();
            clEnqueueWriteBuffer(fila, bTexto, CL_TRUE, 0, n, Pointer.to(texto), 0, null, evCopia);

            clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(bTexto));
            clSetKernelArg(kernel, 1, Sizeof.cl_int, Pointer.to(new int[]{n}));
            clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(bPalavra));
            clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{m}));
            clSetKernelArg(kernel, 4, Sizeof.cl_mem, Pointer.to(bTotal));
            clSetKernelArg(kernel, 5, Sizeof.cl_int, Pointer.to(new int[]{BLOCO}));

            // um work-item por bloco, arredondado para múltiplo do tamanho do work-group
            long workItems = (n + BLOCO - 1) / BLOCO;
            long global = ((workItems + tamanhoGrupo - 1) / tamanhoGrupo) * tamanhoGrupo;
            cl_event evKernel = new cl_event();
            clEnqueueNDRangeKernel(fila, kernel, 1, null, new long[]{global},
                    new long[]{tamanhoGrupo}, 0, null, evKernel);

            clEnqueueReadBuffer(fila, bTotal, CL_TRUE, 0, Sizeof.cl_int, Pointer.to(total), 0, null, null);

            ultimaTransferenciaMs = duracaoMs(evCopia);
            ultimoKernelMs = duracaoMs(evKernel);
            clReleaseEvent(evCopia);
            clReleaseEvent(evKernel);
            return total[0];
        } finally {
            clReleaseMemObject(bTexto);
            clReleaseMemObject(bPalavra);
            clReleaseMemObject(bTotal);
        }
    }

    public String getNomeDispositivo() {
        return nomeDispositivo + (ehGpu ? " (GPU)" : " (dispositivo OpenCL não-GPU)");
    }

    public boolean isGpu() {
        return ehGpu;
    }

    public double getTempoPreparacaoMs() {
        return tempoPreparacaoMs;
    }

    /** Tempo só do kernel na última contagem (medido pelo próprio OpenCL). */
    public double getUltimoKernelMs() {
        return ultimoKernelMs;
    }

    /** Tempo da cópia do texto para o dispositivo na última contagem. */
    public double getUltimaTransferenciaMs() {
        return ultimaTransferenciaMs;
    }

    @Override
    public void close() {
        clReleaseKernel(kernel);
        clReleaseProgram(programa);
        clReleaseCommandQueue(fila);
        clReleaseContext(contexto);
    }

    private static cl_device_id primeiroDispositivo(cl_platform_id plataforma, long tipo) {
        int[] qtd = new int[1];
        try {
            CL.setExceptionsEnabled(false);
            int r = clGetDeviceIDs(plataforma, tipo, 0, null, qtd);
            if (r != CL_SUCCESS || qtd[0] == 0) {
                return null;
            }
            cl_device_id[] ds = new cl_device_id[qtd[0]];
            clGetDeviceIDs(plataforma, tipo, ds.length, ds, null);
            return ds[0];
        } finally {
            CL.setExceptionsEnabled(true);
        }
    }

    private static String textoInfo(cl_device_id d, int param) {
        long[] tam = new long[1];
        clGetDeviceInfo(d, param, 0, null, tam);
        byte[] buf = new byte[(int) tam[0]];
        clGetDeviceInfo(d, param, buf.length, Pointer.to(buf), null);
        return new String(buf, 0, Math.max(0, buf.length - 1));
    }

    private static double duracaoMs(cl_event ev) {
        long[] ini = new long[1];
        long[] fim = new long[1];
        clGetEventProfilingInfo(ev, CL_PROFILING_COMMAND_START, Sizeof.cl_ulong, Pointer.to(ini), null);
        clGetEventProfilingInfo(ev, CL_PROFILING_COMMAND_END, Sizeof.cl_ulong, Pointer.to(fim), null);
        return (fim[0] - ini[0]) / 1e6;
    }
}
