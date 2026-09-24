#!/usr/bin/env python3
"""
TCC BOLA Detecção — Script de Visualização de Resultados
Uso: python generate_charts.py results.csv
"""

import sys
import csv
import matplotlib.pyplot as plt
import numpy as np

def load_results(filepath):
    data = {}
    with open(filepath, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            data[row['tool']] = {
                'tp': int(row['tp']),
                'fp': int(row['fp']),
                'fn': int(row['fn']),
                'tn': int(row['tn']),
            }
    return data

def compute_metrics(tp, fp, fn, tn):
    precision = tp / (tp + fp) if (tp + fp) > 0 else 0
    recall = tp / (tp + fn) if (tp + fn) > 0 else 0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0
    accuracy = (tp + tn) / (tp + fp + fn + tn) if (tp + fp + fn + tn) > 0 else 0
    return {'Precisão': precision, 'Recall': recall, 'F1-Score': f1, 'Acurácia': accuracy}

def plot_metrics(data, output='metrics_comparison.png'):
    tools = list(data.keys())
    metrics = ['Precisão', 'Recall', 'F1-Score', 'Acurácia']
    x = np.arange(len(metrics))
    width = 0.25

    fig, ax = plt.subplots(figsize=(10, 6))
    for i, tool in enumerate(tools):
        values = compute_metrics(**data[tool])
        bars = [values[m] * 100 for m in metrics]
        offset = (i - len(tools)/2 + 0.5) * width
        ax.bar(x + offset, bars, width, label=tool)
        for j, v in enumerate(bars):
            ax.text(x[j] + offset, v + 1, f'{v:.1f}%', ha='center', fontsize=8)

    ax.set_ylabel('Percentual (%)')
    ax.set_title('Comparação de Métricas — Detecção de BOLA')
    ax.set_xticks(x)
    ax.set_xticklabels(metrics)
    ax.legend()
    ax.set_ylim(0, 115)
    ax.grid(axis='y', alpha=0.3)

    plt.tight_layout()
    plt.savefig(output, dpi=150)
    print(f"Gráfico salvo em: {output}")

def plot_confusion_matrix(data, output='confusion_matrices.png'):
    fig, axes = plt.subplots(1, len(data), figsize=(5 * len(data), 4))
    if len(data) == 1:
        axes = [axes]

    for ax, (tool, d) in zip(axes, data.items()):
        matrix = np.array([[d['tn'], d['fp']], [d['fn'], d['tp']]])
        ax.imshow(matrix, cmap='Blues', vmin=0)
        for i in range(2):
            for j in range(2):
                ax.text(j, i, str(matrix[i, j]), ha='center', va='center',
                       fontsize=16, color='white' if matrix[i, j] > matrix.max()/2 else 'black')
        ax.set_xticks([0, 1]); ax.set_xticklabels(['Negativo (real)', 'Positivo (real)'])
        ax.set_yticks([0, 1]); ax.set_yticklabels(['Negativo (previsto)', 'Positivo (previsto)'])
        ax.set_title(tool)

    plt.tight_layout()
    plt.savefig(output, dpi=150)
    print(f"Matrizes de confusão salvas em: {output}")

def main():
    if len(sys.argv) < 2:
        print("Uso: python generate_charts.py results.csv")
        sys.exit(1)

    data = load_results(sys.argv[1])
    print("=== Métricas Calculadas ===")
    for tool, d in data.items():
        m = compute_metrics(**d)
        print(f"\n{tool}:")
        for k, v in m.items():
            print(f"  {k}: {v*100:.1f}%")

    plot_metrics(data)
    plot_confusion_matrix(data)

if __name__ == '__main__':
    main()
