import os
import pandas as pd
import matplotlib.pyplot as plt

FILES = {
    "FirstFit":  "CSV Files/SelectionPolicyFirstFit.csv",
    "MostFull":  "CSV Files/SelectionPolicyMostFull.csv",
    "LeastFull": "CSV Files/SelectionPolicyLeastFull.csv",
    "Random":    "CSV Files/SelectionPolicyRandomSelection.csv",
    "SCPSolver": "CSV Files/SCPSolver.csv",
}

METRICS = [
    ("allocRate", "Allocation Rate"),
    ("cpuUtilRate",  "CPU Utilization Rate"),
    ("ramUtilRate",  "RAM Utilization Rate"),
    ("netUtilRate",  "Network Utilization Rate"),
    ("diskUtilRate", "Disk Utilization Rate"),
]

def load_cols(path, algo, needed_cols):
    df = pd.read_csv(path)
    missing = [c for c in (["numVMs"] + needed_cols) if c not in df.columns]
    if missing:
        raise ValueError(f"{path} missing columns: {missing}")
    df["algo"] = algo
    return df[["numVMs", *needed_cols, "algo"]]

def plot_metric(col, title):
    plt.figure(figsize=(10, 6))
    any_plotted = False
    all_ticks = set()

    for algo, fname in FILES.items():
        if not os.path.exists(fname):
            print(f"[skip] {fname} not found")
            continue
        df = load_cols(fname, algo, [col])
        agg = df.groupby("numVMs", as_index=False)[col].mean()
        agg["numVMs"] = agg["numVMs"].astype(int)
        plt.plot(agg["numVMs"], agg[col], marker="o", label=algo)
        all_ticks.update(agg["numVMs"].tolist())
        any_plotted = True

    if not any_plotted:
        print("No CSVs found; nothing to plot.")
        plt.close()
        return

    # Force nice ticks
    XTICKS = list(range(3, 22, 3))
    # YTICKS = list(range(0, 101, 10))
    ticks = sorted(all_ticks) or XTICKS
    plt.xticks(ticks, [str(t) for t in ticks])
    # plt.yticks(YTICKS, [str(t) for t in YTICKS])

    plt.title(f"{title} vs #VMs (Fixed Hosts)")
    plt.xlabel("#VMs")
    plt.ylabel(f"{title}%")
    plt.grid(True, alpha=0.3)
    plt.legend(title="Algorithm")
    plt.tight_layout()
    os.makedirs("images", exist_ok=True)
    plt.savefig(os.path.join("images", f"{title}.png"), dpi=150, bbox_inches="tight")
    print(f"Saved {f"{title}.png"}")
    plt.close()

if __name__ == "__main__":
    for col, title in METRICS:
        plot_metric(col, title)
