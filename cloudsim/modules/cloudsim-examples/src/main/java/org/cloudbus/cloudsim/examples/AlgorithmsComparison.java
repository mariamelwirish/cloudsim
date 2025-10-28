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
    static int NUM_HOSTS =3;
    static int NUM_VMS = 3;
    static int MAX_VMS = 100;
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


    static class VMWithDRF implements Comparable<VMWithDRF> {
        int vmIndex;
        double dominantShare;

        public VMWithDRF(int vmIndex, double dominantShare) {
            this.vmIndex = vmIndex;
            this.dominantShare = dominantShare;
        }

        @Override
        public int compareTo(VMWithDRF other) {
            // Sort by dominant share ASCENDING (smallest first - easier to place)
            return Double.compare(this.dominantShare, other.dominantShare);
        }

        @Override
        public String toString() {
            return String.format("VM%d (DRF: %.4f)", vmIndex, dominantShare);
        }
    }

    public static int DRFAlgorithm(double[] C, double[] M, double[] N, double[] D,
                                                   double[] c, double[] m, double[] n, double[] d,
                                                   int numHosts, int numVMs) throws IOException {

        System.out.println("\n=================================================================");
        System.out.println("DRF Dynamic Greedy Algorithm (Recalculate DRF after each allocation)");
        System.out.println("=================================================================\n");

        // ==================== STEP 1: Initialize Host Resources ====================
        // Keep track of CURRENT remaining resources (these change dynamically)
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


        // ==================== STEP 2: Unallocated VMs ====================
        // Use a Set to track which VMs still need allocation
        Set<Integer> unallocatedVMs = new HashSet<>();
        for (int j = 0; j < numVMs; j++) {
            unallocatedVMs.add(j);
        }

        boolean[] allocated = new boolean[numVMs];
        int numAllocated = 0;
        int migrations = 0;

        // For migration tracking
        int[] drfDynamicMig = new int[numVMs];
        Arrays.fill(drfDynamicMig, -1);


        // ==================== STEP 3: Dynamic Allocation Loop ====================
        int iteration = 0;

        while (!unallocatedVMs.isEmpty()) {
            iteration++;
            System.out.println("─────────────────────────────────────────────────────────────────");
            System.out.printf("ITERATION %d - Remaining VMs to allocate: %d\n", iteration, unallocatedVMs.size());
            System.out.println("─────────────────────────────────────────────────────────────────\n");

            // ==================== STEP 3.1: Calculate CURRENT Max Host Capacities ====================
            // Use REMAINING resources to calculate DRF (dynamic approach)
            double maxCPU = 0, maxRAM = 0, maxNetwork = 0, maxDisk = 0;

            for (int i = 0; i < numHosts; i++) {
                if (remainingCPU[i] > maxCPU) maxCPU = remainingCPU[i];
                if (remainingRAM[i] > maxRAM) maxRAM = remainingRAM[i];
                if (remainingNet[i] > maxNetwork) maxNetwork = remainingNet[i];
                if (remainingDisk[i] > maxDisk) maxDisk = remainingDisk[i];
            }

            System.out.println("Current Maximum Remaining Capacities (across all hosts):");
            System.out.printf("  CPU: %.1f MIPS\n", maxCPU);
            System.out.printf("  RAM: %.1f MB\n", maxRAM);
            System.out.printf("  Network: %.1f Mbps\n", maxNetwork);
            System.out.printf("  Disk: %.1f GB\n\n", maxDisk);

            // Safety check: if all resources are exhausted
            if (maxCPU <= 0 || maxRAM <= 0 || maxNetwork <= 0 || maxDisk <= 0) {
                System.out.println(" All host resources exhausted. Cannot allocate remaining VMs.\n");
                break;
            }


            // STEP 3.2: Recalculate DRF for Unallocated VMs
            System.out.println("Recalculating DRF for unallocated VMs:");
            System.out.println("VM\tCPU Share\tRAM Share\tNet Share\tDisk Share\tDominant Share");
            System.out.println("---------------------------------------------------------------------------------");

            List<VMWithDRF> vmList = new ArrayList<>();

            for (int j : unallocatedVMs) {
                // Calculate share of each resource (relative to CURRENT remaining capacity)
                double cpuShare = c[j] / maxCPU;
                double ramShare = m[j] / maxRAM;
                double netShare = n[j] / maxNetwork;
                double diskShare = d[j] / maxDisk;

                // Dominant share is the MAXIMUM
                double dominantShare = Math.max(
                        Math.max(cpuShare, ramShare),
                        Math.max(netShare, diskShare)
                );

                vmList.add(new VMWithDRF(j, dominantShare));

                System.out.printf("VM%d\t%.4f\t\t%.4f\t\t%.4f\t\t%.4f\t\t%.4f\n",
                        j, cpuShare, ramShare, netShare, diskShare, dominantShare);
            }


            // ==================== STEP 3.3: Sort VMs by DRF (Ascending) ====================
            Collections.sort(vmList); // Smallest DRF first

            System.out.println("\nSorted order for this iteration (smallest DRF first):");
            for (int i = 0; i < vmList.size(); i++) {
                VMWithDRF vm = vmList.get(i);
                System.out.printf("  %d. VM%d (DRF: %.4f)\n", i+1, vm.vmIndex, vm.dominantShare);
            }
            System.out.println();


            // ==================== STEP 3.4: Try to Allocate the VM with Smallest DRF ====================
            VMWithDRF selectedVM = vmList.get(0); // Pick the easiest VM (smallest DRF)
            int j = selectedVM.vmIndex;

            System.out.printf("► Attempting to allocate VM%d (DRF: %.4f) - Requests: CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n",
                    j, selectedVM.dominantShare, c[j], m[j], n[j], d[j]);

            boolean placed = false;

            // Try each host (First-Fit)
            for (int i = 0; i < numHosts; i++) {
                // Check feasibility
                boolean feasible = (remainingCPU[i] >= c[j]) &&
                        (remainingRAM[i] >= m[j]) &&
                        (remainingNet[i] >= n[j]) &&
                        (remainingDisk[i] >= d[j]);

                if (feasible) {
                    // ALLOCATE VM to this host
                    allocated[j] = true;
                    numAllocated++;
                    placed = true;

                    // Update remaining resources
                    remainingCPU[i] -= c[j];
                    remainingRAM[i] -= m[j];
                    remainingNet[i] -= n[j];
                    remainingDisk[i] -= d[j];

                    // Remove from unallocated set
                    unallocatedVMs.remove(j);

                    // Track migrations
                    if (drfDynamicMig[j] == -1) {
                        drfDynamicMig[j] = i;
                    } else if (drfDynamicMig[j] != i) {
                        drfDynamicMig[j] = i;
                        migrations++;
                    }

                    System.out.printf("  ✓ SUCCESS - Allocated to Host%d\n", i);
                    System.out.printf("    Host%d new state: CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n",
                            i, remainingCPU[i], remainingRAM[i], remainingNet[i], remainingDisk[i]);
                    System.out.printf("    Host%d utilization: %.1f%%\n\n",
                            i, ((C[i]-remainingCPU[i])/C[i] + (M[i]-remainingRAM[i])/M[i] +
                                    (N[i]-remainingNet[i])/N[i] + (D[i]-remainingDisk[i])/D[i]) / 4.0 * 100);

                    break; // Move to next iteration
                } else {
                    System.out.printf("  ✗ Host%d: Insufficient resources\n", i);
                }
            }

            if (!placed) {
                System.out.printf("  ✗ FAILED - No feasible host for VM%d\n", j);
                System.out.println("    This VM will remain unallocated.");
                System.out.println("    Trying next VM in sorted list...\n");

                // Remove this VM from consideration to avoid infinite loop
                unallocatedVMs.remove(j);
            }

            // Prevent infinite loops
            if (iteration > numVMs * 2) {
                System.out.println("Maximum iterations reached. Stopping allocation.\n");
                break;
            }
        }


        // ==================== STEP 4: Calculate Final Statistics ====================
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


        // ==================== STEP 5: Print Summary ====================
        System.out.println("=================================================================");
        System.out.println("FINAL SUMMARY - DRF Dynamic Greedy Algorithm");
        System.out.println("=================================================================");
        System.out.printf("Total iterations: %d\n", iteration);
        System.out.printf("VMs allocated: %d / %d (%.2f%%)\n",
                numAllocated, numVMs, (double)numAllocated/numVMs * 100.0);
        System.out.printf("VMs failed to allocate: %d\n", numVMs - numAllocated);

        System.out.println("\nResource Utilization:");
        System.out.printf("  CPU:     %.1f / %.1f (%.2f%%)\n", cpuUsed, cpuTotal, cpuUsed/cpuTotal*100);
        System.out.printf("  RAM:     %d / %d (%.2f%%)\n", ramUsed, ramTotal, (double)ramUsed/ramTotal*100);
        System.out.printf("  Network: %d / %d (%.2f%%)\n", netUsed, netTotal, (double)netUsed/netTotal*100);
        System.out.printf("  Disk:    %d / %d (%.2f%%)\n", diskUsed, diskTotal, (double)diskUsed/diskTotal*100);

        double migrationRate = (numVMs > 0) ? ((double) migrations / numVMs) * 100.0 : 0.0;
        System.out.printf("\nMigrations: %d / %d VMs (%.2f%%)\n", migrations, numVMs, migrationRate);

        System.out.println("\nFinal Host States:");
        for (int i = 0; i < numHosts; i++) {
            double utilization = ((C[i]-remainingCPU[i])/C[i] +
                    (M[i]-remainingRAM[i])/M[i] +
                    (N[i]-remainingNet[i])/N[i] +
                    (D[i]-remainingDisk[i])/D[i]) / 4.0 * 100.0;
            int vmsOnHost = 0;
            for (int j = 0; j < numVMs; j++) {
                if (drfDynamicMig[j] == i) vmsOnHost++;
            }
            System.out.printf("  Host%d: %d VMs, %.1f%% utilized - Remaining: CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n",
                    i, vmsOnHost, utilization, remainingCPU[i], remainingRAM[i], remainingNet[i], remainingDisk[i]);
        }

        if (numAllocated < numVMs) {
            System.out.println("\nUnallocated VMs:");
            for (int j = 0; j < numVMs; j++) {
                if (!allocated[j]) {
                    System.out.printf("  VM%d - Requests: CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n",
                            j, c[j], m[j], n[j], d[j]);
                }
            }
        }

        System.out.println("=================================================================\n");


        // ==================== STEP 6: Write to CSV ====================
        final String file = "DRF_Dynamic_Greedy_Algorithm.csv";
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

        return migrations;
    }

    public static int DRFL2Algorithm(double[] C, double[] M, double[] N, double[] D,
                                               double[] c, double[] m, double[] n, double[] d,
                                               int numHosts, int numVMs) throws IOException {

        System.out.println("\n=================================================================");
        System.out.println("DRF Dynamic + L2 Euclidean Algorithm");
        System.out.println("(Recalculate DRF after each allocation + L2 host selection)");
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


        // ==================== STEP 2: Track Unallocated VMs ====================
        Set<Integer> unallocatedVMs = new HashSet<>();
        for (int j = 0; j < numVMs; j++) {
            unallocatedVMs.add(j);
        }

        boolean[] allocated = new boolean[numVMs];
        int numAllocated = 0;
        int migrations = 0;

        // For migration tracking
        int[] drfDynamicL2Mig = new int[numVMs];
        Arrays.fill(drfDynamicL2Mig, -1);


        // ==================== STEP 3: Dynamic Allocation Loop ====================
        int iteration = 0;

        while (!unallocatedVMs.isEmpty()) {
            iteration++;
            System.out.println("================================================================");
            System.out.printf("ITERATION %d - Remaining VMs to allocate: %d\n", iteration, unallocatedVMs.size());
            System.out.println("================================================================\n");

            // ==================== STEP 3.1: Calculate CURRENT Max Host Capacities ====================
            double maxCPU = 0, maxRAM = 0, maxNetwork = 0, maxDisk = 0;

            for (int i = 0; i < numHosts; i++) {
                if (remainingCPU[i] > maxCPU) maxCPU = remainingCPU[i];
                if (remainingRAM[i] > maxRAM) maxRAM = remainingRAM[i];
                if (remainingNet[i] > maxNetwork) maxNetwork = remainingNet[i];
                if (remainingDisk[i] > maxDisk) maxDisk = remainingDisk[i];
            }

            System.out.println("Current Maximum Remaining Capacities (across all hosts):");
            System.out.printf("  CPU: %.1f MIPS, RAM: %.1f MB, Network: %.1f Mbps, Disk: %.1f GB\n\n",
                    maxCPU, maxRAM, maxNetwork, maxDisk);

            // Safety check: if all resources are exhausted
            if (maxCPU <= 0 || maxRAM <= 0 || maxNetwork <= 0 || maxDisk <= 0) {
                System.out.println("All host resources exhausted. Cannot allocate remaining VMs.\n");
                break;
            }


            // ==================== STEP 3.2: Recalculate DRF for Unallocated VMs ====================
            System.out.println("Recalculating DRF for unallocated VMs:");
            System.out.println("VM\tCPU Share\tRAM Share\tNet Share\tDisk Share\tDominant Share");
            System.out.println("--------------------------------------------------------------------");

            List<VMWithDRF> vmList = new ArrayList<>();

            for (int j : unallocatedVMs) {
                // Calculate share of each resource (relative to CURRENT remaining capacity)
                double cpuShare = c[j] / maxCPU;
                double ramShare = m[j] / maxRAM;
                double netShare = n[j] / maxNetwork;
                double diskShare = d[j] / maxDisk;

                // Dominant share is the MAXIMUM
                double dominantShare = Math.max(
                        Math.max(cpuShare, ramShare),
                        Math.max(netShare, diskShare)
                );

                vmList.add(new VMWithDRF(j, dominantShare));

                System.out.printf("VM%d\t%.4f\t\t%.4f\t\t%.4f\t\t%.4f\t\t%.4f\n",
                        j, cpuShare, ramShare, netShare, diskShare, dominantShare);
            }


            // ==================== STEP 3.3: Sort VMs by DRF (Ascending) ====================
            Collections.sort(vmList); // Smallest DRF first

            System.out.println("\nSorted order for this iteration (smallest DRF first):");
            for (int i = 0; i < Math.min(5, vmList.size()); i++) {
                VMWithDRF vm = vmList.get(i);
                System.out.printf("  %d. VM%d (DRF: %.4f)\n", i+1, vm.vmIndex, vm.dominantShare);
            }
            if (vmList.size() > 5) {
                System.out.printf("  ... and %d more VMs\n", vmList.size() - 5);
            }
            System.out.println();


            // ==================== STEP 3.4: Select VM with Smallest DRF ====================
            VMWithDRF selectedVM = vmList.get(0); // Pick the easiest VM (smallest DRF)
            int j = selectedVM.vmIndex;

            System.out.println("--------------------------------------------------------------------");
            System.out.printf("=> Selected VM%d (DRF: %.4f)\n", j, selectedVM.dominantShare);
            System.out.printf("  Requests: CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n\n",
                    c[j], m[j], n[j], d[j]);


            // ==================== STEP 3.5: Find Best Host Using L2 Norm ====================
            int bestHost = -1;
            double minL2 = Double.MAX_VALUE;

            System.out.println("  Evaluating hosts using L2 norm:");

            for (int i = 0; i < numHosts; i++) {
                // Check feasibility first
                boolean feasible = (remainingCPU[i] >= c[j]) &&
                        (remainingRAM[i] >= m[j]) &&
                        (remainingNet[i] >= n[j]) &&
                        (remainingDisk[i] >= d[j]);

                if (!feasible) {
                    System.out.printf("    Host%d: ✗ INFEASIBLE", i);

                    // Show what's missing
                    List<String> missing = new ArrayList<>();
                    if (remainingCPU[i] < c[j]) missing.add(String.format("CPU(need %.1f, have %.1f)", c[j], remainingCPU[i]));
                    if (remainingRAM[i] < m[j]) missing.add(String.format("RAM(need %.1f, have %.1f)", m[j], remainingRAM[i]));
                    if (remainingNet[i] < n[j]) missing.add(String.format("Net(need %.1f, have %.1f)", n[j], remainingNet[i]));
                    if (remainingDisk[i] < d[j]) missing.add(String.format("Disk(need %.1f, have %.1f)", d[j], remainingDisk[i]));
                    System.out.printf("\n               %s\n", String.join(", ", missing));

                    continue;
                }

                // Calculate residual after placing VM
                double residualCPU = remainingCPU[i] - c[j];
                double residualRAM = remainingRAM[i] - m[j];
                double residualNet = remainingNet[i] - n[j];
                double residualDisk = remainingDisk[i] - d[j];

                // Normalize residuals by ORIGINAL host capacity
                double normCPU = residualCPU / C[i];
                double normRAM = residualRAM / M[i];
                double normNet = residualNet / N[i];
                double normDisk = residualDisk / D[i];

                // Calculate L2 norm (Euclidean distance from zero)
                // Lower L2 = less waste/fragmentation = better fit
                double l2 = Math.sqrt(
                        normCPU * normCPU +
                                normRAM * normRAM +
                                normNet * normNet +
                                normDisk * normDisk
                );

                System.out.printf("    Host%d: FEASIBLE - L2 = %.4f", i, l2);
                System.out.printf("\n           Normalized residuals: CPU:%.3f RAM:%.3f Net:%.3f Disk:%.3f\n",
                        normCPU, normRAM, normNet, normDisk);
                System.out.printf("           Absolute residuals:   CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n",
                        residualCPU, residualRAM, residualNet, residualDisk);

                // Choose host with minimum L2 (best fit - least waste)
                if (l2 < minL2) {
                    minL2 = l2;
                    bestHost = i;
                }
            }

            System.out.println();


            // ==================== STEP 3.6: Allocate to Best Host ====================
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

                //
                if (drfDynamicL2Mig[j] == -1) {
                    drfDynamicL2Mig[j] = bestHost;
                } else if (drfDynamicL2Mig[j] != bestHost) {
                    drfDynamicL2Mig[j] = bestHost;
                    migrations++;
                }

                double utilization = ((C[bestHost]-remainingCPU[bestHost])/C[bestHost] +
                        (M[bestHost]-remainingRAM[bestHost])/M[bestHost] +
                        (N[bestHost]-remainingNet[bestHost])/N[bestHost] +
                        (D[bestHost]-remainingDisk[bestHost])/D[bestHost]) / 4.0 * 100.0;

                System.out.printf("     SUCCESS - VM%d ALLOCATED to Host%d ✓✓✓\n", j, bestHost);
                System.out.printf("      L2 score: %.4f (minimum waste)\n", minL2);
                System.out.printf("      Host%d new utilization: %.1f%%\n", bestHost, utilization);
                System.out.printf("      Host%d new state: CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n\n",
                        bestHost, remainingCPU[bestHost], remainingRAM[bestHost],
                        remainingNet[bestHost], remainingDisk[bestHost]);

            } else {
                System.out.printf("     FAILED - No feasible host for VM%d ✗✗✗\n", j);
                System.out.println("      This VM cannot be allocated with current resources.");
                System.out.println("      Removing from consideration and continuing...\n");

                // Remove this VM from consideration to avoid infinite loop
                unallocatedVMs.remove(j);
            }

            // Prevent infinite loops
            if (iteration > numVMs * 2) {
                System.out.println(" Maximum iterations reached. Stopping allocation.\n");
                break;
            }
        }


        // ==================== STEP 4: Calculate Final Statistics ====================
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


        // ==================== STEP 5: Print Summary ====================
        System.out.println("=================================================================");
        System.out.println("FINAL SUMMARY - DRF Dynamic + L2 Algorithm");
        System.out.println("=================================================================");
        System.out.printf("Total iterations: %d\n", iteration);
        System.out.printf("VMs successfully allocated: %d / %d (%.2f%%)\n",
                numAllocated, numVMs, (double)numAllocated/numVMs * 100.0);
        System.out.printf("VMs failed to allocate: %d\n", numVMs - numAllocated);

        System.out.println("\nResource Utilization:");
        System.out.printf("  CPU:     %.1f / %.1f (%.2f%%)\n", cpuUsed, cpuTotal, cpuUsed/cpuTotal*100);
        System.out.printf("  RAM:     %d / %d (%.2f%%)\n", ramUsed, ramTotal, (double)ramUsed/ramTotal*100);
        System.out.printf("  Network: %d / %d (%.2f%%)\n", netUsed, netTotal, (double)netUsed/netTotal*100);
        System.out.printf("  Disk:    %d / %d (%.2f%%)\n", diskUsed, diskTotal, (double)diskUsed/diskTotal*100);

        double avgUtilization = (cpuUsed/cpuTotal + (double)ramUsed/ramTotal +
                (double)netUsed/netTotal + (double)diskUsed/diskTotal) / 4.0 * 100.0;
        System.out.printf("  Average: %.2f%%\n", avgUtilization);

        double migrationRate = (numVMs > 0) ? ((double) migrations / numVMs) * 100.0 : 0.0;
        System.out.printf("\nMigrations: %d / %d VMs (%.2f%%)\n", migrations, numVMs, migrationRate);

        System.out.println("\nFinal Host States:");
        for (int i = 0; i < numHosts; i++) {
            double utilization = ((C[i]-remainingCPU[i])/C[i] +
                    (M[i]-remainingRAM[i])/M[i] +
                    (N[i]-remainingNet[i])/N[i] +
                    (D[i]-remainingDisk[i])/D[i]) / 4.0 * 100.0;
            int vmsOnHost = 0;
            for (int jj = 0; jj < numVMs; jj++) {
                if (drfDynamicL2Mig[jj] == i) vmsOnHost++;
            }
            System.out.printf("  Host%d: %2d VMs | %.1f%% utilized\n", i, vmsOnHost, utilization);
            System.out.printf("         Remaining: CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n",
                    remainingCPU[i], remainingRAM[i], remainingNet[i], remainingDisk[i]);
        }

        if (numAllocated < numVMs) {
            System.out.println("\nUnallocated VMs:");
            for (int jj = 0; jj < numVMs; jj++) {
                if (!allocated[jj]) {
                    System.out.printf("  VM%d - Requests: CPU:%.1f RAM:%.1f Net:%.1f Disk:%.1f\n",
                            jj, c[jj], m[jj], n[jj], d[jj]);
                }
            }
        }

        System.out.println("=============================================================================\n");


        // ==================== STEP 6: Write to CSV ====================
        final String file = "DRF_Dynamic_L2_Algorithm.csv";
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

        return migrations;
    }

    public static int DRFScarcityAlgorithm(double[] C, double[] M, double[] N, double[] D,
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

            List<VMWithScore> vmScores = new ArrayList<>();

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

                vmScores.add(new VMWithScore(j, finalScore, drf, scarcityWeighted));

                System.out.printf("VM%d\t%.4f\t\t%.4f\t\t%.4f\t\t%.4f\t\t%.4f\t%.4f\t\t%.4f\n",
                        j, normCPU, normRAM, normNet, normDisk, drf, scarcityWeighted, finalScore);
            }


            // ==================== STEP 4.3: Sort VMs by Score (Ascending) ====================
            Collections.sort(vmScores); // Smallest score first (easiest/fairest)

            System.out.println("\nSorted VM order (lowest qₘ first - easiest to place):");
            for (int i = 0; i < Math.min(5, vmScores.size()); i++) {
                VMWithScore vm = vmScores.get(i);
                System.out.printf("  %d. VM%d (qₘ=%.4f, DRF=%.4f, Scarcity=%.4f)\n",
                        i+1, vm.vmIndex, vm.score, vm.drf, vm.scarcityWeighted);
            }
            if (vmScores.size() > 5) {
                System.out.printf("  ... and %d more VMs\n", vmScores.size() - 5);
            }
            System.out.println();


            // ==================== STEP 4.4: Select VM with Lowest Score ====================
            VMWithScore selectedVM = vmScores.get(0);
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
        final String file = "DRF_Scarcity_Weighted_Algorithm.csv";
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

        return migrations;
    }

// ==================== Helper Classes ====================

    static class VMWithScore implements Comparable<VMWithScore> {
        int vmIndex;
        double score;
        double drf;
        double scarcityWeighted;

        public VMWithScore(int vmIndex, double score, double drf, double scarcityWeighted) {
            this.vmIndex = vmIndex;
            this.score = score;
            this.drf = drf;
            this.scarcityWeighted = scarcityWeighted;
        }

        @Override
        public int compareTo(VMWithScore other) {
            return Double.compare(this.score, other.score); // Ascending order
        }
    }

    // Helper method to visualize scarcity level
    private static String getScarcityIndicator(double remainingFraction) {
        if (remainingFraction > 0.7) return " Abundant";
        if (remainingFraction > 0.4) return " Moderate";
        if (remainingFraction > 0.2) return " Scarce";
        return " Critical";
    }

    public static int DRFScarcityAlgorithmTwo(double[] C, double[] M, double[] N, double[] D,
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

//        for (int i = 0; i < numHosts; i++) {
//            avgCPU += C[i];
//            avgRAM += M[i];
//            avgNet += N[i];
//            avgDisk += D[i];
//        }
//
//        avgCPU /= numHosts;
//        avgRAM /= numHosts;
//        avgNet /= numHosts;
//        avgDisk /= numHosts;

//        System.out.println("Average Host Capacities (for normalization):");
//        System.out.printf("  CPU: %.1f MIPS\n", avgCPU);
//        System.out.printf("  RAM: %.1f MB\n", avgRAM);
//        System.out.printf("  Network: %.1f Mbps\n", avgNet);
//        System.out.printf("  Disk: %.1f GB\n\n", avgDisk);


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
        final double ALPHA = 0.5; // 70% fairness, 30% scarcity
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

            List<VMWithScore> vmScores = new ArrayList<>();

            double avgCPU = 0, avgRAM = 0, avgNet = 0, avgDisk = 0;
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

                vmScores.add(new VMWithScore(j, finalScore, drf, scarcityWeighted));

                System.out.printf("VM%d\t%.4f\t\t%.4f\t\t%.4f\t\t%.4f\t\t%.4f\t%.4f\t\t%.4f\n",
                        j, normCPU, normRAM, normNet, normDisk, drf, scarcityWeighted, finalScore);
            }


            // ==================== STEP 4.3: Sort VMs by Score (Ascending) ====================
            Collections.sort(vmScores); // Smallest score first (easiest/fairest)

            System.out.println("\nSorted VM order (lowest qₘ first - easiest to place):");
            for (int i = 0; i < Math.min(5, vmScores.size()); i++) {
                VMWithScore vm = vmScores.get(i);
                System.out.printf("  %d. VM%d (qₘ=%.4f, DRF=%.4f, Scarcity=%.4f)\n",
                        i+1, vm.vmIndex, vm.score, vm.drf, vm.scarcityWeighted);
            }
            if (vmScores.size() > 5) {
                System.out.printf("  ... and %d more VMs\n", vmScores.size() - 5);
            }
            System.out.println();


            // ==================== STEP 4.4: Select VM with Lowest Score ====================
            VMWithScore selectedVM = vmScores.get(0);
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
                System.out.printf("    FAILED - No feasible host for VM%d \n", j);
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

        System.out.println("=============================================================\n");


        // ==================== STEP 7: Write to CSV ====================
        final String file = "DRFScarcityAlgorithmTwo.csv";
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

        return migrations;
    }

// ==================== Helper Classes ====================


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
            long sumSCP = 0, sumLRFF = 0, sumLRMF = 0, sumFF = 0, sumMF = 0, sumLF = 0, sumDRF = 0, sumL2 = 0, sumSC = 0, sumSC2 = 0;
            for(; NUM_VMS <= MAX_VMS; NUM_VMS += INCREMENT_VAL, row++) {
                // Hosts Specs


                System.out.println("  Testing with " +  NUM_VMS  + " VMs");
//                sumSCP += BranchAndBoundAlgorithm(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
                sumLRMF += LinearRelaxationAlgorithm(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
//                sumLRFF += LinearRelaxationAlgorithmFirstfit(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
                sumFF += algorithm(new SelectionPolicyFirstFit<>());
                sumMF += algorithm(new SelectionPolicyMostFull<>());
                sumLF += algorithm(new SelectionPolicyLeastFull<>());
                sumDRF += DRFAlgorithm(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
                sumL2 += DRFL2Algorithm(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
                sumSC += DRFScarcityAlgorithm(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
                sumSC2 += DRFScarcityAlgorithmTwo(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
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
            System.out.println("LF: " + sumDRF);
//            System.out.println("LF: " + sumLF);
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


