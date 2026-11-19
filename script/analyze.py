#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
analyze.py - Analyze benchmark results to find the optimal CUTOFF for Hybrid MergeSort.

Reads  : result/raw_results.csv (or the latest raw_results*.csv file in result/)
         exact header: dataset,size,algo,cutoff,rep,nanos
Writes : result/analysis/*.csv, result/analysis/summary_tables.md
         result/analysis/plots/*.png (300 DPI)

Usage (from anywhere):
    python script/analyze.py
    python script/analyze.py --tol 0.03
    python script/analyze.py --min-size 0      # also keep N = 100 (appendix, not used for conclusions)
    python script/analyze.py --input result/raw_results_20261118_062208.csv

Libraries: pandas, numpy, matplotlib.
"""
import argparse
import math
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")  # no GUI required
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402
import pandas as pd  # noqa: E402
from matplotlib.lines import Line2D  # noqa: E402
from matplotlib.patches import Patch  # noqa: E402
from matplotlib.ticker import FuncFormatter  # noqa: E402

# ----------------------------------------------------------------------------
# CONSTANTS
# ----------------------------------------------------------------------------
EXPECTED_HEADER = ["dataset", "size", "algo", "cutoff", "rep", "nanos"]
ALGO = "MergeWithCutoff"
BASELINE_CUTOFF = 0
TEXTBOOK_CUTOFFS = (7, 10)
TYPE_ORDER = ["random", "sorted", "reverse", "nearly_sorted", "few_uniques", "interleaved"]
DOUBLING_PAIRS = [(500_000, 1_000_000), (1_000_000, 2_000_000)]
LOGLOG_MIN_SIZE = 100_000
THEORY_N = 5_000_000
CV_WARN_PERCENT = 10.0
DPI = 300

TYPE_COLORS = {
    "random": "#1f77b4", "sorted": "#2ca02c", "reverse": "#d62728",
    "nearly_sorted": "#9467bd", "few_uniques": "#8c564b", "interleaved": "#17becf",
}
MARKERS = ["o", "s", "^", "D", "v", "P"]


# ----------------------------------------------------------------------------
# DATA LOADING
# ----------------------------------------------------------------------------
def find_input(arg_input, result_dir):
    if arg_input:
        p = Path(arg_input)
        if not p.is_file():
            sys.exit(f"[ERROR] Input file not found: {p}")
        return p
    candidates = [p for p in result_dir.glob("raw_results*.csv") if p.is_file()]
    if not candidates:
        sys.exit(f"[ERROR] No raw_results*.csv found in {result_dir}. Run the benchmark first.")
    return max(candidates, key=lambda p: p.stat().st_mtime)


def load_raw(path):
    df = pd.read_csv(path)
    if list(df.columns) != EXPECTED_HEADER:
        sys.exit(f"[ERROR] Unexpected header in {path.name}: {list(df.columns)}; expected {EXPECTED_HEADER}")
    if df.empty:
        sys.exit(f"[ERROR] {path.name} has no data rows")
    if df.isna().any().any():
        sys.exit(f"[ERROR] {path.name} contains missing values")

    df = df[df["algo"] == ALGO].copy()
    if df.empty:
        sys.exit(f"[ERROR] No rows with algo == {ALGO}")

    # dataset = <type>_<size>  -> extract type by removing the _<size> suffix
    suffix = df["dataset"].str.extract(r"_(\d+)$")[0]
    if suffix.isna().any():
        sys.exit("[ERROR] Some dataset names do not end with _<size>")
    if (suffix.astype("int64") != df["size"].astype("int64")).any():
        sys.exit("[ERROR] dataset suffix does not match the size column")
    df["type"] = df["dataset"].str.replace(r"_\d+$", "", regex=True)

    df["size"] = df["size"].astype("int64")
    df["cutoff"] = df["cutoff"].astype("int64")
    df["rep"] = df["rep"].astype("int64")
    df["ms"] = df["nanos"].astype("float64") / 1e6
    if (df["ms"] <= 0).any():
        print("[WARN] Some measurements are <= 0 ns; they are kept but rel_time may be unreliable.")
    return df


def apply_validity_filter(df, min_size):
    """Keep only (size, cutoff) pairs that are true hybrids.
- size < min_size              : drop (microsecond-scale timing + large cutoff relative to N).
- cutoff > 0 and cutoff >= size : drop (MergeWithCutoff then runs pure InsertionSort on the whole array).
- cutoff = 0                   : keep ONLY as baseline (pure MergeSort), not considered for 'best'.
    """
    n0 = len(df)
    small = df["size"] < min_size
    sizes_dropped = sorted(df.loc[small, "size"].unique().tolist())
    df = df[~small]
    degenerate = (df["cutoff"] > BASELINE_CUTOFF) & (df["cutoff"] >= df["size"])
    pairs = sorted({(int(s), int(c)) for s, c in df.loc[degenerate, ["size", "cutoff"]].values})
    df = df[~degenerate].copy()
    if df.empty:
        sys.exit("[ERROR] No data left after validity filtering")
    note = (f"Filter: min_size={min_size} -> dropped sizes {sizes_dropped or 'none'}; "
            f"pure-insertion pairs (cutoff >= size) dropped: {pairs or 'none'}; rows {n0:,} -> {len(df):,}")
    return df, note


def ordered_types(types):
    known = [t for t in TYPE_ORDER if t in types]
    extra = sorted(t for t in types if t not in TYPE_ORDER)
    return known + extra


# ----------------------------------------------------------------------------
# STATISTICS
# ----------------------------------------------------------------------------
def compute_detailed_stats(df, types):
    g = df.groupby(["type", "size", "cutoff"])["ms"]
    stats = g.agg(mean_ms="mean", median_ms="median", std_ms="std",
                  min_ms="min", max_ms="max", n_reps="count").reset_index()
    stats["q1_ms"] = g.quantile(0.25).values
    stats["q3_ms"] = g.quantile(0.75).values
    stats["std_ms"] = stats["std_ms"].fillna(0.0)          # sample std (ddof=1), 1 rep -> 0
    stats["cv_percent"] = np.where(stats["mean_ms"] > 0, stats["std_ms"] / stats["mean_ms"] * 100.0, np.nan)

    base = (stats[stats["cutoff"] == BASELINE_CUTOFF][["type", "size", "median_ms"]]
            .rename(columns={"median_ms": "baseline_median_ms"}))
    stats = stats.merge(base, on=["type", "size"], how="left")
    if stats["baseline_median_ms"].isna().any():
        bad = stats[stats["baseline_median_ms"].isna()][["type", "size"]].drop_duplicates()
        sys.exit(f"[ERROR] Missing baseline (cutoff={BASELINE_CUTOFF}) for:\n{bad.to_string(index=False)}")
    stats["rel_time"] = stats["median_ms"] / stats["baseline_median_ms"]
    stats["speedup"] = stats["baseline_median_ms"] / stats["median_ms"]

    stats["_rank"] = stats["type"].map({t: i for i, t in enumerate(types)})
    stats = stats.sort_values(["_rank", "size", "cutoff"]).drop(columns="_rank").reset_index(drop=True)
    cols = ["type", "size", "cutoff", "n_reps", "mean_ms", "median_ms", "std_ms", "cv_percent",
            "q1_ms", "q3_ms", "min_ms", "max_ms", "rel_time", "speedup"]
    return stats[cols], stats  # (export version, full version with baseline_median_ms)


def compute_best_cutoff(stats, types, tol):
    rows = []
    for t in types:
        for size, g in stats[stats["type"] == t].groupby("size"):
            g = g.sort_values("cutoff")
            h = g[g["cutoff"] > BASELINE_CUTOFF]             # consider only true hybrids (exclude cutoff = 0)
            if h.empty:
                continue
            best = h.loc[h["median_ms"].idxmin()]            # tie -> smallest cutoff
            base_ms = float(g.loc[g["cutoff"] == BASELINE_CUTOFF, "median_ms"].iloc[0])
            near = h[h["median_ms"] <= best["median_ms"] * (1.0 + tol)]["cutoff"].tolist()
            row = {
                "type": t, "size": int(size), "baseline_ms": base_ms,
                "best_cutoff": int(best["cutoff"]), "best_ms": float(best["median_ms"]),
                "speedup": base_ms / float(best["median_ms"]),
                "near_optimal_cutoffs": ";".join(str(int(c)) for c in near),
            }
            for tb in TEXTBOOK_CUTOFFS:
                m = g.loc[g["cutoff"] == tb, "median_ms"]
                row[f"cutoff_{tb}_diff_pct"] = (float(m.iloc[0]) / float(best["median_ms"]) - 1.0) * 100.0 \
                    if len(m) else np.nan
            rows.append(row)
    return pd.DataFrame(rows)


def compute_overall(stats, types):
    s = stats.copy()
    s["log_rel"] = np.log(s["rel_time"])
    per_type = s.pivot_table(index="cutoff", columns="type", values="log_rel", aggfunc="mean")
    out = np.exp(per_type[types])
    out["all_types"] = np.exp(s.groupby("cutoff")["log_rel"].mean())   # gm over every (type, size)
    return out.reset_index()


def pick_recommended(overall):
    cand = overall[overall["cutoff"] > BASELINE_CUTOFF]
    if cand.empty:
        return BASELINE_CUTOFF
    return int(cand.loc[cand["all_types"].idxmin(), "cutoff"])


def compute_scaling(stats, types, recommended):
    rows = []
    roles = [("baseline", BASELINE_CUTOFF), ("recommended", recommended)]
    for t in types:
        for role, c in roles:
            sub = stats[(stats["type"] == t) & (stats["cutoff"] == c)].set_index("size")["median_ms"].sort_index()
            for a, b in DOUBLING_PAIRS:
                if a in sub.index and b in sub.index:
                    rows.append({"kind": "doubling", "type": t, "cutoff_role": role, "cutoff": c,
                                 "size_from": a, "size_to": b, "t_from_ms": sub[a], "t_to_ms": sub[b],
                                 "doubling_ratio": sub[b] / sub[a]})
            fit = sub[sub.index >= LOGLOG_MIN_SIZE]
            if len(fit) >= 2 and (fit > 0).all():
                x, y = np.log(fit.index.values.astype(float)), np.log(fit.values)
                b_exp, log_a = np.polyfit(x, y, 1)
                resid = y - (log_a + b_exp * x)
                ss_tot = float(((y - y.mean()) ** 2).sum())
                r2 = 1.0 - float((resid ** 2).sum()) / ss_tot if ss_tot > 0 else np.nan
                rows.append({"kind": "loglog_fit", "type": t, "cutoff_role": role, "cutoff": c,
                             "size_from": int(fit.index.min()), "size_to": int(fit.index.max()),
                             "n_points": len(fit), "fit_a": math.exp(log_a), "fit_b": b_exp, "r2": r2})
    cols = ["kind", "type", "cutoff_role", "cutoff", "size_from", "size_to", "t_from_ms", "t_to_ms",
            "doubling_ratio", "n_points", "fit_a", "fit_b", "r2"]
    return pd.DataFrame(rows).reindex(columns=cols)


def theory_model(n, k):
    """T(K) ~ N*log2(N/K) + N*K/4 (relative units)."""
    return n * np.log2(n / k) + 0.25 * n * k


# ----------------------------------------------------------------------------
# PLOTS
# ----------------------------------------------------------------------------
def save_fig(fig, path):
    fig.savefig(path, dpi=DPI, bbox_inches="tight")
    plt.close(fig)
    print(f"  saved {path.name}")


def size_fmt(n):
    return f"{int(n):,}"


def plot_time_vs_cutoff(stats, best, t, cutoffs, pos, plots_dir):
    sub = stats[stats["type"] == t]
    sizes = sorted(sub["size"].unique())
    ncols = 4
    nrows = math.ceil(len(sizes) / ncols)
    fig, axes = plt.subplots(nrows, ncols, figsize=(5.2 * ncols, 3.9 * nrows), squeeze=False)
    color = TYPE_COLORS.get(t, "#1f77b4")
    for ax, size in zip(axes.flat, sizes):
        g = sub[sub["size"] == size].sort_values("cutoff")
        x = [pos[c] for c in g["cutoff"]]
        base_ms = float(g.loc[g["cutoff"] == BASELINE_CUTOFF, "median_ms"].iloc[0])
        for tb in TEXTBOOK_CUTOFFS:
            if tb in pos:
                ax.axvline(pos[tb], color="tab:orange", alpha=0.30, lw=4, zorder=0)
        ax.fill_between(x, g["q1_ms"], g["q3_ms"], color=color, alpha=0.25, linewidth=0)
        ax.plot(x, g["median_ms"], "-o", color=color, ms=3.5, lw=1.4)
        ax.axhline(base_ms, color="gray", ls="--", lw=1.1)
        b = best[(best["type"] == t) & (best["size"] == size)].iloc[0]
        ax.plot(pos[int(b["best_cutoff"])], b["best_ms"], marker="*", ms=15, color="gold",
                mec="black", mew=0.8, zorder=5, ls="none")
        ax.set_xticks(range(len(cutoffs)))
        ax.set_xticklabels(cutoffs, rotation=90, fontsize=8)
        ax.set_title(f"N = {size_fmt(size)}  (best cutoff = {int(b['best_cutoff'])})", fontsize=10)
        ax.grid(alpha=0.25)
    for ax in axes.flat[len(sizes):]:
        ax.axis("off")
    for ax in axes[:, 0]:
        ax.set_ylabel("median time (ms)")
    for ax in axes[-1, :]:
        if ax.axison:
            ax.set_xlabel("cutoff (categorical axis, NOT linear)")
    handles = [Line2D([0], [0], color=color, marker="o", ms=4, label="median"),
               Patch(facecolor=color, alpha=0.25, label="Q1-Q3"),
               Line2D([0], [0], color="gray", ls="--", label="baseline (cutoff = 0)"),
               Line2D([0], [0], marker="*", color="gold", mec="black", ls="none", ms=13, label="best cutoff"),
               Patch(facecolor="tab:orange", alpha=0.35, label="textbook cutoffs 7 and 10")]
    fig.suptitle(f"Median time vs cutoff - {t}", fontsize=14)
    fig.tight_layout(rect=[0, 0.05, 1, 0.97])
    fig.legend(handles=handles, loc="lower center", ncol=5, frameon=False, fontsize=10)
    save_fig(fig, plots_dir / f"time_vs_cutoff_{t}.png")


def plot_relative_time(stats, t, cutoffs, pos, plots_dir):
    sub = stats[stats["type"] == t]
    sizes = sorted(sub["size"].unique())
    colors = plt.cm.viridis(np.linspace(0.0, 0.92, len(sizes)))
    fig, ax = plt.subplots(figsize=(11, 6))
    for tb in TEXTBOOK_CUTOFFS:
        if tb in pos:
            ax.axvline(pos[tb], color="tab:orange", alpha=0.25, lw=5, zorder=0)
    for col, size in zip(colors, sizes):
        g = sub[sub["size"] == size].sort_values("cutoff")
        ax.plot([pos[c] for c in g["cutoff"]], g["rel_time"], "-o", color=col, ms=4, lw=1.5,
                label=size_fmt(size))
    ax.axhline(1.0, color="black", ls="--", lw=1.1)
    ax.set_xticks(range(len(cutoffs)))
    ax.set_xticklabels(cutoffs)
    ax.set_xlabel("cutoff (categorical axis, NOT linear; orange bands = textbook 7 and 10)")
    ax.set_ylabel("relative time = median / median(cutoff = 0)")
    ax.set_title(f"Relative time vs cutoff - {t}  (1.0 = baseline, lower is better)")
    ax.grid(alpha=0.25)
    ax.legend(title="N", loc="center left", bbox_to_anchor=(1.01, 0.5), frameon=False)
    save_fig(fig, plots_dir / f"relative_time_{t}.png")


def plot_overall(overall, types, cutoffs, pos, recommended, plots_dir):
    fig, ax = plt.subplots(figsize=(11, 6))
    x = [pos[c] for c in overall["cutoff"]]
    for tb in TEXTBOOK_CUTOFFS:
        if tb in pos:
            ax.axvline(pos[tb], color="tab:orange", alpha=0.25, lw=5, zorder=0)
    for t in types:
        ax.plot(x, overall[t], "-", color=TYPE_COLORS.get(t), lw=1.1, alpha=0.8, label=t)
    ax.plot(x, overall["all_types"], "-o", color="black", lw=3.2, ms=5, label="all types (geometric mean)")
    rec_row = overall[overall["cutoff"] == recommended].iloc[0]
    ax.plot(pos[recommended], rec_row["all_types"], marker="*", ms=18, color="gold", mec="black",
            ls="none", zorder=6, label=f"recommended cutoff = {recommended}")
    ax.axhline(1.0, color="gray", ls="--", lw=1.1)
    ax.set_xticks(range(len(cutoffs)))
    ax.set_xticklabels(cutoffs)
    ax.set_xlabel("cutoff (categorical axis, NOT linear; orange bands = textbook 7 and 10)")
    ax.set_ylabel("relative time (geometric mean over sizes)")
    ax.set_title("Overall relative time vs cutoff (1.0 = baseline, lower is better)")
    ax.grid(alpha=0.25)
    ax.legend(loc="center left", bbox_to_anchor=(1.01, 0.5), frameon=False)
    save_fig(fig, plots_dir / "overall_cutoff.png")


def plot_best_cutoff_vs_size(best, types, cutoffs, pos, plots_dir):
    fig, ax = plt.subplots(figsize=(11, 6))
    for tb in TEXTBOOK_CUTOFFS:
        if tb in pos:
            ax.axhline(pos[tb], color="tab:orange", alpha=0.25, lw=5, zorder=0)
    for i, t in enumerate(types):
        g = best[best["type"] == t].sort_values("size")
        ax.plot(g["size"], [pos[c] for c in g["best_cutoff"]], linestyle="-", marker=MARKERS[i % len(MARKERS)],
                color=TYPE_COLORS.get(t), lw=1.4, alpha=0.85, ms=12 - 1.4 * i, mfc="none", mew=1.6, label=t)
    sizes = sorted(best["size"].unique())
    ax.set_xscale("log")
    ax.set_xticks(sizes)
    ax.set_xticklabels([size_fmt(s) for s in sizes], rotation=45, ha="right")
    ax.xaxis.set_minor_formatter(FuncFormatter(lambda v, p: ""))
    ax.set_yticks(range(len(cutoffs)))
    ax.set_yticklabels(cutoffs)
    ax.set_xlabel("N (log scale)")
    ax.set_ylabel("best cutoff (categorical axis, NOT linear)")
    ax.set_title("Best cutoff vs data size (orange bands = textbook 7 and 10)")
    ax.grid(alpha=0.25)
    ax.legend(loc="center left", bbox_to_anchor=(1.01, 0.5), frameon=False)
    save_fig(fig, plots_dir / "best_cutoff_vs_size.png")


def plot_standard(stats, types, recommended, plots_dir):
    ncols = 3
    nrows = math.ceil(len(types) / ncols)
    fig, axes = plt.subplots(nrows, ncols, figsize=(5.6 * ncols, 4.2 * nrows), squeeze=False)
    for ax, t in zip(axes.flat, types):
        for c, col, lab, mk in [(BASELINE_CUTOFF, "tab:red", "cutoff = 0 (baseline)", "o"),
                                (recommended, "tab:blue", f"cutoff = {recommended} (recommended)", "s")]:
            g = stats[(stats["type"] == t) & (stats["cutoff"] == c)].sort_values("size")
            ax.plot(g["size"] / 1e6, g["median_ms"], "-" + mk, color=col, ms=5, lw=1.6, label=lab)
        ax.set_title(t)
        ax.set_xlabel("N (million elements)")
        ax.set_ylabel("T(N) (ms)")
        ax.grid(alpha=0.25)
    for ax in axes.flat[len(types):]:
        ax.axis("off")
    handles, labels = axes.flat[0].get_legend_handles_labels()
    fig.suptitle("T(N): baseline vs recommended cutoff (median time)", fontsize=14)
    fig.tight_layout(rect=[0, 0.05, 1, 0.96])
    fig.legend(handles, labels, loc="lower center", ncol=2, frameon=False, fontsize=11)
    save_fig(fig, plots_dir / "standard_plot.png")


def theory_summary(stats, best):
    """Returns a dict of theoretical vs empirical info (random), or None if data is missing."""
    sub = stats[stats["type"] == "random"]
    if sub.empty:
        return None
    n = THEORY_N if THEORY_N in set(sub["size"]) else int(sub["size"].max())
    g = sub[sub["size"] == n].sort_values("cutoff")
    emp = g[g["cutoff"] > BASELINE_CUTOFF]
    if emp.empty:
        return None
    ks = emp["cutoff"].values.astype(float)
    th_best_tested = int(ks[np.argmin(theory_model(n, ks))])
    emp_best = emp.loc[emp["median_ms"].idxmin()]
    return {"N": n, "k_star_continuous": 4.0 / math.log(2.0), "theory_best_tested_cutoff": th_best_tested,
            "empirical_best_cutoff": int(emp_best["cutoff"]), "empirical_best_ms": float(emp_best["median_ms"]),
            "baseline_ms": float(g.loc[g["cutoff"] == BASELINE_CUTOFF, "median_ms"].iloc[0]), "g": g}


def plot_theory_vs_empirical(stats, best, plots_dir):
    info = theory_summary(stats, best)
    if info is None:
        print("  [skip] theory_vs_empirical_random.png (no random data)")
        return None
    n, g = info["N"], info["g"]
    t0 = info["baseline_ms"]
    scale = t0 / theory_model(n, 1.0)          # cutoff = 0 is equivalent to pure merge sort ~ K = 1
    emp = g[g["cutoff"] > BASELINE_CUTOFF]
    k_max = float(emp["cutoff"].max())
    yerr = np.vstack([emp["median_ms"] - emp["q1_ms"], emp["q3_ms"] - emp["median_ms"]])
    fig, axes = plt.subplots(1, 2, figsize=(14, 5.6))
    for ax, (k_hi, title) in zip(axes, [(k_max, "full cutoff range"), (min(24.0, k_max), "zoom: cutoff <= 24")]):
        ks = np.linspace(1.0, k_hi, 600)
        ax.plot(ks, theory_model(n, ks) * scale, "-", color="tab:green", lw=2.2,
                label=r"theory: $T(K) \propto N\log_2(N/K) + \frac{1}{4}NK$ (scaled)")
        e = emp[emp["cutoff"] <= k_hi]
        ax.errorbar(e["cutoff"], e["median_ms"], yerr=yerr[:, (emp["cutoff"] <= k_hi).values], fmt="o-",
                    color="tab:blue", ms=5, lw=1.3, capsize=3, label="empirical median (bars: Q1-Q3)")
        ax.axhline(t0, color="gray", ls="--", lw=1.1, label="baseline cutoff = 0 (theory anchored at K = 1)")
        ax.axvline(info["k_star_continuous"], color="tab:green", ls=":", lw=1.5,
                   label=rf"theory $K^*=4/\ln 2 \approx {info['k_star_continuous']:.2f}$")
        eb = info["empirical_best_cutoff"]
        if eb <= k_hi:
            ax.plot(eb, info["empirical_best_ms"], marker="*", ms=17, color="gold", mec="black", ls="none",
                    zorder=6, label=f"empirical best cutoff = {eb}")
        ax.set_xlabel("cutoff K (linear axis)")
        ax.set_ylabel("median time (ms)")
        ax.set_title(f"random, N = {size_fmt(n)} - {title}")
        ax.grid(alpha=0.25)
    axes[0].legend(fontsize=8.5, loc="best")
    fig.tight_layout()
    save_fig(fig, plots_dir / "theory_vs_empirical_random.png")
    return info


# ----------------------------------------------------------------------------
# MARKDOWN
# ----------------------------------------------------------------------------
def _fmt_float(v, digits):
    if pd.isna(v):
        return ""
    if v == 0:
        return "0"
    if abs(v) < 0.01:
        return f"{v:.3e}"
    if abs(v) >= 1000:
        return f"{v:,.1f}"
    return f"{v:.{digits}f}"


def _fmt_col(series, digits):
    if pd.api.types.is_integer_dtype(series):
        return [f"{int(v):,}" if series.name in ("size", "size_from", "size_to") else str(int(v)) for v in series]
    if pd.api.types.is_float_dtype(series):
        return [_fmt_float(v, digits) for v in series]
    return [str(v) for v in series]


def md_table(df, digits=3):
    cols = [_fmt_col(df[c], digits) for c in df.columns]
    lines = ["| " + " | ".join(df.columns) + " |", "|" + "|".join("---" for _ in df.columns) + "|"]
    for i in range(len(df)):
        lines.append("| " + " | ".join(col[i] for col in cols) + " |")
    return "\n".join(lines)


def write_markdown(path, ctx):
    stats, best, overall, scaling = ctx["stats"], ctx["best"], ctx["overall"], ctx["scaling"]
    types, rec, tol = ctx["types"], ctx["recommended"], ctx["tol"]
    L = []
    L.append("# Cutoff experiment - summary tables\n")
    L.append(f"- Input file: `{ctx['input'].name}`")
    L.append(f"- Types: {', '.join(types)}; sizes: {', '.join(size_fmt(s) for s in sorted(stats['size'].unique()))}")
    L.append(f"- Cutoffs: {', '.join(str(c) for c in sorted(stats['cutoff'].unique()))} (baseline = {BASELINE_CUTOFF})")
    L.append(f"- Validity filter: {ctx['filter_note']}")
    L.append("- `best_cutoff` / `near_optimal_cutoffs` only consider true hybrid cutoffs (cutoff > 0); "
             "cutoff = 0 (pure MergeSort) is used only as the baseline.")
    L.append(f"- Near-optimal tolerance: `--tol {tol}` (median <= best_ms * (1 + tol))")
    L.append(f"- Recommended cutoff (lowest geometric-mean rel_time over all types and sizes, cutoff > 0): **{rec}**\n")

    cv = stats["cv_percent"]
    L.append("## Data quality\n")
    L.append(f"- Groups (type, size, cutoff): {len(stats)}; repetitions per group: "
             f"{int(stats['n_reps'].min())} to {int(stats['n_reps'].max())}")
    L.append(f"- Median CV: {cv.median():.2f}%; groups with CV > {CV_WARN_PERCENT:.0f}%: "
             f"{int((cv > CV_WARN_PERCENT).sum())} ({(cv > CV_WARN_PERCENT).mean() * 100:.1f}%)")
    L.append("- Differences between cutoffs that are smaller than the run-to-run spread (see `cv_percent`, "
             "`q1_ms`/`q3_ms` in `detailed_stats.csv`) should not be interpreted as real effects.")
    L.append("")

    L.append("## 1. Best cutoff per (type, size)\n")
    L.append(md_table(best, 3) + "\n")

    L.append("## 2. Overall relative time per cutoff (geometric mean over sizes; 1.0 = baseline)\n")
    L.append(md_table(overall, 4))
    best_row = {t: int(overall.loc[overall[t].idxmin(), "cutoff"]) for t in types + ["all_types"]}
    L.append("\nLowest value per column: " + ", ".join(f"{k} -> cutoff {v}" for k, v in best_row.items()) + "\n")

    L.append("## 3. Doubling ratios T(2N)/T(N)\n")
    d = scaling[scaling["kind"] == "doubling"][["type", "cutoff_role", "cutoff", "size_from", "size_to",
                                                "t_from_ms", "t_to_ms", "doubling_ratio"]]
    L.append("For an ideal N log N algorithm the ratio is slightly above 2.\n")
    L.append(md_table(d, 3) + "\n")

    L.append(f"## 4. Log-log regression  T(ms) = a * N^b  (sizes >= {LOGLOG_MIN_SIZE:,})\n")
    f = scaling[scaling["kind"] == "loglog_fit"][["type", "cutoff_role", "cutoff", "n_points", "fit_a", "fit_b", "r2"]].copy()
    f["n_points"] = f["n_points"].astype(int)
    L.append(md_table(f, 4).replace("| fit_a |", "| fit_a (ms) |") + "\n")

    th = ctx.get("theory")
    if th:
        L.append("## 5. Theory vs empirical (random)\n")
        L.append(md_table(pd.DataFrame([{
            "N": th["N"], "theory_K_star (4/ln2)": th["k_star_continuous"],
            "theory_best_among_tested": th["theory_best_tested_cutoff"],
            "empirical_best_cutoff": th["empirical_best_cutoff"]}]), 3).replace("| N |", "| N |") + "\n")
        L.append("The model `N log2(N/K) + N K / 4` is a comparison-count model; real timing also depends on "
                 "memory access, JIT and boxed `Integer` objects, so a shift of the empirical optimum is expected.\n")

    L.append("## Files\n")
    L.append("`detailed_stats.csv` (all groups, not repeated here), `best_cutoff.csv`, `overall_cutoff.csv`, "
             "`doubling_and_scaling.csv`, and plots in `plots/`.")
    path.write_text("\n".join(L) + "\n", encoding="utf-8")


# ----------------------------------------------------------------------------
# MAIN
# ----------------------------------------------------------------------------
def main():
    ap = argparse.ArgumentParser(description="Analyze Hybrid MergeSort cutoff benchmark results.")
    ap.add_argument("--tol", type=float, default=0.02,
                    help="near-optimal tolerance (default 0.02 = within 2%% of best median)")
    ap.add_argument("--min-size", type=int, default=1000,
                    help="ignore datasets with size < MIN_SIZE (default 1000; use 0 to keep everything)")
    ap.add_argument("--input", type=str, default=None,
                    help="raw results CSV (default: newest result/raw_results*.csv)")
    args = ap.parse_args()
    if args.tol < 0:
        sys.exit("[ERROR] --tol must be >= 0")

    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except Exception:
            pass

    root = Path(__file__).resolve().parent.parent
    result_dir = root / "result"
    out_dir = result_dir / "analysis"
    plots_dir = out_dir / "plots"
    plots_dir.mkdir(parents=True, exist_ok=True)

    in_path = find_input(args.input, result_dir)
    print(f"Input : {in_path}")
    df = load_raw(in_path)
    df, filter_note = apply_validity_filter(df, args.min_size)
    print(filter_note)
    types = ordered_types(df["type"].unique().tolist())
    print(f"Rows  : {len(df):,} | types: {types} | sizes: {sorted(df['size'].unique().tolist())}")

    detailed, stats = compute_detailed_stats(df, types)
    best = compute_best_cutoff(stats, types, args.tol)
    overall = compute_overall(stats, types)
    rec = pick_recommended(overall)
    scaling = compute_scaling(stats, types, rec)

    detailed.to_csv(out_dir / "detailed_stats.csv", index=False, float_format="%.6f")
    best.to_csv(out_dir / "best_cutoff.csv", index=False, float_format="%.6f")
    overall.to_csv(out_dir / "overall_cutoff.csv", index=False, float_format="%.6f")
    scaling.to_csv(out_dir / "doubling_and_scaling.csv", index=False, float_format="%.6f")
    print(f"Tables written to {out_dir}")

    cutoffs = sorted(stats["cutoff"].unique().tolist())
    pos = {c: i for i, c in enumerate(cutoffs)}
    plt.rcParams.update({"font.size": 10, "axes.titlesize": 11, "axes.labelsize": 10})

    print("Plots:")
    for t in types:
        plot_time_vs_cutoff(stats, best, t, cutoffs, pos, plots_dir)
        plot_relative_time(stats, t, cutoffs, pos, plots_dir)
    plot_overall(overall, types, cutoffs, pos, rec, plots_dir)
    plot_best_cutoff_vs_size(best, types, cutoffs, pos, plots_dir)
    plot_standard(stats, types, rec, plots_dir)
    theory = plot_theory_vs_empirical(stats, best, plots_dir)

    write_markdown(out_dir / "summary_tables.md", {
        "stats": stats, "best": best, "overall": overall, "scaling": scaling, "types": types,
        "recommended": rec, "tol": args.tol, "input": in_path, "theory": theory,
        "filter_note": filter_note})

    print("\n=== KEY RESULTS ===")
    print(f"Recommended cutoff (all types, geometric mean): {rec}")
    row = overall[overall["cutoff"] == rec].iloc[0]
    print(f"  geometric-mean rel_time = {row['all_types']:.4f}  (speedup {1.0 / row['all_types']:.4f}x vs baseline)")
    if theory:
        print(f"Theory K* = 4/ln2 = {theory['k_star_continuous']:.2f}; empirical best (random, "
              f"N={theory['N']:,}) = {theory['empirical_best_cutoff']}")
    print(f"Summary: {out_dir / 'summary_tables.md'}")


if __name__ == "__main__":
    main()