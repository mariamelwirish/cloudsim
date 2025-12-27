import os
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

# ---------------- paths ----------------
FILES = {
    "FirstFit":        "CSV Files/SelectionPolicyFirstFit.csv",
    "MostFull":        "CSV Files/SelectionPolicyMostFull.csv",
    "LeastFull":       "CSV Files/SelectionPolicyLeastFull.csv",
    "BranchAndCut":  "CSV Files/BranchAndBoundAlgorithm.csv",
    "LinearRelaxation":  "CSV Files/LinearRelaxationAlgorithm.csv",
    "DRF-Scarcity":  "CSV Files/DRFScarcityAlgorithm.csv",
    # "DRF-Scarcity-W/O-Sorting":  "CSV Files/DRFScarcityAlgorithmTwo.csv",
}

# metric column 
METRICS = [
    ("allocRate",  "Allocation Rate"),
    ("cpuUtilRate","CPU Utilization Rate"),
    ("ramUtilRate","RAM Utilization Rate"),
    ("netUtilRate","Network Utilization Rate"),
    ("diskUtilRate","Disk Utilization Rate"),
    ("migrationRate","Migration Rate"),
    ("migrations","Migrations"),
]

STYLES = {
    "FirstFit":       dict(linestyle=(0,(3,1,1,1)),   marker="x", linewidth=2),
    "MostFull":       dict(linestyle="--",  marker="s", linewidth=2),
    "LeastFull":      dict(linestyle="-.",  marker="*", linewidth=2),
    "BranchAndBound": dict(linestyle= "-", marker="o", linewidth=2),
    "LinearRelaxation": dict(linestyle= "-", marker="*", linewidth=2),
    "DRF-Scarcity": dict(linestyle= "-", marker="s", linewidth=2),
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


def load_cols(path, algo, needed_cols, strict=True):
    df = pd.read_csv(path)
    required = ["numVMs"] + needed_cols
    missing = [c for c in required if c not in df.columns]
    if missing:
        if strict:
            raise ValueError(f"{path} missing columns: {missing}")
        else:
            # keep only available columns
            available_needed = [c for c in needed_cols if c in df.columns]
            if not available_needed:
                # nothing usable beyond numVMs
                return None
            needed_cols = available_needed
    df["algo"] = algo
    # make sure numeric for available cols
    for col in ["numVMs"] + needed_cols:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")
    df = df.dropna(subset=[c for c in ["numVMs"] + needed_cols if c in df.columns])
    return df[["numVMs", *needed_cols, "algo"]]

def plot_metric(col, title):
    os.makedirs(IMAGES_DIR, exist_ok=True)
    plt.figure(figsize=(10, 6))
    any_plotted = False
    all_ticks = set()

    # Use only specific algorithms for migration-related plots
    active_files = FILES
    if col in ["migrationRate", "migrations"]:
        active_files = {k: v for k, v in FILES.items() if k in ["BranchAndCut", "LinearRelaxation", "DRF-Scarcity"]}

    for algo, fname in active_files.items():
        if not os.path.exists(fname):
            print(f"[skip] {fname} not found")
            continue

        df = load_cols(fname, algo, [col])
        agg = df.groupby("numVMs", as_index=False)[col].mean()
        # Ensure points are ordered by numVMs for consistent plotting and smoothing
        agg = agg.sort_values("numVMs")

        # Apply rolling-average smoothing for migration plots with preserved endpoints
        y = agg[col]
        if col in ["migrationRate", "migrations"]:
            w = 3
            y = (y.rolling(window=w, center=True, min_periods=w).mean()).fillna(y)
            # Keep edges unchanged: replace missing smoothed points with original values
            # y = y_roll.fillna(y)

        style = STYLES.get(algo, dict(linestyle="-", marker="o", linewidth=2))
        plt.plot(agg["numVMs"], y, label=algo, **style)

        all_ticks.update(agg["numVMs"].tolist())
        any_plotted = True

    if not any_plotted:
        print("No CSVs found; nothing to plot.")
        plt.close()
        return


    ticks = sorted(all_ticks)
    if ticks:
        xmin, xmax = min(ticks), max(ticks)

        step = 6
        xticks = np.arange(xmin, xmax + step, step)

        plt.xticks(xticks, [str(int(t)) for t in xticks])


    plt.title(f"{title} vs #VMs (over 3 Hosts)")
    plt.xlabel("#VMs")
    # Use percent sign for rates only; raw count for migrations
    if col == "migrations":
        plt.ylabel(title)
    else:
        plt.ylabel(f"{title} (%)")
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

    # Define metric subsets
    non_migration_metrics = [
        "allocRate", "cpuUtilRate", "ramUtilRate", "netUtilRate", "diskUtilRate"
    ]
    migration_metrics = ["migrationRate", "migrations"]

    # Build non-migration summary 
    non_frames = []
    for algo, fname in FILES.items():
        if not os.path.exists(fname):
            print(f"[skip] {fname} not found")
            continue
        df = load_cols(fname, algo, non_migration_metrics, strict=False)
        if df is None:
            print(f"[skip] {fname} has none of non-migration metrics")
            continue
        non_frames.append(df)

    if non_frames:
        non_all = pd.concat(non_frames, ignore_index=True)
        non_available = [c for c in non_migration_metrics if c in non_all.columns]
        non_summary = (
            non_all
            .groupby(["numVMs", "algo"], as_index=False)[non_available]
            .mean()
            .sort_values(["numVMs", "algo"])
        )

        # Round available non-migration metrics
        for c in non_available:
            non_summary[c] = non_summary[c].round(2)

        # Write CSV and MD for non-migration metrics only
        csv_path = os.path.join(TABLES_DIR, "summary_by_numVMs_and_algo.csv")
        non_summary.to_csv(csv_path, index=False)
        print(f"Saved {csv_path}")

        md_path = os.path.join(TABLES_DIR, "summary_by_numVMs_and_algo.md")
        with open(md_path, "w", encoding="utf-8") as f:
            for v in sorted(non_summary["numVMs"].unique()):
                sub = non_summary[non_summary["numVMs"] == v]
                f.write(f"### numVMs = {int(v)}\n\n")
                rename_map = {
                    "algo": "Algorithm",
                    "allocRate": "AllocRate(%)",
                    "cpuUtilRate": "CPU(%)",
                    "ramUtilRate": "RAM(%)",
                    "netUtilRate": "Net(%)",
                    "diskUtilRate": "Disk(%)",
                }
                cols = ["algo"] + non_available
                f.write(
                    sub[cols]
                    .rename(columns={k: v for k, v in rename_map.items() if k in cols})
                    .to_markdown(index=False)
                )
                f.write("\n\n")
        print(f"Saved {md_path}")
    else:
        print("No data found for non-migration summary table.")

    #  Build migration-only summary (mig_summary.md) 
    mig_frames = []
    for algo, fname in FILES.items():
        if not os.path.exists(fname):
            print(f"[skip] {fname} not found")
            continue
        df = load_cols(fname, algo, migration_metrics, strict=False)
        if df is None:
            print(f"[skip] {fname} has none of migration metrics")
            continue
        mig_frames.append(df)

    if mig_frames:
        mig_all = pd.concat(mig_frames, ignore_index=True)
        mig_available = [c for c in migration_metrics if c in mig_all.columns]
        mig_summary = (
            mig_all
            .groupby(["numVMs", "algo"], as_index=False)[mig_available]
            .mean()
            .sort_values(["numVMs", "algo"])
        )

        # Round rate column if present; keep migrations as integer-like
        if "migrationRate" in mig_available:
            mig_summary["migrationRate"] = mig_summary["migrationRate"].round(2)

        # Write CSV for migration summary
        csv_mig_path = os.path.join(TABLES_DIR, "mig_summary.csv")
        mig_summary.to_csv(csv_mig_path, index=False)
        print(f"Saved {csv_mig_path}")

        md_mig_path = os.path.join(TABLES_DIR, "mig_summary.md")
        with open(md_mig_path, "w", encoding="utf-8") as f:
            for v in sorted(mig_summary["numVMs"].unique()):
                sub = mig_summary[mig_summary["numVMs"] == v]
                f.write(f"### numVMs = {int(v)}\n\n")
                rename_map = {
                    "algo": "Algorithm",
                    "migrationRate": "MigRate(%)",
                    "migrations": "#Migrations",
                }
                cols = ["algo"] + mig_available
                f.write(
                    sub[cols]
                    .rename(columns={k: v for k, v in rename_map.items() if k in cols})
                    .to_markdown(index=False)
                )
                f.write("\n\n")
        print(f"Saved {md_mig_path}")
    else:
        print("No data found for migration summary table.")

if __name__ == "__main__":
    for col, title in METRICS:
        plot_metric(col, title)
    make_summary_table()
