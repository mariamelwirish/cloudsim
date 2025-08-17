package org.cloudbus.cloudsim.examples;

import scpsolver.constraints.*;
import scpsolver.lpsolver.*;
import scpsolver.problems.LinearProgram;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.Arrays;

public class testcode {

    // ====== CONFIGURABLE MONTE CARLO SETTINGS ======
    static final int NUM_HOSTS = 5;
    static final int NUM_VMS = 25;
    static final int MONTE_CARLO_ITERS = 100;
    static final long RNG_SEED = 42L; // set to System.nanoTime() if you want non-reproducible runs
    static final boolean WRITE_CSV = true;
    static final String CSV_PATH = "mc_results.csv";

    // ====== REALISTIC RANGES (units: vCPU, GiB, Mb/s, GiB) ======
    // Hosts
    static final int HOST_CPU_MIN = 32, HOST_CPU_MAX = 96;          // vCPU
    static final int HOST_RAM_MIN = 128, HOST_RAM_MAX = 768;        // GiB
    static final int HOST_NET_MIN = 10_000, HOST_NET_MAX = 25_000;  // Mb/s (10–25 GbE)
    static final int HOST_DISK_MIN = 4_000, HOST_DISK_MAX = 16_000; // GiB (≈4–16 TB)

    // VMs
    static final int VM_CPU_MIN = 1, VM_CPU_MAX = 16;               // vCPU (t3/m5 style)
    static final int VM_RAM_MIN = 2, VM_RAM_MAX = 64;               // GiB
    static final int VM_NET_MIN = 100, VM_NET_MAX = 5_000;          // Mb/s
    static final int VM_DISK_MIN = 20, VM_DISK_MAX = 500;           // GiB

    static class SolveResult {
        int allocatedCount;
        double[] solution; // binary x_ij flattened
    }

    public static SolveResult solveProblem(double[] C, double[] M, double[] N, double[] D,
                                           double[] c, double[] m, double[] n, double[] d,
                                           int numHosts, int numVMs) {

        int numVars = numHosts * numVMs;
        double[] objFun = new double[numVars];
        Arrays.fill(objFun, 1.0);
        LinearProgram lp = new LinearProgram(objFun);

        // Host capacity constraints
        for (int i = 0; i < numHosts; i++) {
            double[] cpuCons = new double[numVars];
            double[] ramCons = new double[numVars];
            double[] netCons = new double[numVars];
            double[] dskCons = new double[numVars];

            for (int j = 0; j < numVMs; j++) {
                int idx = i * numVMs + j;
                cpuCons[idx] = c[j];
                ramCons[idx] = m[j];
                netCons[idx] = n[j];
                dskCons[idx] = d[j];
            }
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(cpuCons, C[i], "cpu_host_" + i));
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(ramCons, M[i], "ram_host_" + i));
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(netCons, N[i], "net_host_" + i));
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(dskCons, D[i], "disk_host_" + i));
        }

        // Each VM assigned to at most one host
        for (int j = 0; j < numVMs; j++) {
            double[] cons = new double[numVars];
            for (int i = 0; i < numHosts; i++) cons[i * numVMs + j] = 1.0;
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(cons, 1.0, "vm_assign_" + j));
        }

        // Binary variables
        for (int i = 0; i < numVars; i++) lp.setBinary(i);

        LinearProgramSolver solver = SolverFactory.newDefault();
        double[] solution = solver.solve(lp);

        SolveResult res = new SolveResult();
        if (solution == null) {
            res.allocatedCount = 0;
            res.solution = null;
            return res;
        }

        int allocated = (int) lp.evaluate(solution);
        res.allocatedCount = allocated;
        res.solution = solution;
        return res;
    }

    // Generate random host capacities and VM demands within ranges
    static void genRandomScenario(Random rng,
                                  int numHosts, int numVMs,
                                  double[] C, double[] M, double[] N, double[] D,
                                  double[] c, double[] m, double[] n, double[] d) {

        // Hosts
        for (int i = 0; i < numHosts; i++) {
            C[i] = randInt(rng, HOST_CPU_MIN, HOST_CPU_MAX);
            M[i] = randInt(rng, HOST_RAM_MIN, HOST_RAM_MAX);
            N[i] = randInt(rng, HOST_NET_MIN, HOST_NET_MAX);
            D[i] = randInt(rng, HOST_DISK_MIN, HOST_DISK_MAX);
        }

        // VMs
        for (int j = 0; j < numVMs; j++) {
            c[j] = randInt(rng, VM_CPU_MIN, VM_CPU_MAX);
            m[j] = randInt(rng, VM_RAM_MIN, VM_RAM_MAX);
            n[j] = randInt(rng, VM_NET_MIN, VM_NET_MAX);
            d[j] = randInt(rng, VM_DISK_MIN, VM_DISK_MAX);
        }
    }

    static int randInt(Random rng, int minInclusive, int maxInclusive) {
        return minInclusive + rng.nextInt(maxInclusive - minInclusive + 1);
    }

    public static void main(String[] args) {
        Random rng = new Random(RNG_SEED);

        // Arrays reused per iteration
        double[] C = new double[NUM_HOSTS];
        double[] M = new double[NUM_HOSTS];
        double[] N = new double[NUM_HOSTS];
        double[] D = new double[NUM_HOSTS];

        double[] c = new double[NUM_VMS];
        double[] m = new double[NUM_VMS];
        double[] n = new double[NUM_VMS];
        double[] d = new double[NUM_VMS];

        int[] perRunAllocated = new int[MONTE_CARLO_ITERS];

        // Optional CSV header
        FileWriter fw = null;
        try {
            if (WRITE_CSV) {
                fw = new FileWriter(CSV_PATH);
                fw.write("iter,allocated_vms,total_hosts,total_vms\n");
            }

            for (int iter = 0; iter < MONTE_CARLO_ITERS; iter++) {
                genRandomScenario(rng, NUM_HOSTS, NUM_VMS, C, M, N, D, c, m, n, d);

                SolveResult r = solveProblem(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
                perRunAllocated[iter] = r.allocatedCount;

                if (WRITE_CSV) {
                    fw.write((iter + 1) + "," + r.allocatedCount + "," + NUM_HOSTS + "," + NUM_VMS + "\n");
                }

                // Optional: print a tiny per-iter summary (comment out if too verbose)
                System.out.printf("Run %3d: Allocated %2d / %2d VMs%n", iter + 1, r.allocatedCount, NUM_VMS);
            }

        } catch (IOException e) {
            System.err.println("CSV write error: " + e.getMessage());
        } finally {
            if (fw != null) try { fw.close(); } catch (IOException ignored) {}
        }

        // Compute and print the average
        double sum = 0;
        int max = Integer.MIN_VALUE, min = Integer.MAX_VALUE;
        for (int v : perRunAllocated) { sum += v; max = Math.max(max, v); min = Math.min(min, v); }
        double avg = sum / MONTE_CARLO_ITERS;

        System.out.println("\n==============================================================");
        System.out.printf("Monte Carlo runs: %d, Hosts: %d, VMs: %d%n", MONTE_CARLO_ITERS, NUM_HOSTS, NUM_VMS);
        System.out.printf("Average allocated VMs: %.2f%n", avg);
        System.out.printf("Best run (max allocated): %d%n", max);
        System.out.printf("Worst run (min allocated): %d%n", min);
        if (WRITE_CSV) {
            System.out.println("Per-run results saved to: " + CSV_PATH);
        }
    }
}
