package org.cloudbus.cloudsim.examples;
import scpsolver.constraints.*;
import scpsolver.lpsolver.*;
import scpsolver.problems.LinearProgram;

public class ProblemModeled {
    public static void solveProblem(double[] C, double[] M, double[] N, double[] D, double[] c, double[] m, double[] n, double[] d, int numHosts, int numVMs) {

        // 1. Set objective function (maximize number of allocated VMs)
        // Variables are ordered as: x_11, x_12, ..., x_1n, x_21, x_22, ..., x_2n, ..., x_m1, x_m2, ..., x_mn
        int numVars = numHosts * numVMs; // Total number of variables (for x_ij)
        double[] objFun = new double[numVars];
        for (int i = 0; i < numVars; i++) {
            objFun[i] = 1.0;
        }
        LinearProgram lp = new LinearProgram(objFun);

        // 2. Host Resources Constraints.
        for(int i = 0; i < numHosts; i++) {
            // 1. CPU Constraint: ∑j c_j * x_ij <= C_i
            double[] cpuConstraint = new double[numVars];
            for(int j = 0; j < numVMs; j++) {
                int index =  i * numVMs + j;
                cpuConstraint[index] = c[j];
            }
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(cpuConstraint, C[i], "cpu_host_" + i));

            // 2. RAM Constraint: ∑j m_j * x_ij <= M_i
            double[] ramConstraint = new double[numVars];
            for(int j = 0; j < numVMs; j++) {
                int index =  i * numVMs + j;
                ramConstraint[index] = m[j];
            }
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(ramConstraint, M[i], "ram_host_" + i));

            // 3. Network Bandwidth Constraint: ∑j n_j * x_ij <= N_i
            double[] bwConstraint = new double[numVars];
            for(int j = 0; j < numVMs; j++) {
                int index =  i * numVMs + j;
                bwConstraint[index] = n[j];
            }
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(bwConstraint, N[i], "network_host_" + i));

            // 4. Disk Constraint: ∑j d_j * x_ij <= D_i
            double[] diskConstraint = new double[numVars];
            for(int j = 0; j < numVMs; j++) {
                int index =  i * numVMs + j;
                diskConstraint[index] = d[j];
            }
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(diskConstraint, D[i], "disk_host_" + i));
        }

        // 3. VM Assignment Constraints (each VM assigned to at most one host).
        // For each VM j: ∑i x_ij <= 1
        for (int j = 0; j < numVMs; j++) {
            double[] assignmentConstraint = new double[numVars];
            for (int i = 0; i < numHosts; i++) {
                int varIndex = i * numVMs + j;
                assignmentConstraint[varIndex] = 1.0;
            }
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(assignmentConstraint, 1.0, "vm_assignment_" + j));
        }

        // 4. Set binary constraints (0 <= x_ij <= 1, integer)
        for (int i = 0; i < numVars; i++) {
            lp.setBinary(i);
        }

        // 5. Solve the problem
        LinearProgramSolver solver = SolverFactory.newDefault();
        double[] solution = solver.solve(lp);

        // 6. Print results
        if (solution != null) {
            System.out.println("\n=================================================================\n");
            System.out.println("Optimal solution found!");
            System.out.println("Total VMs allocated: " + (int)lp.evaluate(solution));

            System.out.println("\nVM-Host assignments:");
            int totalAllocated = 0;
            for (int i = 0; i < numHosts; i++) {
                System.out.println("Host " + (i+1) + ":");
                for (int j = 0; j < numVMs; j++) {
                    int varIndex = i * numVMs + j;
                    if (solution[varIndex] >= 0.5) {  // Binary variable, check if assigned
                        System.out.println("  VM " + (j+1) + " assigned");
                        totalAllocated++;
                    }
                }
            }

            // Print resource utilization per host
            System.out.println("\nResource utilization per host:");
            for (int i = 0; i < numHosts; i++) {
                double cpuUsed = 0, ramUsed = 0, netUsed = 0, diskUsed = 0;
                for (int j = 0; j < numVMs; j++) {
                    int varIndex = i * numVMs + j;
                    if (solution[varIndex] >= 0.5) {
                        cpuUsed += c[j];
                        ramUsed += m[j];
                        netUsed += n[j];
                        diskUsed += d[j];
                    }
                }
                System.out.printf("Host %d: CPU: %.1f/%.1f, RAM: %.1f/%.1f, Network: %.1f/%.1f, Disk: %.1f/%.1f%n",
                        i+1, cpuUsed, C[i], ramUsed, M[i], netUsed, N[i], diskUsed, D[i]);
            }

            System.out.println("\nUnallocated VMs:");
            for (int j = 0; j < numVMs; j++) {
                boolean allocated = false;
                for (int i = 0; i < numHosts; i++) {
                    int varIndex = i * numVMs + j;
                    if (solution[varIndex] >= 0.5) {
                        allocated = true;
                        break;
                    }
                }
                if (!allocated) {
                    System.out.printf("  VM %d (CPU: %.1f, RAM: %.1f, Network: %.1f, Disk: %.1f)%n",
                            j+1, c[j], m[j], n[j], d[j]);
                }
            }

        } else {
            System.out.println("No feasible solution found!");
        }


    }

    public static void main(String[] args) {
        // Data
        int numHosts = 6;
        int numVMs = 20;

        // Host Resources.
        double[] C = {80, 60, 50, 90, 30, 65};
        double[] M = {12, 10, 9, 16, 6, 11};
        double[] N = {600, 500, 400, 700, 350, 550};
        double[] D = {250, 200, 180, 300, 150, 230};

        // VM Required Resources.
        double[] c = {35, 40, 30, 45, 36, 50, 33, 38, 42, 31, 22, 25, 27, 20, 23, 28, 26, 24, 22, 25};
        double[] m = {6, 8, 5, 9, 7, 10, 6, 7, 8, 5, 4, 5, 4, 3, 4, 5, 4, 3, 3, 4};
        double[] n = {280, 300, 250, 320, 290, 350, 260, 275, 300, 240, 190, 200, 210, 180, 190, 220, 210, 200, 180, 190};
        double[] d = {110, 130, 100, 140, 115, 150, 100, 120, 135, 95, 85, 90, 95, 80, 85, 100, 90, 85, 80, 90};

        // Solve the problem
        solveProblem(C, M, N, D, c, m, n, d, numHosts, numVMs);
    }

}
