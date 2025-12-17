package org.cloudbus.cloudsim.examples.algorithms;

import org.cloudbus.cloudsim.examples.AlgorithmsComparison;
import scpsolver.constraints.LinearSmallerThanEqualsConstraint;
import scpsolver.lpsolver.LinearProgramSolver;
import scpsolver.lpsolver.SolverFactory;
import scpsolver.problems.LinearProgram;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.cloudbus.cloudsim.examples.AlgorithmsComparison.flag;

public class LinearRelaxationAlgorithm {

    public LinearRelaxationAlgorithm(double[] C, double[] M, double[] N, double[] D, double[] c, double[] m, double[] n, double[] d, int numHosts, int numVMs, boolean bab) throws IOException {

        // 1. Set objective function (maximize number of allocated VMs)
        // Variables are ordered as: x_11, x_12, ..., x_1n, x_21, x_22, ..., x_2n, ..., x_m1, x_m2, ..., x_mn
        int numVars = numHosts * numVMs; // Total number of variables (for x_ij)
        double[] objFun = new double[numVars];
        for (int i = 0; i < numVars; i++) {
            objFun[i] = 1.0;
        }
        LinearProgram lp = new LinearProgram(objFun);

        // 2. Host Resources Constraints.
        for (int i = 0; i < numHosts; i++) {
            // 1. CPU Constraint: ∑j c_j * x_ij <= C_i
            double[] cpuConstraint = new double[numVars];
            for (int j = 0; j < numVMs; j++) {
                int index = i * numVMs + j;
                cpuConstraint[index] = c[j];
            }
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(cpuConstraint, C[i], "cpu_host_" + i));

            // 2. RAM Constraint: ∑j m_j * x_ij <= M_i
            double[] ramConstraint = new double[numVars];
            for (int j = 0; j < numVMs; j++) {
                int index = i * numVMs + j;
                ramConstraint[index] = m[j];
            }
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(ramConstraint, M[i], "ram_host_" + i));

            // 3. Network Bandwidth Constraint: ∑j n_j * x_ij <= N_i
            double[] bwConstraint = new double[numVars];
            for (int j = 0; j < numVMs; j++) {
                int index = i * numVMs + j;
                bwConstraint[index] = n[j];
            }
            lp.addConstraint(new LinearSmallerThanEqualsConstraint(bwConstraint, N[i], "network_host_" + i));

            // 4. Disk Constraint: ∑j d_j * x_ij <= D_i
            double[] diskConstraint = new double[numVars];
            for (int j = 0; j < numVMs; j++) {
                int index = i * numVMs + j;
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

        // 4. Relax (0 <= x_ij <= 1)
        double[] lowerBounds = new double[numVars];
        double[] upperBounds = new double[numVars];

        for (int i = 0; i < numVars; i++) {
            lowerBounds[i] = 0.0;
            upperBounds[i] = 1.0;
        }

        lp.setLowerbound(lowerBounds);
        lp.setUpperbound(upperBounds);

        // 5. Solve the problem
        System.out.println("\nLinear Relaxation: \n");
        LinearProgramSolver solver = SolverFactory.newDefault();
        double[] solution = solver.solve(lp);

        int num_allocated = 0;

        // Calculating total resources for CSVWriter
        double cpuTotal = 0, cpuUsed = 0;
        long ramTotal = 0, ramUsed = 0;
        long bwTotal = 0, bwUsed = 0;
        long diskTotal = 0, diskUsed = 0;
        for (int i = 0; i < numHosts; i++) {
            cpuTotal += C[i];
            ramTotal += (long) M[i];
            bwTotal += (long) N[i];
            diskTotal += (long) D[i];
        }

        // Track actual resource usage per host
        double[] hostCpuUsed = new double[numHosts];
        double[] hostRamUsed = new double[numHosts];
        double[] hostBwUsed = new double[numHosts];
        double[] hostDiskUsed = new double[numHosts];
        for (int i = 0; i < numHosts; i++) {
            hostCpuUsed[i] = 0.0;
            hostRamUsed[i] = 0.0;
            hostBwUsed[i] = 0.0;
            hostDiskUsed[i] = 0.0;
        }

        // To track the unallocated VMs for Phase 2
        boolean[] allocated = new boolean[numVMs];
        for(int i = 0; i < numVMs; i++){
            allocated[i] = false;
        }

        int migrations = 0;
        // Phase 1: Process LP solution where x_ij > 0.5 (rounding)
//        System.out.println("Phase 1: Processing LP solution (rounding > 0.5):");
//        for (int i = 0; i < numHosts; i++) {
//            System.out.println("Host " + (i + 1) + ":");
//            for (int j = 0; j < numVMs; j++) {
//                int varIndex = i * numVMs + j;
//                // Check if the solution suggests this assignment && VM not already allocated
//                if (solution[varIndex] > 0.5 && !allocated[j]) {
//                    // Check if adding this VM would exceed capacity
//                    if (hostCpuUsed[i] + c[j] <= C[i] && hostRamUsed[i] + m[j] <= M[i] && hostBwUsed[i] + n[j] <= N[i] && hostDiskUsed[i] + d[j] <= D[i]) {
//
//                        // Allocate the VM
//                        allocated[j] = true;
//                        hostCpuUsed[i] += c[j];
//                        hostRamUsed[i] += m[j];
//                        hostBwUsed[i] += n[j];
//                        hostDiskUsed[i] += d[j];
//                        num_allocated++;
//
//                        System.out.println("  VM " + j + " assigned (LP value: " +
//                                String.format("%.3f", solution[varIndex]) + ")");
//
//                        if(lrMig[j] == -1) {
//                            lrMig[j] = i;
//                        }
//                        else if(lrMig[j] != i) {
//                            lrMig[j] = i;
//                            migrations++;
//                        }
//                    }
//                }
//            }
//
//            System.out.printf("Host %d: CPU: %.1f/%.1f, RAM: %.1f/%.1f, Network: %.1f/%.1f, Disk: %.1f/%.1f%n",
//                    (i + 1), hostCpuUsed[i], C[i], hostRamUsed[i], M[i],
//                    hostBwUsed[i], N[i], hostDiskUsed[i], D[i]);
//        }

        List<AlgorithmsComparison.VMAllocation> allocations = new ArrayList<>();

        // Collect all non-zero allocations
        for (int i = 0; i < numHosts; i++) {
            for (int j = 0; j < numVMs; j++) {
                int varIndex = i * numVMs + j;
                if (solution[varIndex] > 0.0) { // Only consider positive allocations
                    allocations.add(new AlgorithmsComparison.VMAllocation(j, i, solution[varIndex]));
                }
            }
        }

        // Sort by allocation value (highest first)
        Collections.sort(allocations);

        // Track allocated VMs
        for(int i = 0; i < numVMs; i++){
            allocated[i] = false;
        }

        // Phase 1: Process sorted allocations with threshold (e.g., > 0.5)
        System.out.println("Phase 1: Processing LP solution (sorted by allocation value):");
        for (AlgorithmsComparison.VMAllocation alloc : allocations) {
            int j = alloc.vmIndex;
            int i = alloc.hostIndex;

            // Apply threshold and check if VM not already allocated
            if (alloc.allocationValue > 0.5 && !allocated[j]) {
                // Check if adding this VM would exceed capacity
                if (hostCpuUsed[i] + c[j] <= C[i] &&
                        hostRamUsed[i] + m[j] <= M[i] &&
                        hostBwUsed[i] + n[j] <= N[i] &&
                        hostDiskUsed[i] + d[j] <= D[i]) {

                    // Allocate the VM
                    allocated[j] = true;
                    hostCpuUsed[i] += c[j];
                    hostRamUsed[i] += m[j];
                    hostBwUsed[i] += n[j];
                    hostDiskUsed[i] += d[j];
                    num_allocated++;

                    System.out.println("  VM " + j + " assigned to Host " + (i + 1) +
                            " (LP value: " + String.format("%.3f", alloc.allocationValue) + ")");


                }
            }
        }

        // Phase 2: Most-Full for remaining VMs
        // Phase 2: Most-Full for remaining VMs (sorted by LP solution values)
        System.out.println("\nPhase 2: Most-Full for remaining unallocated VMs (sorted by LP solution values):");

// Get remaining allocations for unallocated VMs
        List<AlgorithmsComparison.VMAllocation> remainingAllocations = new ArrayList<>();
        for (AlgorithmsComparison.VMAllocation alloc : allocations) {
            if (!allocated[alloc.vmIndex]) {
                remainingAllocations.add(alloc);
            }
        }

// Sort by allocation value (highest first) - same as Phase 1
        Collections.sort(remainingAllocations);

        int phase2Allocated = 0;

// Process remaining VMs in sorted order (by LP solution values)
        for (AlgorithmsComparison.VMAllocation alloc : remainingAllocations) {
            int j = alloc.vmIndex;

            // Skip if VM was somehow allocated (safety check)
            if (allocated[j]) continue;
            // Build list of hosts ordered by current utilization (descending)
            Integer[] hostOrder = new Integer[numHosts];
            for (int i = 0; i < numHosts; i++) hostOrder[i] = i;

            Arrays.sort(hostOrder, (a, b) -> {
                double utilA = (hostCpuUsed[a] / C[a] +
                        hostRamUsed[a] / M[a] +
                        hostBwUsed[a]  / N[a] +
                        hostDiskUsed[a]/ D[a]) / 4.0;
                double utilB = (hostCpuUsed[b] / C[b] +
                        hostRamUsed[b] / M[b] +
                        hostBwUsed[b]  / N[b] +
                        hostDiskUsed[b]/ D[b]) / 4.0;
                return Double.compare(utilB, utilA); // descending
            });

            // Try hosts in Most-Full order
            for (int i : hostOrder) {
                if (hostCpuUsed[i] + c[j] <= C[i] &&
                        hostRamUsed[i] + m[j] <= M[i] &&
                        hostBwUsed[i]  + n[j] <= N[i] &&
                        hostDiskUsed[i]+ d[j] <= D[i]) {

                    // Allocate VM to the most utilized feasible host
                    allocated[j] = true;
                    hostCpuUsed[i] += c[j];
                    hostRamUsed[i] += m[j];
                    hostBwUsed[i]  += n[j];
                    hostDiskUsed[i]+= d[j];
                    num_allocated++;
                    phase2Allocated++;

                    System.out.println("  VM " + j + " assigned to Host " + (i + 1) +
                            " (Most-Full, LP value: " + String.format("%.3f", alloc.allocationValue) + ")");


                    break; // move to next VM
                }
            }
        }
        System.out.println("Phase 2 allocated " + phase2Allocated + " additional VMs");

        // Calculate resource usage
        cpuUsed = 0; ramUsed = 0; bwUsed = 0; diskUsed = 0;
        for (int i = 0; i < numHosts; i++) {
            cpuUsed += hostCpuUsed[i];
            ramUsed += (long) hostRamUsed[i];
            bwUsed += (long) hostBwUsed[i];
            diskUsed += (long) hostDiskUsed[i];
        }

        System.out.println("\n=================================================================\n");
        System.out.println("Optimal solution found!");
        System.out.println("Total VMs allocated: " + num_allocated);
        System.out.println("\nFinal Resource Utilization:");
        System.out.printf("CPU: %.1f/%.1f (%.1f%%)%n", cpuUsed, cpuTotal, (cpuUsed/cpuTotal)*100);
        System.out.printf("RAM: %d/%d (%.1f%%)%n", ramUsed, ramTotal, ((double)ramUsed/ramTotal)*100);
        System.out.printf("Network: %d/%d (%.1f%%)%n", bwUsed, bwTotal, ((double)bwUsed/bwTotal)*100);
        System.out.printf("Disk: %d/%d (%.1f%%)%n", diskUsed, diskTotal, ((double)diskUsed/diskTotal)*100);

        double migrationRate = (numVMs > 0) ? ((double) migrations / numVMs) * 100.0 : 0.0;
        System.out.println("Migrations: " + migrations + " out of " + numVMs + " VMs");
        System.out.println("Migration Rate: " + String.format("%.2f%%", migrationRate));


        Path RESULTS_DIR = Paths.get("../results/CSV Files/");
        java.nio.file.Files.createDirectories(RESULTS_DIR);
        Path outFile;
        if(bab) {
            outFile = RESULTS_DIR.resolve("BranchAndBoundAlgorithm.csv");
        } else {
            outFile = RESULTS_DIR.resolve("LinearRelaxationAlgorithm.csv");
        }

        try (com.opencsv.CSVWriter w = new com.opencsv.CSVWriter(new java.io.FileWriter(outFile.toFile(), flag))) {
            boolean header = java.nio.file.Files.notExists(outFile) || java.nio.file.Files.size(outFile) == 0;
            if (header) {
                w.writeNext(new String[]{
                        "placedVMs", "numVMs",
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
                    String.valueOf((double) num_allocated / numVMs * 100.0),
                    String.valueOf((cpuUsed / cpuTotal) * 100.0),
                    String.valueOf(((double) ramUsed / ramTotal) * 100.0),
                    String.valueOf(((double) bwUsed / bwTotal) * 100.0),
                    String.valueOf(((double) diskUsed / diskTotal) * 100.0),
                    String.valueOf(migrations),
                    String.valueOf(migrationRate)
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
