import os
import pandas as pd
import matplotlib.pyplot as plt


FILES = {
    "FirstFit":  "SelectionPolicyFirstFit.csv",
    "MostFull":  "SelectionPolicyMostFull.csv",
    "LeastFull": "SelectionPolicyLeastFull.csv",
    "Random":    "SelectionPolicyRandomSelection.csv",
    "SCPSolver": "SCPSolver.csv",
}

def load_and_prepare(path, algo):
    df = pd.read_csv(path)

    if "placedVMs" not in df.columns:
        raise ValueError(f"{path} must contain a 'placedVMs' column")

    if "numVMs" not in df.columns:
        raise ValueError(f"{path} must contain a 'numVMs' column")
    
    if "allocRate" not in df.columns:
        raise ValueError(f"{path} must contain a 'numVMs' column")

    df["algo"] = algo
    return df[["placedVMs", "numVMs", "allocRate", "algo"]]

def main():
    plt.figure(figsize=(10, 6))
    any_plotted = False

    for algo, fname in FILES.items():
        if not os.path.exists(fname):
            print(f"[skip] {fname} not found")
            continue

        df = load_and_prepare(fname, algo)
        agg = df.groupby("numVMs", as_index=False)["allocRate"].mean()
        plt.plot(agg["numVMs"], agg["allocRate"], marker="o", label=algo)

        any_plotted = True

    if not any_plotted:
        print("No CSVs found; nothing to plot.")
        return

    ticks = list(range(3, 22, 3))
    plt.xticks(ticks, [str(t) for t in ticks])
    plt.title("Allocation Rate vs #VMs (Fixed Hosts)")
    plt.xlabel("#VMs")
    plt.ylabel("Allocation Rate (%)")
    plt.grid(True, alpha=0.3)
    plt.legend(title="Algorithm")
    plt.tight_layout()
    plt.savefig("allocation_rate_vs_numVMs.png", dpi=150)
    print("Saved allocation_rate_vs_numVMs.png")

if __name__ == "__main__":
    main()

