"""
Gera os gráficos estáticos (PNG) usados no relatório a partir dos CSVs produzidos pelo benchmark Java.

Uso (na raiz do projeto):  python3 analise/gerar_graficos.py
Requer: matplotlib e pandas.
"""
import os

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import pandas as pd  # noqa: E402

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(RAIZ, "resultados")
SAIDA = os.path.join(RAIZ, "graficos")
os.makedirs(SAIDA, exist_ok=True)

ALGORITMOS = ["BubbleSort", "QuickSort", "MergeSort", "CountingSort"]
DISTRIBUICOES = ["ALEATORIO", "ORDENADO", "INVERSO", "POUCOS_DISTINTOS"]
NOME_DIST = {"ALEATORIO": "Aleatório", "ORDENADO": "Ordenado", "INVERSO": "Inverso",
             "POUCOS_DISTINTOS": "Poucos distintos"}
CONFIGS = ["SERIAL", "P1", "P2", "P4", "P8"]
NOME_CONFIG = {"SERIAL": "Serial", "P1": "Paralelo 1 thread", "P2": "Paralelo 2 threads",
               "P4": "Paralelo 4 threads", "P8": "Paralelo 8 threads"}

# Estilo sóbrio, em tons de cinza, adequado a impressão
ESTILOS = [
    dict(color="black", linestyle="-", marker="o"),
    dict(color="0.35", linestyle="--", marker="s"),
    dict(color="0.2", linestyle=":", marker="^"),
    dict(color="0.5", linestyle="-.", marker="D"),
    dict(color="0.65", linestyle="-", marker="v"),
]
plt.rcParams.update({"font.size": 9, "axes.grid": True, "grid.color": "0.88", "grid.linewidth": 0.6,
                     "axes.spines.top": False, "axes.spines.right": False, "savefig.dpi": 200,
                     "legend.frameon": False})


def config(df):
    return df.apply(lambda r: "SERIAL" if r["versao"] == "SERIAL" else f"P{int(r['threads'])}", axis=1)


resumo = pd.read_csv(os.path.join(RES, "resumo.csv"))
resumo["config"] = config(resumo)
brutos = pd.read_csv(os.path.join(RES, "brutos.csv"))
brutos["config"] = config(brutos)


def maior_tamanho(alg):
    return resumo[resumo.algoritmo == alg].tamanho.max()


def salvar(fig, nome):
    fig.tight_layout()
    fig.savefig(os.path.join(SAIDA, nome), bbox_inches="tight")
    plt.close(fig)
    print("gerado:", nome)


# Figura 1: versões seriais dos quatro algoritmos, entrada aleatória
fig, ax = plt.subplots(figsize=(6.4, 4.2))
for i, alg in enumerate(ALGORITMOS):
    d = resumo[(resumo.algoritmo == alg) & (resumo.distribuicao == "ALEATORIO") & (resumo.config == "SERIAL")]
    ax.errorbar(d.tamanho, d.media_ms, yerr=d.ic95_ms, label=alg, capsize=2, markersize=4, **ESTILOS[i])
ax.set_xscale("log")
ax.set_yscale("log")
ax.set_xlabel("Tamanho da entrada (n)")
ax.set_ylabel("Tempo médio (ms)")
ax.set_title("Versões seriais — entrada aleatória")
ax.legend()
salvar(fig, "fig1_serial_algoritmos.png")

# Figura 2: tempo x tamanho por configuração, um painel por algoritmo (entrada aleatória)
fig, eixos = plt.subplots(2, 2, figsize=(8.6, 6.6))
for ax, alg in zip(eixos.flat, ALGORITMOS):
    for i, c in enumerate(CONFIGS):
        d = resumo[(resumo.algoritmo == alg) & (resumo.distribuicao == "ALEATORIO") & (resumo.config == c)]
        ax.errorbar(d.tamanho, d.media_ms, yerr=d.ic95_ms, label=NOME_CONFIG[c], capsize=2, markersize=3.5,
                    **ESTILOS[i])
    ax.set_xscale("log")
    ax.set_yscale("log")
    ax.set_title(alg)
    ax.set_xlabel("Tamanho da entrada (n)")
    ax.set_ylabel("Tempo médio (ms)")
eixos[0, 0].legend(fontsize=7.5)
salvar(fig, "fig2_tempo_por_configuracao.png")

# Figura 3: speedup x threads no maior tamanho, uma linha por distribuição
fig, eixos = plt.subplots(2, 2, figsize=(8.6, 6.6))
for ax, alg in zip(eixos.flat, ALGORITMOS):
    n = maior_tamanho(alg)
    for i, dist in enumerate(DISTRIBUICOES):
        d = resumo[(resumo.algoritmo == alg) & (resumo.distribuicao == dist) & (resumo.versao == "PARALELO")
                   & (resumo.tamanho == n)].sort_values("threads")
        ax.plot(d.threads, d.speedup, label=NOME_DIST[dist], markersize=4, **ESTILOS[i])
    ax.axhline(1.0, color="0.6", linewidth=0.8)
    ax.set_xscale("log", base=2)
    ax.set_xticks([1, 2, 4, 8])
    ax.set_xticklabels(["1", "2", "4", "8"])
    ax.set_title(f"{alg} (n = {n:,})".replace(",", "."))
    ax.set_xlabel("Número de threads")
    ax.set_ylabel("Speedup")
eixos[0, 0].legend(fontsize=7.5)
salvar(fig, "fig3_speedup_por_distribuicao.png")

# Figura 4: speedup x tamanho (2 threads), entrada aleatória
fig, ax = plt.subplots(figsize=(6.4, 4.2))
for i, alg in enumerate(ALGORITMOS):
    d = resumo[(resumo.algoritmo == alg) & (resumo.distribuicao == "ALEATORIO") & (resumo.config == "P2")]
    ax.plot(d.tamanho, d.speedup, label=alg, markersize=4, **ESTILOS[i])
ax.axhline(1.0, color="0.6", linewidth=0.8)
ax.axhline(2.0, color="0.6", linewidth=0.8, linestyle="--")
ax.set_xscale("log")
ax.set_yscale("log", base=2)
ax.set_xlabel("Tamanho da entrada (n)")
ax.set_ylabel("Speedup com 2 threads (escala log2)")
ax.set_title("Speedup em função do tamanho — entrada aleatória, 2 threads")
ax.legend()
salvar(fig, "fig4_speedup_por_tamanho.png")

# Figura 5: efeito da distribuição no tempo serial (maior tamanho de cada algoritmo)
fig, eixos = plt.subplots(2, 2, figsize=(8.6, 6.0))
for ax, alg in zip(eixos.flat, ALGORITMOS):
    n = maior_tamanho(alg)
    d = resumo[(resumo.algoritmo == alg) & (resumo.tamanho == n) & (resumo.config == "SERIAL")]
    d = d.set_index("distribuicao").reindex(DISTRIBUICOES)
    ax.bar([NOME_DIST[x] for x in DISTRIBUICOES], d.media_ms, yerr=d.ic95_ms, color="0.55",
           edgecolor="black", linewidth=0.6, capsize=3)
    ax.set_title(f"{alg} serial (n = {n:,})".replace(",", "."))
    ax.set_ylabel("Tempo médio (ms)")
    ax.tick_params(axis="x", labelsize=7.5)
    ax.grid(axis="x", visible=False)
salvar(fig, "fig5_efeito_distribuicao.png")

# Figura 6: experimento de núcleos
arq_nuc = os.path.join(RES, "nucleos_resumo.csv")
if os.path.exists(arq_nuc):
    nuc = pd.read_csv(arq_nuc)
    fig, eixos = plt.subplots(2, 2, figsize=(8.6, 6.6))
    for ax, alg in zip(eixos.flat, ALGORITMOS):
        n = nuc[nuc.algoritmo == alg].tamanho.max()
        for i, (k, dist) in enumerate([(k, dist) for k in sorted(nuc.nucleos.unique())
                                       for dist in ["ALEATORIO", "POUCOS_DISTINTOS"]]):
            d = nuc[(nuc.algoritmo == alg) & (nuc.nucleos == k) & (nuc.distribuicao == dist)
                    & (nuc.versao == "PARALELO") & (nuc.tamanho == n)].sort_values("threads")
            ax.plot(d.threads, d.speedup, label=f"{k} núcleo(s) — {NOME_DIST[dist]}", markersize=4, **ESTILOS[i])
        ax.axhline(1.0, color="0.6", linewidth=0.8)
        ax.set_xscale("log", base=2)
        ax.set_xticks([1, 2, 4, 8])
        ax.set_xticklabels(["1", "2", "4", "8"])
        ax.set_title(f"{alg} (n = {n:,})".replace(",", "."))
        ax.set_xlabel("Número de threads")
        ax.set_ylabel("Speedup")
    eixos[0, 0].legend(fontsize=7)
    salvar(fig, "fig6_experimento_nucleos.png")

# Figura 7: dispersão das amostras (boxplot) no maior tamanho, entrada aleatória
fig, eixos = plt.subplots(2, 2, figsize=(8.6, 6.0))
for ax, alg in zip(eixos.flat, ALGORITMOS):
    n = maior_tamanho(alg)
    d = brutos[(brutos.algoritmo == alg) & (brutos.distribuicao == "ALEATORIO") & (brutos.tamanho == n)]
    dados = [d[d.config == c].tempo_ms.values for c in CONFIGS]
    ax.boxplot(dados, tick_labels=["Serial", "P1", "P2", "P4", "P8"], widths=0.5,
               medianprops=dict(color="black"), flierprops=dict(marker="x", markersize=4))
    ax.set_title(f"{alg} (n = {n:,})".replace(",", "."))
    ax.set_ylabel("Tempo (ms)")
    ax.grid(axis="x", visible=False)
salvar(fig, "fig7_dispersao_amostras.png")
