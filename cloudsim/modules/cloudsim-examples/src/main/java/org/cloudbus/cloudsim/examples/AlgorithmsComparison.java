package org.cloudbus.cloudsim.examples;
import com.opencsv.CSVWriter;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.container.core.ContainerVm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.GuestEntity;
import org.cloudbus.cloudsim.core.HostEntity;
import org.cloudbus.cloudsim.lists.VmList;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.selectionPolicies.*;
import scpsolver.constraints.LinearSmallerThanEqualsConstraint;
import scpsolver.lpsolver.LinearProgramSolver;
import scpsolver.lpsolver.SolverFactory;
import scpsolver.problems.LinearProgram;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class AlgorithmsComparison {
    public static DatacenterBroker broker;

    /** The vmlist. */
    private static List<Vm> vmlist;

    /** Global Variables for all algorithms **/
    // General
    static int NUM_HOSTS =4;
    static int NUM_VMS = 3;
    static int MAX_VMS = 25;
    static int INCREMENT_VAL = 3;
    static int MONTE_CARLO_ITERS = 500;

    // Hosts Specs
    static double[] C;
    static double[] M;
    static double[] N;
    static double[] D;

    // VMs Specs
    static double[] c;
    static double[] m;
    static double[] n;
    static double[] d;

    // Flag for CSVWriter append
    static boolean flag;

    static int [] bbMig;
    static int [] lrMig;
    static int [] lrrMig;

    static int [] lfMig;

    static int [] mfMig;
    static int [] ffMig;

    //static int VMs = 0;

    // Hard coded examples for debugging
//    static double[] C = {80, 60, 50, 90, 30, 65};
//    static double[] M = {12, 10, 9, 16, 6, 11};
//    static double[] N = {600, 500, 400, 700, 350, 550};
//    static double[] D = {250, 200, 180, 300, 150, 230};
//
//    // VM Required Resources.
//    static double[] c = {35, 40, 30, 45, 36, 50, 33, 38, 42, 31, 22, 25, 27, 20, 23, 28, 26, 24, 22, 25};
//    static double[] m = {6, 8, 5, 9, 7, 10, 6, 7, 8, 5, 4, 5, 4, 3, 4, 5, 4, 3, 3, 4};
//    static double[] n = {280, 300, 250, 320, 290, 350, 260, 275, 300, 240, 190, 200, 210, 180, 190, 220, 210, 200, 180, 190};
//    static double[] d = {110, 130, 100, 140, 115, 150, 100, 120, 135, 95, 85, 90, 95, 80, 85, 100, 90, 85, 80, 90};

    public static int algorithm(SelectionPolicy<HostEntity> selectionPolicy) {
        int allocated = 0;
        int migrations = 0;
        try {
            // First step: Initialize the CloudSim package. It should be called before creating any entities.
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance(); // Calendar whose fields have been initialized with the current date and time.
            boolean trace_flag = false; // trace events

            CloudSim.init(num_user, calendar, trace_flag);

            // Second step: Create Datacenters
            String name = selectionPolicy.getClass().getSimpleName();
            List<Host> hostList = new ArrayList<>();

            for (int i = 0; i < NUM_HOSTS; i++) {
                List<Pe> peList = new ArrayList<>();
                peList.add(new Pe(0, new PeProvisionerSimple(C[i])));
                hostList.add(new Host(
                        i,
                        new RamProvisionerSimple((int) M[i]),
                        new BwProvisionerSimple((int) N[i]),
                        (int) D[i],
                        peList,
                        new VmSchedulerTimeShared(peList)
                ));

            }

            // === Datacenter Properties ===
            String arch = "x86";
            String os = "Linux";
            String vmm = "Xen";
            double time_zone = 10.0;
            double cost = 3.0;
            double costPerMem = 0.05;
            double costPerStorage = 0.001;
            double costPerBw = 0.0;
            LinkedList<Storage> storageList = new LinkedList<>();

            DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                    arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw
            );

            Datacenter datacenter = null;
            try {
                datacenter = new Datacenter(name, characteristics, new VmAllocationWithSelectionPolicy(hostList, selectionPolicy), storageList, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Datacenters are the resource providers in CloudSim. We need at
            // list one of them to run a CloudSim simulation

            // Third step: Create Broker
            broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();

            // Fourth step: Create VMs
            vmlist = new ArrayList<>();

            // VM properties: (id, brokerId, mips, pesNumber, ram, bw, size, vmm, scheduler)
            for (int i = 0; i < NUM_VMS; i++) {
                vmlist.add(new Vm(i, brokerId, c[i], 1, (int) m[i], (int) n[i], (int) d[i], vmm, new CloudletSchedulerTimeShared()));
            }

            // submit vm list to the broker
            broker.submitGuestList(vmlist);

            // Fifth step: Starts the simulation
            CloudSim.startSimulation();
            allocated = broker.getAllocatedVMs();
            for (int i = 0; i < NUM_VMS; i++) {
                GuestEntity guest = VmList.getById(broker.getGuestList(), i);
                int host = ((guest.getHost() == null) ? -1 : guest.getHost().getId());
                if(host != -1) {
                    if(lfMig[i] == -1) {
                        lfMig[i] = host;
                    }
                    else if(lfMig[i] != host) {
                        lfMig[i] = host;
                        migrations++;
                    }
                }
            }

            Log.println("VMs allocated: " + (broker.getAllocatedVMs()));
//            for(HostEntity host : datacenter.getHostList()) {
//                Log.println(host.getNumberOfGuests());
//                Log.println("mips: " + host.getTotalMips() + " | available: " + host.getGuestScheduler().getAvailableMips());
//                Log.println("ram: " + host.getRam() + " | available: " + host.getGuestRamProvisioner().getAvailableRam());
//                Log.println("bw: " + host.getBw() + " | available: " + host.getGuestBwProvisioner().getAvailableBw());
//                Log.println("disk: " + ((Host) host).getMaxStorage() + " | available: " + host.getStorage());
//
//            }
            double cpuTotal = 0, cpuAvail = 0;
            long ramTotal = 0, ramAvail = 0;
            long netTotal = 0, netAvail = 0;
            long diskTotal = 0, diskAvail = 0;

            for (HostEntity h : datacenter.getHostList()) {
                cpuTotal += h.getTotalMips();
                cpuAvail += h.getGuestScheduler().getAvailableMips();

                ramTotal += h.getRam();
                ramAvail += h.getGuestRamProvisioner().getAvailableRam();

                netTotal += h.getBw();
                netAvail += h.getGuestBwProvisioner().getAvailableBw();

                diskTotal += ((Host) h).getMaxStorage();
                diskAvail += h.getStorage();
            }

            double cpuUsed = cpuTotal - cpuAvail;
            long   ramUsed = ramTotal - ramAvail;
            long   netUsed = netTotal - netAvail;
            long   diskUsed = diskTotal - diskAvail;

            final String file = name + ".csv";

            Path RESULTS_DIR = Paths.get("../results/CSV Files/");
            java.nio.file.Files.createDirectories(RESULTS_DIR);
            Path outFile = RESULTS_DIR.resolve(file);

            java.io.File f = new java.io.File(file);


            try (com.opencsv.CSVWriter w = new com.opencsv.CSVWriter(new java.io.FileWriter(outFile.toFile(), flag))) {
                boolean header =  java.nio.file.Files.notExists(outFile) || java.nio.file.Files.size(outFile) == 0;
                if (header) {
                    w.writeNext(new String[]{
                            "placedVMs", "numVMs",
                            "allocRate",
                            "cpuUtilRate",
                            "ramUtilRate",
                            "netUtilRate",
                            "diskUtilRate"
                    });
                }
                w.writeNext(new String[]{
                        String.valueOf(allocated), String.valueOf(NUM_VMS),
                        String.valueOf((float) allocated / (float) NUM_VMS * 100.0),
                        String.valueOf((double)cpuUsed / (double)cpuTotal * 100.0),
                        String.valueOf((float) ramUsed / ramTotal * 100.0),
                        String.valueOf((float) netUsed / (float) netTotal * 100.0),
                        String.valueOf((float) diskUsed / (float) diskTotal * 100.0)
                });
            }
            broker.clearDatacenters();
            CloudSim.stopSimulation();


            //Final step: Print results when simulation is over
            List<Cloudlet> newList = broker.getCloudletReceivedList();

            Log.println(name + " Finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.println("Unwanted errors happen");
        }
        System.out.println("\n=================================================================\n");
        return migrations;
    }

    public static int BranchAndBoundAlgorithm(double[] C, double[] M, double[] N, double[] D, double[] c, double[] m, double[] n, double[] d, int numHosts, int numVMs) throws IOException {

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
        double[] solution = solver.solve(lp);
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
                        if(bbMig[j] == -1) {
                            bbMig[j] = i;
                        }
                        else if(bbMig[j] != i) {
                            bbMig[j] = i;
                            migrations++;
                        }

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

        double migrationRate = (NUM_VMS > 0) ? ((double) migrations / NUM_VMS) * 100.0 : 0.0;
        System.out.println("Migrations: " + migrations + " out of " + NUM_VMS + " VMs");
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
                    String.valueOf(NUM_VMS),
                    String.valueOf((float) num_allocated / (float) NUM_VMS * 100.0),
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

        return migrations;


    }
    static class VMAllocation implements Comparable<VMAllocation> {
        int vmIndex;
        int hostIndex;
        double allocationValue;

        public VMAllocation(int vmIndex, int hostIndex, double allocationValue) {
            this.vmIndex = vmIndex;
            this.hostIndex = hostIndex;
            this.allocationValue = allocationValue;
        }

        @Override
        public int compareTo(VMAllocation other) {
            // Sort by allocation value in descending order (highest first)
            return Double.compare(other.allocationValue, this.allocationValue);
        }

        @Override
        public String toString() {
            return String.format("VM%d->Host%d (%.3f)", vmIndex, hostIndex, allocationValue);
        }
    }


    public static int LinearRelaxationAlgorithm(double[] C, double[] M, double[] N, double[] D, double[] c, double[] m, double[] n, double[] d, int numHosts, int numVMs) throws IOException {

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

        List<VMAllocation> allocations = new ArrayList<>();

        // Collect all non-zero allocations
        for (int i = 0; i < numHosts; i++) {
            for (int j = 0; j < numVMs; j++) {
                int varIndex = i * numVMs + j;
                if (solution[varIndex] > 0.0) { // Only consider positive allocations
                    allocations.add(new VMAllocation(j, i, solution[varIndex]));
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
        for (VMAllocation alloc : allocations) {
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

                    // Migration tracking
                    if(lrMig[j] == -1) {
                        lrMig[j] = i;
                    } else if(lrMig[j] != i) {
                        lrMig[j] = i;
                        migrations++;
                    }
                }
            }
        }

        // Phase 2: Most-Full for remaining VMs
        // Phase 2: Most-Full for remaining VMs (sorted by LP solution values)
        System.out.println("\nPhase 2: Most-Full for remaining unallocated VMs (sorted by LP solution values):");

// Get remaining allocations for unallocated VMs
        List<VMAllocation> remainingAllocations = new ArrayList<>();
        for (VMAllocation alloc : allocations) {
            if (!allocated[alloc.vmIndex]) {
                remainingAllocations.add(alloc);
            }
        }

// Sort by allocation value (highest first) - same as Phase 1
        Collections.sort(remainingAllocations);

        int phase2Allocated = 0;

// Process remaining VMs in sorted order (by LP solution values)
        for (VMAllocation alloc : remainingAllocations) {
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

                    if (lrMig[j] == -1) lrMig[j] = i;
                    else if (lrMig[j] != i) {
                        lrMig[j] = i;
                        migrations++;
                    }
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

        final String file = "LinearRelaxationAlgorithm.csv";
        Path RESULTS_DIR = Paths.get("../results/CSV Files/");
        java.nio.file.Files.createDirectories(RESULTS_DIR);
        Path outFile = RESULTS_DIR.resolve("LinearRelaxationAlgorithm.csv");

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

        return migrations;
    }

    public static int LinearRelaxationAlgorithmFirstfit(double[] C, double[] M, double[] N, double[] D, double[] c, double[] m, double[] n, double[] d, int numHosts, int numVMs) throws IOException {

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

        List<VMAllocation> allocations = new ArrayList<>();

        // Collect all non-zero allocations
        for (int i = 0; i < numHosts; i++) {
            for (int j = 0; j < numVMs; j++) {
                int varIndex = i * numVMs + j;
                if (solution[varIndex] > 0.0) { // Only consider positive allocations
                    allocations.add(new VMAllocation(j, i, solution[varIndex]));
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
        for (VMAllocation alloc : allocations) {
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

                    // Migration tracking
                    if(lrrMig[j] == -1) {
                        lrrMig[j] = i;
                    } else if(lrrMig[j] != i) {
                        lrrMig[j] = i;
                        migrations++;
                    }
                }
            }
        }

//        // Phase 2: Most-Full for remaining VMs
//        System.out.println("\nPhase 2: Most-Full for remaining unallocated VMs:");
//        int phase2Allocated = 0;
//
//        for (int j = 0; j < numVMs; j++) {
//            if (!allocated[j]) {
//                // Build list of hosts ordered by current utilization (descending)
//                Integer[] hostOrder = new Integer[numHosts];
//                for (int i = 0; i < numHosts; i++) hostOrder[i] = i;
//
//                Arrays.sort(hostOrder, (a, b) -> {
//                    double utilA = (hostCpuUsed[a] / C[a] +
//                            hostRamUsed[a] / M[a] +
//                            hostBwUsed[a]  / N[a] +
//                            hostDiskUsed[a]/ D[a]) / 4.0;
//                    double utilB = (hostCpuUsed[b] / C[b] +
//                            hostRamUsed[b] / M[b] +
//                            hostBwUsed[b]  / N[b] +
//                            hostDiskUsed[b]/ D[b]) / 4.0;
//                    return Double.compare(utilB, utilA); // descending
//                });
//
//                // Try hosts in Most-Full order
//                for (int i : hostOrder) {
//                    if (hostCpuUsed[i] + c[j] <= C[i] &&
//                            hostRamUsed[i] + m[j] <= M[i] &&
//                            hostBwUsed[i]  + n[j] <= N[i] &&
//                            hostDiskUsed[i]+ d[j] <= D[i]) {
//
//                        // Allocate VM to the most utilized feasible host
//                        allocated[j] = true;
//                        hostCpuUsed[i] += c[j];
//                        hostRamUsed[i] += m[j];
//                        hostBwUsed[i]  += n[j];
//                        hostDiskUsed[i]+= d[j];
//                        num_allocated++;
//                        phase2Allocated++;
//
//                        System.out.println("  VM " + j + " assigned to Host " + (i + 1) + " (Most-Full)");
//
//                        if (lrMig[j] == -1) lrMig[j] = i;
//                        else if (lrMig[j] != i) {
//                            lrMig[j] = i;
//                            migrations++;
//                        }
//                        break; // move to next VM
//                    }
//                }
//            }
//        }
//        System.out.println("Phase 2 allocated " + phase2Allocated + " additional VMs");

        // Phase 2: First-fit for remaining VMs
        System.out.println("\nPhase 2: First-fit for remaining unallocated VMs:");
        int phase2Allocated = 0;
        for (int j = 0; j < numVMs; j++) {
            if (!allocated[j]) {
                // Try to find first host that can accommodate this VM
                for (int i = 0; i < numHosts; i++) {
                    if (hostCpuUsed[i] + c[j] <= C[i] && hostRamUsed[i] + m[j] <= M[i] && hostBwUsed[i] + n[j] <= N[i] && hostDiskUsed[i] + d[j] <= D[i]) {
                        // Allocate VM to first available host
                        allocated[j] = true;
                        hostCpuUsed[i] += c[j];
                        hostRamUsed[i] += m[j];
                        hostBwUsed[i] += n[j];
                        hostDiskUsed[i] += d[j];
                        num_allocated++;
                        phase2Allocated++;

                        System.out.println("  VM " + j + " assigned to Host " + (i + 1) + " (First-Fit)");

                        if(lrrMig[j] == -1) {
                            lrrMig[j] = i;
                        }
                        else if(lrrMig[j] != i) {
                            lrrMig[j] = i;
                            migrations++;
                        }

                        break; // Move to next VM
                    }
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

        final String file = "LinearRelaxationAlgorithm.csv";
        Path RESULTS_DIR = Paths.get("../results/CSV Files/");
        java.nio.file.Files.createDirectories(RESULTS_DIR);
        Path outFile = RESULTS_DIR.resolve("LinearRelaxationAlgorithm.csv");

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

        return migrations;
    }

    // helper for the random function
    private static int randInt(int min, int max) {
        return min + (int) Math.floor(Math.random() * (max - min + 1));
    }

    // randomize specs for hosts and VMs.
    private static void randomizeSpecs() {
        // Hosts Ranges
        final int C_MIN      = 100000;
        final int C_MAX      = 1000000;

        final int M_MIN    = 64000; // 2^16
        final int M_MAX    = 640000; // 2^19

        final int N_MIN      = 1000;
        final int N_MAX      = 10000;

        final int D_MIN        = 1000;
        final int D_MAX        = 10000;

        // VMs Ranges
        final int c_MIN      = 10000;
        final int c_MAX      = 100000;

        final int m_MIN    = 6400; // 2^14
        final int m_MAX    = 64000; // 2^16

        final int n_MIN      = 100;
        final int n_MAX      = 1000;

        final int d_MIN        = 100;
        final int d_MAX        = 1000;

        // Hosts Random Assignment
        for (int i = 0; i < C.length; i++) {
            C[i] = randInt(C_MIN, C_MAX);
            M[i] = randInt(M_MIN, M_MAX);
            N[i] = randInt(N_MIN, N_MAX);
            D[i] = randInt(D_MIN, D_MAX);
        }

        // VMs Random Assignment
        for (int j = 0; j < c.length; j++) {
            c[j] = randInt(c_MIN, c_MAX);
            m[j] = randInt(m_MIN, m_MAX);
            n[j] = randInt(n_MIN, n_MAX);
            d[j] = randInt(d_MIN, d_MAX);
        }
    }

    public static void main(String[] args) throws IOException {
        final int COL_SCP = 0, COL_LR = 1, COL_FF = 2, COL_MF = 3, COL_LF = 4, COL_RD = 5, START_VMS = NUM_VMS;
        int rows = (MAX_VMS / INCREMENT_VAL), row = 0;
        double[][] results = new double[rows][6];

        for (int t = 0; t < MONTE_CARLO_ITERS; t++) {
            NUM_VMS = START_VMS;
            bbMig = new int[MAX_VMS+1];
            lrMig = new int[MAX_VMS+1];
            lrrMig = new int[MAX_VMS+1];
            lfMig = new int[MAX_VMS+1];
            Arrays.fill(bbMig, -1);
            Arrays.fill(lrMig, -1);
            Arrays.fill(lrrMig, -1);
            Arrays.fill(lfMig, -1);
            C = new double[NUM_HOSTS];
            M = new double[NUM_HOSTS];
            N = new double[NUM_HOSTS];
            D = new double[NUM_HOSTS];

            // VMs Specs
            c = new double[MAX_VMS+1];
            m = new double[MAX_VMS+1];
            n = new double[MAX_VMS+1];
            d = new double[MAX_VMS+1];
            randomizeSpecs();
            long sumSCP = 0, sumLRFF = 0, sumLRMF = 0, sumFF = 0, sumMF = 0, sumLF = 0, sumRD = 0;
            for(; NUM_VMS <= MAX_VMS; NUM_VMS += INCREMENT_VAL, row++) {
                // Hosts Specs


                System.out.println("  Testing with " +  NUM_VMS  + " VMs");
                sumSCP += BranchAndBoundAlgorithm(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
                sumLRMF += LinearRelaxationAlgorithm(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
//                sumLRFF += LinearRelaxationAlgorithmFirstfit(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
                sumFF += algorithm(new SelectionPolicyFirstFit<>());
                sumMF += algorithm(new SelectionPolicyMostFull<>());
                sumLF += algorithm(new SelectionPolicyLeastFull<>());
//                sumRD += algorithm(new SelectionPolicyRandomSelection<>());
                if(!flag) flag = true;
            }
//            double total = (double) MONTE_CARLO_ITERS * NUM_VMS; // MC runs × requested VMs
//            results[row][COL_SCP] = 100.0 * sumSCP / total;
//            results[row][COL_LR] = 100.0 * sumLR / total;
//            results[row][COL_FF]  = 100.0 * sumFF  / total;
//            results[row][COL_MF]  = 100.0 * sumMF  / total;
//            results[row][COL_LF]  = 100.0 * sumLF  / total;
//            results[row][COL_RD]  = 100.0 * sumRD  / total;
            System.out.println("BB: " + sumSCP);
            System.out.println("LRMF: " + sumLRMF);
            System.out.println("LRFF: " + sumLRFF);
            System.out.println("FF: " + sumFF);
            System.out.println("MF: " + sumMF);
            System.out.println("LF: " + sumLF);
        }
        flag = false;

        System.out.println("\n\t\t\t=== Results Matrix (Allocation Rate %) ===\n");

//        System.out.println("numVMs\t Branch & Bound\t\tLinear Relaxation\t\tFirst Fit\t\tMost Full\t\tLeast Full\t\tRandom");
//
//        for (int i = 0; i < results.length; i++) {
//            int vmsAtRow = START_VMS + i * INCREMENT_VAL;
//            System.out.print(vmsAtRow + "\t\t\t");
//            for (int j = 0; j < results[i].length; j++) {
//                System.out.printf("%.2f%%\t\t\t", results[i][j]);
//            }
//            System.out.println();
//        }
    }
}


