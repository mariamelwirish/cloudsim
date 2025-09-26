import os
import pandas as pd
import matplotlib.pyplot as plt

# ---------------- paths ----------------
FILES = {
    "BranchAndBound":  "CSV Files/BranchAndBoundAlgorithm.csv",
    "LinearRelaxation":  "CSV Files/LinearRelaxationAlgorithm.csv",
}

# metric column -> pretty title
METRICS = [
    ("migrations","Migrations"),
]

STYLES = {
    "FirstFit":       dict(linestyle=(0,(3,1,1,1)),   marker="o", linewidth=2),
    "MostFull":       dict(linestyle="--",  marker="s", linewidth=2),
    "LeastFull":      dict(linestyle="-.",  marker="*", linewidth=2),
    "BranchAndBound": dict(linestyle= "-", marker="x", linewidth=2),
}

IMAGES_DIR = "images"
TABLES_DIR = "tables"

# ---- global style settings ----
plt.rcParams["font.family"] = "Times New Roman"
plt.rcParams["font.size"] = 16              # base font size
plt.rcParams["axes.titleweight"] = "bold"   # make all titles bold
plt.rcParams["axes.labelsize"] = 18         # axis labels
plt.rcParams["axes.titlesize"] = 20         # plot titles
plt.rcParams["legend.fontsize"] = 15        # legend text
plt.rcParams["xtick.labelsize"] = 16        # x-axis numbers
plt.rcParams["ytick.labelsize"] = 16        # y-axis numbers
plt.rcParams["grid.linewidth"] = 0.7


def load_cols(path, algo, needed_cols):
    df = pd.read_csv(path)
    missing = [c for c in (["numVMs"] + needed_cols) if c not in df.columns]
    if missing:
        raise ValueError(f"{path} missing columns: {missing}")
    df["algo"] = algo
    # make sure numeric
    for col in ["numVMs"] + needed_cols:
        df[col] = pd.to_numeric(df[col], errors="coerce")
    df = df.dropna(subset=["numVMs"] + needed_cols)
    return df[["numVMs", *needed_cols, "algo"]]

def plot_metric(col, title):
    os.makedirs(IMAGES_DIR, exist_ok=True)
    plt.figure(figsize=(10, 6))
    any_plotted = False
    all_ticks = set()

    for algo, fname in FILES.items():
        if not os.path.exists(fname):
            print(f"[skip] {fname} not found")
            continue

        df = load_cols(fname, algo, [col])
        agg = df.groupby("numVMs", as_index=False)[col].mean()

        style = STYLES.get(algo, dict(linestyle="-", marker="o", linewidth=2))
        plt.plot(agg["numVMs"], agg[col], label=algo, **style)

        all_ticks.update(agg["numVMs"].tolist())
        any_plotted = True

    if not any_plotted:
        print("No CSVs found; nothing to plot.")
        plt.close()
        return

    ticks = sorted(all_ticks)
    if ticks:
        plt.xticks(ticks, [str(int(t)) for t in ticks])  # force integer tick labels

    plt.title(f"{title} vs #VMs (over 4 Hosts)")
    plt.xlabel("#VMs")
    plt.ylabel(f"{title}")
    plt.grid(True, alpha=0.5)
    plt.legend(frameon=True)
    plt.tight_layout()
    out_path1 = os.path.join(IMAGES_DIR, f"{title}.png")
    plt.savefig(out_path1, format='png', dpi=600, bbox_inches="tight")

    out_path2 = os.path.join(IMAGES_DIR, f"{title}.pdf")
    plt.savefig(out_path2, format='pdf', dpi=600, bbox_inches="tight")

    print(f"Saved {out_path1}")
    print(f"Saved {out_path2}")

    plt.close()

def make_summary_table():
    os.makedirs(TABLES_DIR, exist_ok=True)

    frames = []
    needed = [m[0] for m in METRICS]  
    for algo, fname in FILES.items():
        if not os.path.exists(fname):
            print(f"[skip] {fname} not found")
            continue
        df = load_cols(fname, algo, needed)
        frames.append(df)

    if not frames:
        print("No data found for summary table.")
        return

    all_df = pd.concat(frames, ignore_index=True)

    # mean per (numVMs, algo)
    summary = (
        all_df
        .groupby(["numVMs", "algo"], as_index=False)[needed]
        .mean()
        .sort_values(["numVMs", "algo"])
    )

    summary_rounded = summary.copy()
    for col, _ in METRICS:
        summary_rounded[col] = summary_rounded[col].round(2)

    csv_path = os.path.join(TABLES_DIR, "mig_summary.csv")
    summary_rounded.to_csv(csv_path, index=False)
    print(f"Saved {csv_path}")

    md_path = os.path.join(TABLES_DIR, "mig_summary.md")
    with open(md_path, "w", encoding="utf-8") as f:
        for v in sorted(summary_rounded["numVMs"].unique()):
            sub = summary_rounded[summary_rounded["numVMs"] == v]
            f.write(f"### numVMs = {int(v)}\n\n")
            f.write(
                sub[["algo"] + [m[0] for m in METRICS]]
                .rename(columns={
                    "algo": "Algorithm",
                    "migrationRate": "MigRate"
                })
                .to_markdown(index=False)
            )
            f.write("\n\n")
    print(f"Saved {md_path}")

if __name__ == "__main__":
    for col, title in METRICS:
        plot_metric(col, title)
    make_summary_table()
