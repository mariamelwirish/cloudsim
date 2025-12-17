package org.cloudbus.cloudsim.examples.algorithms;

import scpsolver.constraints.LinearSmallerThanEqualsConstraint;
import scpsolver.lpsolver.LinearProgramSolver;
import scpsolver.lpsolver.SolverFactory;
import scpsolver.problems.LinearProgram;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.cloudbus.cloudsim.examples.AlgorithmsComparison.flag;

public class BranchAndBoundAlgorithm {
    public BranchAndBoundAlgorithm(double[] C, double[] M, double[] N, double[] D, double[] c, double[] m, double[] n, double[] d, int numHosts, int numVMs) throws IOException {

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
        System.out.println("\nBranch and Bound: \n");
        LinearProgramSolver solver = SolverFactory.newDefault();
        solver.setTimeconstraint(20);
        double[] solution = solver.solve(lp);

        if (solution == null) {
            System.out.println("B&B did not finish within 20s. Falling back to Linear Relaxation...");
            new LinearRelaxationAlgorithm(C, M, N, D, c, m, n, d, numHosts, numVMs, true);
            return;
        }

        int num_allocated =  (int)lp.evaluate(solution);


        // 6. Print results

        // Calculating total resources for CSVWriter
        double cpuTotal = 0, cpuUsed = 0;
        long   ramTotal = 0, ramUsed = 0;
        long   bwTotal = 0, bwUsed = 0;
        long   diskTotal = 0, diskUsed = 0;

        for (int i = 0; i < numHosts; i++) {
            cpuTotal  += C[i];
            ramTotal  += (long) M[i];
            bwTotal  += (long) N[i];
            diskTotal += (long) D[i];
        }
        int migrations = 0;
        if (solution != null) {
            System.out.println("\n=================================================================\n");
            System.out.println("Optimal solution found!");
            System.out.println("Total VMs allocated: " + num_allocated);
            System.out.println("\nVM-Host assignments:");
            int totalAllocated = 0;
            for (int i = 0; i < numHosts; i++) {
                System.out.println("Host " + (i+1) + ":");
                for (int j = 0; j < numVMs; j++) {
                    int varIndex = i * numVMs + j;
                    if (solution[varIndex] >= 0.5) {  // Binary variable, check if assigned
                        System.out.println("  VM " + (j) + " assigned");
                        totalAllocated++;
                    }
                }
            }

            // Print resource utilization per host
            System.out.println("\nResource utilization per host:");
            for (int i = 0; i < numHosts; i++) {
                double cpu = 0, ram = 0, bw = 0, disk = 0;
                for (int j = 0; j < numVMs; j++) {
                    int varIndex = i * numVMs + j;
                    if (solution[varIndex] >= 0.5) {
                        cpu += c[j];
                        ram += m[j];
                        bw += n[j];
                        disk += d[j];
                    }
                }
                // Calculating total used sources for CSVWriter
                cpuUsed  += cpu;
                ramUsed  += (long) ram;
                bwUsed  += (long) bw;
                diskUsed += (long) disk;
                System.out.printf("Host %d: CPU: %.1f/%.1f, RAM: %.1f/%.1f, Network: %.1f/%.1f, Disk: %.1f/%.1f%n",
                        i, cpu, C[i], ram, M[i], bw, N[i], disk, D[i]);
            }

//            System.out.println("\nUnallocated VMs:");
//            for (int j = 0; j < numVMs; j++) {
//                boolean allocated = false;
//                for (int i = 0; i < numHosts; i++) {
//                    int varIndex = i * numVMs + j;
//                    if (solution[varIndex] >= 0.5) {
//                        allocated = true;
//                        break;
//                    }
//                }
//                if (!allocated) {
//                    System.out.printf("  VM %d (CPU: %.1f, RAM: %.1f, Network: %.1f, Disk: %.1f)%n",
//                            j, c[j], m[j], n[j], d[j]);
//                }
//            }

        } else {
            System.out.println("No feasible solution found!");
        }

        double migrationRate = (numVMs > 0) ? ((double) migrations / numVMs) * 100.0 : 0.0;
        System.out.println("Migrations: " + migrations + " out of " + numVMs + " VMs");
        System.out.println("Migration Rate: " + String.format("%.2f%%", migrationRate));

        final String file = "BranchAndBoundAlgorithm.csv";
        Path RESULTS_DIR = Paths.get("../results/CSV Files/");
        java.nio.file.Files.createDirectories(RESULTS_DIR);
        Path outFile = RESULTS_DIR.resolve("BranchAndBoundAlgorithm.csv");

        try (com.opencsv.CSVWriter w = new com.opencsv.CSVWriter(new java.io.FileWriter(outFile.toFile(), flag))) {
            boolean header =  java.nio.file.Files.notExists(outFile) || java.nio.file.Files.size(outFile) == 0;
            if (header) {
                w.writeNext(new String[]{
                        "placedVMs","numVMs",
                        "allocRate",
                        "cpuUtilRate",
                        "ramUtilRate",
                        "netUtilRate",
                        "diskUtilRate",
                        "migrations",
                        "migrationRate"
                });
            }
            w.writeNext(new String[]{
                    String.valueOf(num_allocated),
                    String.valueOf(numVMs),
                    String.valueOf((float) num_allocated / (float) numVMs * 100.0),
                    String.valueOf((double)cpuUsed / (double)cpuTotal * 100.0),
                    String.valueOf((float) ramUsed / ramTotal * 100.0),
                    String.valueOf((float) bwUsed / (float) bwTotal * 100.0),
                    String.valueOf((float) diskUsed / (float) diskTotal * 100.0),
                    String.valueOf(migrations),
                    String.valueOf(migrationRate)
            });
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
    public static void main(String[] args) throws IOException {
//        LinearProgramSolver solver = SolverFactory.newDefault();
//        solver.setTimeconstraint(10);
//        System.out.println(solver.getTimeconstraint());
    }
}
