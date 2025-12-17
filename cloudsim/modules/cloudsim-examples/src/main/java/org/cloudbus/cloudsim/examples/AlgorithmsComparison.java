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
import java.util.concurrent.*;

import org.cloudbus.cloudsim.examples.algorithms.*;

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
    static int MONTE_CARLO_ITERS = 1000;

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
    public static boolean flag;

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

    public static class VMAllocation implements Comparable<VMAllocation> {
        public int vmIndex;
        public int hostIndex;
        public double allocationValue;

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

    public static class VMWithScore implements Comparable<VMWithScore> {
        public int vmIndex;
        public double score;
        public double drf;
        public double scarcityWeighted;

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
    public static String getScarcityIndicator(double remainingFraction) {
        if (remainingFraction > 0.7) return " Abundant";
        if (remainingFraction > 0.4) return " Moderate";
        if (remainingFraction > 0.2) return " Scarce";
        return " Critical";
    }




    public static void main(String[] args) throws IOException {
        int START_VMS = NUM_VMS;
        for (int t = 0; t < MONTE_CARLO_ITERS; t++) {
            NUM_VMS = START_VMS;

            // Host Specs
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
            for(; NUM_VMS <= MAX_VMS; NUM_VMS += INCREMENT_VAL) {
                System.out.println("  Testing with " +  NUM_VMS  + " VMs");
                algorithm(new SelectionPolicyFirstFit<>());
                algorithm(new SelectionPolicyMostFull<>());
                algorithm(new SelectionPolicyLeastFull<>());
                new BranchAndBoundAlgorithm(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
                new LinearRelaxationAlgorithm(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS, false);
                new DRFScarcityAlgorithm(C, M, N, D, c, m, n, d, NUM_HOSTS, NUM_VMS);
//                sumRD += algorithm(new SelectionPolicyRandomSelection<>());
                if(!flag) flag = true;
            }
        }
        flag = false;


    }
}


