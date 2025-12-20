package org.cloudbus.cloudsim.examples.algorithms;

import org.cloudbus.cloudsim.examples.AlgorithmsComparison;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.cloudbus.cloudsim.examples.AlgorithmsComparison.flag;
import static org.cloudbus.cloudsim.examples.AlgorithmsComparison.getScarcityIndicator;

public class DRFScarcityAlgorithm {
    public DRFScarcityAlgorithm(double[] C, double[] M, double[] N, double[] D,
                                           double[] c, double[] m, double[] n, double[] d,
                                           int numHosts, int numVMs) throws IOException {

        System.out.println("\n=================================================================");
        System.out.println("DRF + Scarcity-Weighted Greedy Algorithm");
        System.out.println("(Combines fairness-based DRF with dynamic resource scarcity)");
        System.out.println("=================================================================\n");

        // ==================== STEP 1: Initialize Host Resources ====================
        double[] remainingCPU = new double[numHosts];
        double[] remainingRAM = new double[numHosts];
        double[] remainingNet = new double[numHosts];
        double[] remainingDisk = new double[numHosts];

        for (int i = 0; i < numHosts; i++) {
            remainingCPU[i] = C[i];
            remainingRAM[i] = M[i];
            remainingNet[i] = N[i];
            remainingDisk[i] = D[i];
        }

        System.out.println("Initial Host Capacities:");
        for (int i = 0; i < numHosts; i++) {
            System.out.printf("  Host%d: CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n",
                    i, C[i], M[i], N[i], D[i]);
        }
        System.out.println();


        // ==================== STEP 2: Calculate Average Host Capacities ====================
        double avgCPU = 0, avgRAM = 0, avgNet = 0, avgDisk = 0;

        for (int i = 0; i < numHosts; i++) {
            avgCPU += C[i];
            avgRAM += M[i];
            avgNet += N[i];
            avgDisk += D[i];
        }

        avgCPU /= numHosts;
        avgRAM /= numHosts;
        avgNet /= numHosts;
        avgDisk /= numHosts;

        System.out.println("Average Host Capacities (for normalization):");
        System.out.printf("  CPU: %.1f MIPS\n", avgCPU);
        System.out.printf("  RAM: %.1f MB\n", avgRAM);
        System.out.printf("  Network: %.1f Mbps\n", avgNet);
        System.out.printf("  Disk: %.1f GB\n\n", avgDisk);


        // ==================== STEP 3: Track Unallocated VMs ====================
        Set<Integer> unallocatedVMs = new HashSet<>();
        for (int j = 0; j < numVMs; j++) {
            unallocatedVMs.add(j);
        }

        boolean[] allocated = new boolean[numVMs];
        int numAllocated = 0;
        int migrations = 0;

        // For migration tracking
        int[] drfScarcityMig = new int[numVMs];
        Arrays.fill(drfScarcityMig, -1);

        // Alpha parameter: balance between fairness (DRF) and scarcity
        final double ALPHA = 0.7; // 70% fairness, 30% scarcity
        final double EPSILON = 0.001; // To avoid division by zero


        // ==================== STEP 4: Dynamic Allocation Loop ====================
        int iteration = 0;

        while (!unallocatedVMs.isEmpty()) {

            iteration++;


            System.out.println("\"=================================================================");
            System.out.printf("ITERATION %d - Remaining VMs: %d\n", iteration, unallocatedVMs.size());
            System.out.println("\"=================================================================\n");


            // ==================== STEP 4.1: Calculate Scarcity Weights (λ) ====================
            // λ_k = 1 / (ε + avg_i(Γᵢ,ₖ / Cᵢ,ₖ))
            // Higher λ = more scarce resource

            double avgRemainingCPU = 0, avgRemainingRAM = 0, avgRemainingNet = 0, avgRemainingDisk = 0;

            for (int i = 0; i < numHosts; i++) {
                avgRemainingCPU += remainingCPU[i] / C[i]; // Fraction remaining
                avgRemainingRAM += remainingRAM[i] / M[i];
                avgRemainingNet += remainingNet[i] / N[i];
                avgRemainingDisk += remainingDisk[i] / D[i];
            }

            avgRemainingCPU /= numHosts;
            avgRemainingRAM /= numHosts;
            avgRemainingNet /= numHosts;
            avgRemainingDisk /= numHosts;

            // Calculate scarcity weights (inverse of remaining fraction)
            double lambdaCPU = 1.0 / (EPSILON + avgRemainingCPU);
            double lambdaRAM = 1.0 / (EPSILON + avgRemainingRAM);
            double lambdaNet = 1.0 / (EPSILON + avgRemainingNet);
            double lambdaDisk = 1.0 / (EPSILON + avgRemainingDisk);

            System.out.println("Current Resource Scarcity Weights (λ):");
            System.out.printf("  λ_CPU:  %.4f (avg remaining: %.1f%%)  %s\n",
                    lambdaCPU, avgRemainingCPU * 100, getScarcityIndicator(avgRemainingCPU));
            System.out.printf("  λ_RAM:  %.4f (avg remaining: %.1f%%)  %s\n",
                    lambdaRAM, avgRemainingRAM * 100, getScarcityIndicator(avgRemainingRAM));
            System.out.printf("  λ_Net:  %.4f (avg remaining: %.1f%%)  %s\n",
                    lambdaNet, avgRemainingNet * 100, getScarcityIndicator(avgRemainingNet));
            System.out.printf("  λ_Disk: %.4f (avg remaining: %.1f%%)  %s\n\n",
                    lambdaDisk, avgRemainingDisk * 100, getScarcityIndicator(avgRemainingDisk));


            // ==================== STEP 4.2: Calculate Score for Each Unallocated VM ====================
            System.out.println("Computing VM scores (qₘ = α×DRF + (1-α)×ScarcityWeighted):");
            System.out.println("VM\tNorm CPU\tNorm RAM\tNorm Net\tNorm Disk\tDRF\tWeighted\tFinal Score (qₘ)");
            System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────");

            List<AlgorithmsComparison.VMWithScore> vmScores = new ArrayList<>();

            avgCPU = 0; avgRAM = 0; avgNet = 0; avgDisk = 0;
            for (int i = 0; i < numHosts; i++) {
                avgCPU += C[i] - remainingCPU[i];
                avgRAM += M[i] - remainingRAM[i];
                avgNet += N[i] -  remainingNet[i];
                avgDisk += D[i] -  remainingDisk[i];
            }

            avgCPU /= numHosts;
            avgRAM /= numHosts;
            avgNet /= numHosts;
            avgDisk /= numHosts;

            for (int j : unallocatedVMs) {
                // Step 1: Normalize VM demands by average host capacity
                double normCPU = c[j] / avgCPU;
                double normRAM = m[j] / avgRAM;
                double normNet = n[j] / avgNet;
                double normDisk = d[j] / avgDisk;

                // Step 2: Calculate Dominant Resource Fairness (DRF)
                double drf = Math.max(
                        Math.max(normCPU, normRAM),
                        Math.max(normNet, normDisk)
                );

                // Step 3: Calculate Scarcity-Weighted Sum
                double scarcityWeighted = lambdaCPU * normCPU +
                        lambdaRAM * normRAM +
                        lambdaNet * normNet +
                        lambdaDisk * normDisk;

                // Step 4: Combine into final score
                // qₘ = α × DRF + (1-α) × ScarcityWeighted
                double finalScore = ALPHA * drf + (1 - ALPHA) * scarcityWeighted;

                vmScores.add(new AlgorithmsComparison.VMWithScore(j, finalScore, drf, scarcityWeighted));

                System.out.printf("VM%d\t%.4f\t\t%.4f\t\t%.4f\t\t%.4f\t\t%.4f\t%.4f\t\t%.4f\n",
                        j, normCPU, normRAM, normNet, normDisk, drf, scarcityWeighted, finalScore);
            }


            // ==================== STEP 4.3: Sort VMs by Score (Ascending) ====================
            Collections.sort(vmScores); // Smallest score first (easiest/fairest)

            System.out.println("\nSorted VM order (lowest qₘ first - easiest to place):");
            for (int i = 0; i < Math.min(5, vmScores.size()); i++) {
                AlgorithmsComparison.VMWithScore vm = vmScores.get(i);
                System.out.printf("  %d. VM%d (qₘ=%.4f, DRF=%.4f, Scarcity=%.4f)\n",
                        i+1, vm.vmIndex, vm.score, vm.drf, vm.scarcityWeighted);
            }
            if (vmScores.size() > 5) {
                System.out.printf("  ... and %d more VMs\n", vmScores.size() - 5);
            }
            System.out.println();


            // ==================== STEP 4.4: Select VM with Lowest Score ====================
            AlgorithmsComparison.VMWithScore selectedVM = vmScores.get(0);
            int j = selectedVM.vmIndex;

            System.out.println("─────────────────────────────────────────────────────────────────");
            System.out.printf("=> Selected VM%d (Score: %.4f)\n", j, selectedVM.score);
            System.out.printf("  DRF component: %.4f (weight: %.1f%%)\n", selectedVM.drf, ALPHA * 100);
            System.out.printf("  Scarcity component: %.4f (weight: %.1f%%)\n", selectedVM.scarcityWeighted, (1-ALPHA) * 100);
            System.out.printf("  Requests: CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n\n", c[j], m[j], n[j], d[j]);


            // ==================== STEP 4.5: Find Best Host (Max Post-Allocation Utilization) ====================
            int bestHost = -1;
            double minMaxUtilization = Double.MAX_VALUE; // We want to minimize the MAX utilization

            System.out.println("  Evaluating hosts (minimize maximum post-allocation utilization):");

            for (int i = 0; i < numHosts; i++) {
                // Check feasibility
                boolean feasible = (remainingCPU[i] >= c[j]) &&
                        (remainingRAM[i] >= m[j]) &&
                        (remainingNet[i] >= n[j]) &&
                        (remainingDisk[i] >= d[j]);

                if (!feasible) {
                    System.out.printf("    Host%d: ✗ INFEASIBLE", i);

                    List<String> missing = new ArrayList<>();
                    if (remainingCPU[i] < c[j]) missing.add(String.format("CPU(%.1f<%.1f)", remainingCPU[i], c[j]));
                    if (remainingRAM[i] < m[j]) missing.add(String.format("RAM(%.1f<%.1f)", remainingRAM[i], m[j]));
                    if (remainingNet[i] < n[j]) missing.add(String.format("Net(%.1f<%.1f)", remainingNet[i], n[j]));
                    if (remainingDisk[i] < d[j]) missing.add(String.format("Disk(%.1f<%.1f)", remainingDisk[i], d[j]));
                    System.out.printf(" - %s\n", String.join(", ", missing));

                    continue;
                }

                // Calculate post-allocation utilization for each resource
                double postCPU = (C[i] - remainingCPU[i] + c[j]) / C[i];
                double postRAM = (M[i] - remainingRAM[i] + m[j]) / M[i];
                double postNet = (N[i] - remainingNet[i] + n[j]) / N[i];
                double postDisk = (D[i] - remainingDisk[i] + d[j]) / D[i];

                // Find MAXIMUM post-allocation utilization (bottleneck)
                double maxPostUtilization = Math.max(
                        Math.max(postCPU, postRAM),
                        Math.max(postNet, postDisk)
                );

                System.out.printf("    Host%d:  FEASIBLE - Max util after: %.4f", i, maxPostUtilization);
                System.out.printf(" (CPU:%.3f RAM:%.3f Net:%.3f Disk:%.3f)\n",
                        postCPU, postRAM, postNet, postDisk);

                // Choose host that minimizes maximum post-allocation utilization
                if (maxPostUtilization < minMaxUtilization) {
                    minMaxUtilization = maxPostUtilization;
                    bestHost = i;
                }
            }

            System.out.println();


            // ==================== STEP 4.6: Allocate to Best Host ====================
            if (bestHost != -1) {
                // ALLOCATE
                allocated[j] = true;
                numAllocated++;

                // Update remaining resources
                remainingCPU[bestHost] -= c[j];
                remainingRAM[bestHost] -= m[j];
                remainingNet[bestHost] -= n[j];
                remainingDisk[bestHost] -= d[j];

                // Remove from unallocated set
                unallocatedVMs.remove(j);

                // Track migrations
                if (drfScarcityMig[j] == -1) {
                    drfScarcityMig[j] = bestHost;
                } else if (drfScarcityMig[j] != bestHost) {
                    drfScarcityMig[j] = bestHost;
                    migrations++;
                }

                double avgUtilization = ((C[bestHost]-remainingCPU[bestHost])/C[bestHost] +
                        (M[bestHost]-remainingRAM[bestHost])/M[bestHost] +
                        (N[bestHost]-remainingNet[bestHost])/N[bestHost] +
                        (D[bestHost]-remainingDisk[bestHost])/D[bestHost]) / 4.0 * 100.0;

                System.out.printf("      SUCCESS - VM%d ALLOCATED to Host%d ✓✓✓\n", j, bestHost);
                System.out.printf("      Max post-allocation utilization: %.4f (%.1f%%)\n",
                        minMaxUtilization, minMaxUtilization * 100);
                System.out.printf("      Host%d average utilization: %.1f%%\n", bestHost, avgUtilization);
                System.out.printf("      Host%d remaining: CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n\n",
                        bestHost, remainingCPU[bestHost], remainingRAM[bestHost],
                        remainingNet[bestHost], remainingDisk[bestHost]);

            } else {
                System.out.printf("    FAILED - No feasible host for VM%d ✗✗✗\n", j);
                System.out.println("      Removing from consideration...\n");
                unallocatedVMs.remove(j);
            }

            // Prevent infinite loops
            if (iteration > numVMs * 2) {
                System.out.println("  Maximum iterations reached. Stopping.\n");
                break;
            }
        }


        // ==================== STEP 5: Calculate Final Statistics ====================
        double cpuTotal = 0, cpuUsed = 0;
        long ramTotal = 0, ramUsed = 0;
        long netTotal = 0, netUsed = 0;
        long diskTotal = 0, diskUsed = 0;

        for (int i = 0; i < numHosts; i++) {
            cpuTotal += C[i];
            ramTotal += (long) M[i];
            netTotal += (long) N[i];
            diskTotal += (long) D[i];

            cpuUsed += (C[i] - remainingCPU[i]);
            ramUsed += (long) (M[i] - remainingRAM[i]);
            netUsed += (long) (N[i] - remainingNet[i]);
            diskUsed += (long) (D[i] - remainingDisk[i]);
        }


        // ==================== STEP 6: Print Summary ====================
        System.out.println("=================================================================");
        System.out.println("FINAL SUMMARY - DRF + Scarcity-Weighted Algorithm");
        System.out.println("=================================================================");
        System.out.printf("Algorithm parameters: α = %.2f (%.0f%% fairness, %.0f%% scarcity)\n",
                ALPHA, ALPHA*100, (1-ALPHA)*100);
        System.out.printf("Total iterations: %d\n", iteration);
        System.out.printf("VMs successfully allocated: %d / %d (%.2f%%)\n",
                numAllocated, numVMs, (double)numAllocated/numVMs * 100.0);
        System.out.printf("VMs failed to allocate: %d\n", numVMs - numAllocated);

        System.out.println("\n Resource Utilization:");
        System.out.printf("  CPU:     %.1f / %.1f (%.2f%%)\n", cpuUsed, cpuTotal, cpuUsed/cpuTotal*100);
        System.out.printf("  RAM:     %d / %d (%.2f%%)\n", ramUsed, ramTotal, (double)ramUsed/ramTotal*100);
        System.out.printf("  Network: %d / %d (%.2f%%)\n", netUsed, netTotal, (double)netUsed/netTotal*100);
        System.out.printf("  Disk:    %d / %d (%.2f%%)\n", diskUsed, diskTotal, (double)diskUsed/diskTotal*100);

        double avgUtilization = (cpuUsed/cpuTotal + (double)ramUsed/ramTotal +
                (double)netUsed/netTotal + (double)diskUsed/diskTotal) / 4.0 * 100.0;
        System.out.printf("  Average: %.2f%%\n", avgUtilization);

        double migrationRate = (numVMs > 0) ? ((double) migrations / numVMs) * 100.0 : 0.0;
        System.out.printf("\n Migrations: %d / %d VMs (%.2f%%)\n", migrations, numVMs, migrationRate);

        System.out.println("\n  Final Host States:");
        for (int i = 0; i < numHosts; i++) {
            double utilization = ((C[i]-remainingCPU[i])/C[i] +
                    (M[i]-remainingRAM[i])/M[i] +
                    (N[i]-remainingNet[i])/N[i] +
                    (D[i]-remainingDisk[i])/D[i]) / 4.0 * 100.0;
            int vmsOnHost = 0;
            for (int jj = 0; jj < numVMs; jj++) {
                if (drfScarcityMig[jj] == i) vmsOnHost++;
            }
            System.out.printf("  Host%d: %2d VMs | %.1f%% avg util\n", i, vmsOnHost, utilization);
            System.out.printf("         CPU:%.1f%% RAM:%.1f%% Net:%.1f%% Disk:%.1f%%\n",
                    (C[i]-remainingCPU[i])/C[i]*100,
                    (M[i]-remainingRAM[i])/M[i]*100,
                    (N[i]-remainingNet[i])/N[i]*100,
                    (D[i]-remainingDisk[i])/D[i]*100);
        }

        if (numAllocated < numVMs) {
            System.out.println("\n Unallocated VMs:");
            for (int jj = 0; jj < numVMs; jj++) {
                if (!allocated[jj]) {
                    System.out.printf("  VM%d - CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n",
                            jj, c[jj], m[jj], n[jj], d[jj]);
                }
            }
        }

        System.out.println("═════════════════════════════════════════════════════════════════\n");


        // ==================== STEP 7: Write to CSV ====================
        final String file = "DRFScarcityAlgorithm.csv";
        Path RESULTS_DIR = Paths.get("../results/CSV Files/");
        java.nio.file.Files.createDirectories(RESULTS_DIR);
        Path outFile = RESULTS_DIR.resolve(file);

        try (com.opencsv.CSVWriter w = new com.opencsv.CSVWriter(
                new java.io.FileWriter(outFile.toFile(), flag))) {
            boolean header = java.nio.file.Files.notExists(outFile) ||
                    java.nio.file.Files.size(outFile) == 0;
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
                    String.valueOf(numAllocated),
                    String.valueOf(numVMs),
                    String.valueOf((double) numAllocated / numVMs * 100.0),
                    String.valueOf((cpuUsed / cpuTotal) * 100.0),
                    String.valueOf(((double) ramUsed / ramTotal) * 100.0),
                    String.valueOf(((double) netUsed / netTotal) * 100.0),
                    String.valueOf(((double) diskUsed / diskTotal) * 100.0),
                    String.valueOf(migrations),
                    String.valueOf(migrationRate)
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
