# CloudSim Algorithms Comparison

A CloudSim-based simulation project to compare VM allocation algorithms over Hosts (FirstFit, MostFull, LeastFull, Random) against an SCPSolver via Monte Carlo analysis.

## Prerequisites

- IntelliJ IDEA
- Java Development Kit (JDK)
- Python (for graph generation)
- [SCPSolver library](http://scpsolver.org/) (SCPSolver.jar & GLPKSolverPack.jar)

## Setup Instructions

### 1. Clone and Import Project

1. Clone this repository to your local machine.
2. Open IntelliJ IDEA.
3. Import the project by selecting "Open" and choosing the project directory.

### 2. Configure Dependencies

1. Download the SCPSolver.jar & GLPKSolverPack.jar from [SCPSolver library](http://scpsolver.org/).
2. Add both to your project structure inside the following paths in IntelliJ IDEA:
   - Go to **File** → **Project Structure** → **Libraries**.
   - Go to **File** → **Project Structure** → **Modules**  → **cloudsim-examples**.
   
## Running the Simulation

### Algorithm Comparison Simulation

1. Navigate to the following file in IntelliJ:
   ```
   cloudsim/modules/cloudsim-examples/src/main/java/org.cloudbus.cloudsim.examples.AlgorithmsComparison.java
   ```

2. **Customization Options (if you want)**
   - Number of VMs.
   - Number of Hosts.
   - Monte Carlo iterations.
   - Hardware specifications.
   - Other simulation parameters.
  
4. Run the `AlgorithmsComparison.java` file.

5. **Output:**
   - Console output showing average number of allocated VMs.
   - Results automatically saved as CSV files in `results/CSV Files/`.

## Generating Graphs

### 1. Set up Python Environment in VS Code (First Time Only)

1. Open the `results` folder in **VS Code**
2. Open terminal.
3. Create and activate a virtual environment, then install required packages:

      **Windows:**
      ```bash
      py -m venv .venv
      .\.venv\Scripts\activate
      python -m pip install pandas matplotlib
      ```
      
      **macOS/Linux:**
      ```bash
      python3 -m venv .venv
      source .venv/bin/activate
      python -m pip install pandas matplotlib
      ```

### 2. Generate Graphs

1. In VS Code, ensure your virtual environment is activated in the terminal.
2. After running the simulation and generating CSV files, run ```graph.py```

   ``` bash
   python graph.py
   ```
3. Generated graphs will be saved in `results/images/`

## Project Structure

```
├── cloudsim/
│   └── modules/
│       └── cloudsim-examples/
│           └── src/main/java/
│               └── org.cloudbus.cloudsim.examples/
│                   └── AlgorithmsComparison.java
├── results/
│   ├── CSV Files/          # Generated simulation data
│   ├── images/             # Generated graphs
│   └── graph.py           # Python script for visualization
└── README.md
```

## Usage Tips

- Modify simulation parameters in `AlgorithmsComparison.java` to experiment with different scenarios.
- If SCPSolver library is not found, verify it's properly added to the project structure.
- For graph generation issues, check that Python environment is activated and the required libraries are installed.
