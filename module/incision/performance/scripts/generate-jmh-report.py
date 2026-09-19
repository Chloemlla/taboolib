#!/usr/bin/env python3
"""Aggregate the Incision JMH matrix and render reproducible performance charts."""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
from collections import defaultdict
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np


NODE_ORDER = [
    "1.12.2-paper",
    "1.16.5-paper",
    "1.20.6-paper",
    "1.21.11-paper",
    "26.1.2-spigot",
    "26.2-paper",
]
BACKEND_ORDER = ["jvmti", "instrumentation"]
BENCHMARK_ORDER = ["baseline", "lead", "leadTrail", "predicateTrue", "spliceProceed", "siteInvoke"]
ADVICE_ORDER = [benchmark for benchmark in BENCHMARK_ORDER if benchmark != "baseline"]
FILE_PATTERN = re.compile(r"^(?P<node>.+)-java(?P<java>\d+)-(?P<backend>jvmti|instrumentation)$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--result-root",
        type=Path,
        required=True,
        help="Directory containing raw/, data/, and charts/.",
    )
    return parser.parse_args()


def load_rows(raw_root: Path) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for result_file in sorted(raw_root.glob("*.json")):
        match = FILE_PATTERN.match(result_file.stem)
        if match is None:
            raise ValueError(f"Unexpected JMH result filename: {result_file.name}")
        payload = json.loads(result_file.read_text(encoding="utf-8"))
        if len(payload) != len(BENCHMARK_ORDER):
            raise ValueError(f"Expected 6 benchmarks in {result_file.name}, got {len(payload)}")
        for entry in payload:
            metric = entry["primaryMetric"]
            if metric["scoreUnit"] != "ns/op":
                raise ValueError(f"Unexpected unit in {result_file.name}: {metric['scoreUnit']}")
            rows.append(
                {
                    "node": match.group("node"),
                    "java": int(match.group("java")),
                    "backend": match.group("backend"),
                    "benchmark": entry["benchmark"].rsplit(".", 1)[-1],
                    "score_ns_op": float(metric["score"]),
                    "score_error_ns_op": float(metric["scoreError"]),
                    "confidence_low_ns_op": float(metric["scoreConfidence"][0]),
                    "confidence_high_ns_op": float(metric["scoreConfidence"][1]),
                    "jdk_version": entry["jdkVersion"],
                    "vm_name": entry["vmName"],
                    "vm_version": entry["vmVersion"],
                    "warmup_iterations": int(entry["warmupIterations"]),
                    "measurement_iterations": int(entry["measurementIterations"]),
                    "measurement_time": entry["measurementTime"],
                    "threads": int(entry["threads"]),
                    "forks": int(entry["forks"]),
                    "raw_samples": json.dumps(metric["rawData"], separators=(",", ":")),
                }
            )
    expected = len(NODE_ORDER) * len(BACKEND_ORDER) * len(BENCHMARK_ORDER)
    if len(rows) != expected:
        raise ValueError(f"Expected {expected} matrix rows, got {len(rows)}")
    return rows


def derive_metrics(rows: list[dict[str, object]]) -> None:
    lookup = {(row["node"], row["backend"], row["benchmark"]): row for row in rows}
    for row in rows:
        baseline = lookup[(row["node"], row["backend"], "baseline")]
        score = float(row["score_ns_op"])
        baseline_score = float(baseline["score_ns_op"])
        score_error = float(row["score_error_ns_op"])
        baseline_error = float(baseline["score_error_ns_op"])
        # Error propagation assumes independent estimates. It is used only for chart uncertainty bars,
        # while the source JMH confidence interval remains preserved verbatim in the CSV.
        row["overhead_ns_op"] = score - baseline_score
        row["overhead_error_ns_op"] = math.hypot(score_error, baseline_error)
        row["score_multiplier"] = score / baseline_score
        row["overhead_multiplier"] = (score - baseline_score) / baseline_score


def write_csv(rows: list[dict[str, object]], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = list(rows[0].keys())
    with output.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def configure_plotting() -> None:
    plt.rcParams.update(
        {
            "figure.dpi": 140,
            "savefig.dpi": 180,
            "axes.grid": True,
            "axes.axisbelow": True,
            "grid.alpha": 0.22,
            "font.size": 9,
        }
    )


def plot_overhead_by_version(rows: list[dict[str, object]], output: Path) -> None:
    lookup = {(row["node"], row["backend"], row["benchmark"]): row for row in rows}
    fig, axes = plt.subplots(3, 2, figsize=(12, 10), sharex=True)
    x = np.arange(len(NODE_ORDER))
    colors = {"jvmti": "#3366cc", "instrumentation": "#dc3912"}
    markers = {"jvmti": "o", "instrumentation": "s"}
    for axis, benchmark in zip(axes.flat, ADVICE_ORDER):
        for backend in BACKEND_ORDER:
            points = [lookup[(node, backend, benchmark)] for node in NODE_ORDER]
            values = [float(point["overhead_ns_op"]) for point in points]
            errors = [float(point["overhead_error_ns_op"]) for point in points]
            axis.errorbar(
                x,
                values,
                yerr=errors,
                label=backend,
                color=colors[backend],
                marker=markers[backend],
                capsize=3,
                linewidth=1.4,
            )
        axis.set_title(benchmark)
        axis.set_ylabel("Overhead (ns/op)")
    axes.flat[-1].axis("off")
    for axis in axes[-1]:
        if axis.has_data():
            axis.set_xticks(x, NODE_ORDER, rotation=28, ha="right")
    # One shared legend keeps the five small multiples directly comparable without repeated chart chrome.
    handles, labels = axes.flat[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="lower right", bbox_to_anchor=(0.94, 0.08), frameon=False)
    fig.suptitle("Incision steady-state advice overhead across server/JVM versions")
    fig.tight_layout(rect=(0, 0.02, 1, 0.97))
    fig.savefig(output, bbox_inches="tight")
    plt.close(fig)


def plot_backend_comparison(rows: list[dict[str, object]], output: Path) -> None:
    lookup = {(row["node"], row["backend"], row["benchmark"]): row for row in rows}
    fig, axis = plt.subplots(figsize=(8, 7))
    markers = dict(zip(ADVICE_ORDER, ["o", "s", "^", "D", "P"]))
    all_values: list[float] = []
    for benchmark in ADVICE_ORDER:
        x = [float(lookup[(node, "jvmti", benchmark)]["overhead_ns_op"]) for node in NODE_ORDER]
        y = [float(lookup[(node, "instrumentation", benchmark)]["overhead_ns_op"]) for node in NODE_ORDER]
        all_values.extend(x + y)
        axis.scatter(x, y, marker=markers[benchmark], s=52, label=benchmark, alpha=0.85)
    low = min(all_values) * 0.92
    high = max(all_values) * 1.05
    axis.plot([low, high], [low, high], color="#555555", linewidth=1, linestyle="--", label="equal cost")
    axis.set_xlim(low, high)
    axis.set_ylim(low, high)
    axis.set_xlabel("JVMTI overhead (ns/op)")
    axis.set_ylabel("Instrumentation overhead (ns/op)")
    axis.set_title("Paired backend comparison (each point is one server/JVM node)")
    axis.legend(frameon=False, ncol=2)
    fig.tight_layout()
    fig.savefig(output, bbox_inches="tight")
    plt.close(fig)


def plot_normalized_overhead(rows: list[dict[str, object]], output: Path) -> None:
    lookup = {(row["node"], row["backend"], row["benchmark"]): row for row in rows}
    fig, axes = plt.subplots(1, 2, figsize=(13, 5), sharey=True)
    x = np.arange(len(NODE_ORDER))
    width = 0.15
    for axis, backend in zip(axes, BACKEND_ORDER):
        for index, benchmark in enumerate(ADVICE_ORDER):
            values = [float(lookup[(node, backend, benchmark)]["overhead_multiplier"]) for node in NODE_ORDER]
            offset = (index - (len(ADVICE_ORDER) - 1) / 2) * width
            axis.bar(x + offset, values, width=width, label=benchmark)
        axis.set_title(backend)
        axis.set_yscale("log")
        axis.set_xticks(x, NODE_ORDER, rotation=28, ha="right")
        axis.set_ylabel("Overhead / baseline (log scale)")
    handles, labels = axes[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="upper center", ncol=5, frameon=False, bbox_to_anchor=(0.5, 1.02))
    fig.suptitle("Normalized advice overhead relative to the bare fixture method", y=1.08)
    fig.tight_layout()
    fig.savefig(output, bbox_inches="tight")
    plt.close(fig)


def plot_score_heatmap(rows: list[dict[str, object]], output: Path) -> None:
    lookup = {(row["node"], row["backend"], row["benchmark"]): row for row in rows}
    columns = [(node, backend) for node in NODE_ORDER for backend in BACKEND_ORDER]
    matrix = np.array(
        [[float(lookup[(node, backend, benchmark)]["score_ns_op"]) for node, backend in columns] for benchmark in BENCHMARK_ORDER]
    )
    fig, axis = plt.subplots(figsize=(14, 5.8))
    image = axis.imshow(matrix, aspect="auto", cmap="viridis")
    axis.set_xticks(
        np.arange(len(columns)),
        [f"{node}\n{backend}" for node, backend in columns],
        rotation=35,
        ha="right",
    )
    axis.set_yticks(np.arange(len(BENCHMARK_ORDER)), BENCHMARK_ORDER)
    for row_index in range(matrix.shape[0]):
        for column_index in range(matrix.shape[1]):
            value = matrix[row_index, column_index]
            text_color = "white" if value < matrix.max() * 0.55 else "black"
            axis.text(column_index, row_index, f"{value:.1f}", ha="center", va="center", color=text_color, fontsize=7)
    colorbar = fig.colorbar(image, ax=axis, pad=0.015)
    colorbar.set_label("Measured cost (ns/op)")
    axis.set_title("Complete JMH score matrix")
    fig.tight_layout()
    fig.savefig(output, bbox_inches="tight")
    plt.close(fig)


def write_aggregate_csv(rows: list[dict[str, object]], output: Path) -> None:
    grouped: dict[tuple[str, str], list[dict[str, object]]] = defaultdict(list)
    for row in rows:
        if row["benchmark"] != "baseline":
            grouped[(str(row["backend"]), str(row["benchmark"]))].append(row)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=["backend", "benchmark", "mean_overhead_ns_op", "median_overhead_ns_op", "geomean_overhead_multiplier"],
        )
        writer.writeheader()
        for backend in BACKEND_ORDER:
            for benchmark in ADVICE_ORDER:
                group = grouped[(backend, benchmark)]
                overheads = np.array([float(row["overhead_ns_op"]) for row in group])
                multipliers = np.array([float(row["overhead_multiplier"]) for row in group])
                writer.writerow(
                    {
                        "backend": backend,
                        "benchmark": benchmark,
                        "mean_overhead_ns_op": f"{overheads.mean():.6f}",
                        "median_overhead_ns_op": f"{np.median(overheads):.6f}",
                        "geomean_overhead_multiplier": f"{math.exp(np.log(multipliers).mean()):.6f}",
                    }
                )


def main() -> None:
    args = parse_args()
    result_root = args.result_root.resolve()
    rows = load_rows(result_root / "raw")
    derive_metrics(rows)
    configure_plotting()
    data_root = result_root / "data"
    chart_root = result_root / "charts"
    chart_root.mkdir(parents=True, exist_ok=True)
    write_csv(rows, data_root / "jmh-summary.csv")
    write_aggregate_csv(rows, data_root / "jmh-aggregate.csv")
    plot_overhead_by_version(rows, chart_root / "overhead-by-version.png")
    plot_backend_comparison(rows, chart_root / "backend-comparison.png")
    plot_normalized_overhead(rows, chart_root / "normalized-overhead.png")
    plot_score_heatmap(rows, chart_root / "score-heatmap.png")
    print(f"Generated {len(rows)} rows and 4 charts under {result_root}")


if __name__ == "__main__":
    main()

