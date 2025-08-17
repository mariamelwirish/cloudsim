package org.cloudbus.cloudsim.examples;
import com.opencsv.CSVWriter;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.HostEntity;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.selectionPolicies.*;
import scpsolver.constraints.LinearSmallerThanEqualsConstraint;
import scpsolver.lpsolver.LinearProgramSolver;
import scpsolver.lpsolver.SolverFactory;
import scpsolver.problems.LinearProgram;
import java.io.FileWriter;
import java.util.*;

public class AlgorithmsComparison {
    public static DatacenterBroker broker;

    /** The vmlist. */
    private static List<Vm> vmlist;

    /** Global Variables for all algorithms **/
    // General
    static final int NUM_HOSTS =4;
    static final int NUM_VMS = 25;
    static final int MONTE_CARLO_ITERS = 100;

    // Hosts Specs
    static double[] C = new double[NUM_HOSTS];
    static double[] M = new double[NUM_HOSTS];
    static double[] N = new double[NUM_HOSTS];
    static double[] D = new double[NUM_HOSTS];

    // VMs Specs
    static double[] c = new double[NUM_VMS];
    static double[] m = new double[NUM_VMS];
    static double[] n = new double[NUM_VMS];
    static double[] d = new double[NUM_VMS];

    // Flag for CSVWriter append
    static boolean flag;

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

                diskTotal = ((Host) h).getMaxStorage();
                diskAvail += h.getStorage();
            }

            double cpuUsed = cpuTotal - cpuAvail;
            long   ramUsed = ramTotal - ramAvail;
            long   netUsed = netTotal - netAvail;
            long   diskUsed = diskTotal - diskAvail;

            String file = name + ".csv";
            java.io.File f = new java.io.File(file);
            try (CSVWriter w = new CSVWriter(new FileWriter(file, flag))) {
                if (!f.exists() || f.length() == 0) {
                    w.writeNext(new String[]{
                            "placedVMs",
                            "cpuUsed","cpuTotal",
                            "ramUsedMB","ramTotalMB",
                            "netUsedMBps","netTotalMBps",
                            "diskUsedMB","diskTotalMB"
                    });
                }
                w.writeNext(new String[]{
                        String.valueOf(allocated),
                        String.valueOf((long)cpuUsed), String.valueOf((long)cpuTotal),
                        String.valueOf(ramUsed),        String.valueOf(ramTotal),
                        String.valueOf(netUsed),        String.valueOf(netTotal),
                        String.valueOf(diskUsed),       String.valueOf(diskTotal)
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
        return allocated;
    }

    public static int SCPSolver(double[] C, double[] M, double[] N, double[] D, double[] c, double[] m, double[] n, double[] d, int numHosts, int numVMs) {

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
                    // Calculating total used sources for CSVWriter
                    cpuUsed  += cpu;
                    ramUsed  += (long) ram;
                    bwUsed  += (long) bw;
                    diskUsed += (long) disk;
                }
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

        final String file = "SCPSolver.csv";
        java.io.File f = new java.io.File(file);
        try (com.opencsv.CSVWriter w = new com.opencsv.CSVWriter(new java.io.FileWriter(file, flag))) {
            if (!f.exists() || f.length() == 0) {
                w.writeNext(new String[]{
                        "placedVMs",
                        "cpuUsed","cpuTotal",
                        "ramUsedMB","ramTotalMB",
                        "netUsedMBps","netTotalMBps",
                        "diskUsedMB","diskTotalMB"
                });
            }
            w.writeNext(new String[]{
                    String.valueOf(num_allocated),
                    String.valueOf((long)cpuUsed),  String.valueOf((long)cpuTotal),
                    String.valueOf(ramUsed),        String.valueOf(ramTotal),
                    String.valueOf(bwUsed),        String.valueOf(bwTotal),
                    String.valueOf(diskUsed),       String.valueOf(diskTotal)
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        return num_allocated;


    }

    // helper for the random function
    private static int randInt(int min, int max) {
        return min + (int) Math.floor(Math.random() * (max - min + 1));
    }

    // randomize specs for hosts and VMs.
    private static void randomizeSpecs() {
        // Hosts Ranges
        final int C_MIN      = 1000;
        final int C_MAX      = 10000;

        final int M_MIN    = 64000;
        final int M_MAX    = 512000;

        final int N_MIN      = 1000;
        final int N_MAX      = 10000;

        final int D_MIN        = 1000;
        final int D_MAX        = 10000;

        // VMs Ranges
        final int c_MIN      = 500;
        final int c_MAX      = 5000;

        final int m_MIN    = 1000;
        final int m_MAX    = 64000;

        final int n_MIN      = 100;
        final int n_MAX      = 1000;

        final int d_MIN        = 100;
        final int d_MAX        = 1000;

        // Hosts Random Assignment
        for (int i = 0; i < NUM_HOSTS; i++) {
            C[i] = randInt(C_MIN, C_MAX);
            M[i] = randInt(M_MIN, M_MAX);
            N[i] = randInt(N_MIN, N_MAX);
            D[i] = randInt(D_MIN, D_MAX);
        }

        // VMs Random Assignment
        for (int j = 0; j < NUM_VMS; j++) {
            c[j] = randInt(c_MIN, c_MAX);
            m[j] = randInt(m_MIN, m_MAX);
            n[j] = randInt(n_MIN, n_MAX);
            d[j] = randInt(d_MIN, d_MAX);
        }
    }

    public static void main(String[] args) {
        long sumSCP = 0, sumFF = 0, sumMF = 0, sumLF = 0, sumRD = 0;

        for (int t = 0; t < MONTE_CARLO_ITERS; t++) {
            randomizeSpecs();
            sumSCP  += SCPSolver(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
            sumFF += algorithm(new SelectionPolicyFirstFit<>());
            sumMF += algorithm(new SelectionPolicyMostFull<>());
            sumLF += algorithm(new SelectionPolicyLeastFull<>());
            sumRD += algorithm(new SelectionPolicyRandomSelection<>());
            if(!flag) flag = true;
        }
        flag = false;

        double totalReq = MONTE_CARLO_ITERS * NUM_VMS; // total VMs requested across all runs

        System.out.println("\n=== Monte Carlo Results over " + MONTE_CARLO_ITERS + " runs ===");
        System.out.printf("SCPSolver  avg rate: %.2f%%%n", 100.0 * sumSCP / totalReq);
        System.out.printf("FirstFit  avg rate: %.2f%%%n", 100.0 * sumFF  / totalReq);
        System.out.printf("MostFull  avg rate: %.2f%%%n", 100.0 * sumMF  / totalReq);
        System.out.printf("LeastFull avg rate: %.2f%%%n", 100.0 * sumLF  / totalReq);
        System.out.printf("Random    avg rate: %.2f%%%n", 100.0 * sumRD  / totalReq);
    }




}

