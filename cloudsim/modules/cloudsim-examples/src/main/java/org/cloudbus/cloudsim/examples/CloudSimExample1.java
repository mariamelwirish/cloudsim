package org.cloudbus.cloudsim.examples;

/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation
 *               of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009, The University of Melbourne, Australia
 */

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;

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

/**
 * A simple example showing how to create a data center with one host and run one cloudlet on it.
 */
public class CloudSimExample1 {
	public static DatacenterBroker broker;

	/** The cloudlet list. */
	private static List<Cloudlet> cloudletList;
	/** The vmlist. */
	private static List<Vm> vmlist;

	/**
	 * Creates main() to run this example.
	 *
	 * @param args the args
	 */
	public static void main(String[] args) {
		Log.println("Starting CloudSimExample1...");

		try {
			// First step: Initialize the CloudSim package. It should be called before creating any entities.
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance(); // Calendar whose fields have been initialized with the current date and time.
 			boolean trace_flag = false; // trace events

			/* Comment Start - Dinesh Bhagwat 
			 * Initialize the CloudSim library. 
			 * init() invokes initCommonVariable() which in turn calls initialize() (all these 3 methods are defined in CloudSim.java).
			 * initialize() creates two collections - an ArrayList of SimEntity Objects (named entities which denote the simulation entities) and 
			 * a LinkedHashMap (named entitiesByName which denote the LinkedHashMap of the same simulation entities), with name of every SimEntity as the key.
			 * initialize() creates two queues - a Queue of SimEvents (future) and another Queue of SimEvents (deferred). 
			 * initialize() creates a HashMap of of Predicates (with integers as keys) - these predicates are used to select a particular event from the deferred queue. 
			 * initialize() sets the simulation clock to 0 and running (a boolean flag) to false.
			 * Once initialize() returns (note that we are in method initCommonVariable() now), a CloudSimShutDown (which is derived from SimEntity) instance is created 
			 * (with numuser as 1, its name as CloudSimShutDown, id as -1, and state as RUNNABLE). Then this new entity is added to the simulation 
			 * While being added to the simulation, its id changes to 0 (from the earlier -1). The two collections - entities and entitiesByName are updated with this SimEntity.
			 * the shutdownId (whose default value was -1) is 0    
			 * Once initCommonVariable() returns (note that we are in method init() now), a CloudInformationService (which is also derived from SimEntity) instance is created 
			 * (with its name as CloudInformatinService, id as -1, and state as RUNNABLE). Then this new entity is also added to the simulation. 
			 * While being added to the simulation, the id of the SimEntitiy is changed to 1 (which is the next id) from its earlier value of -1. 
			 * The two collections - entities and entitiesByName are updated with this SimEntity.
			 * the cisId(whose default value is -1) is 1
			 * Comment End - Dinesh Bhagwat 
			 */
			CloudSim.init(num_user, calendar, trace_flag);

			// Second step: Create Datacenters
			// Datacenters are the resource providers in CloudSim. We need at
			// list one of them to run a CloudSim simulation
			Datacenter datacenter0 = createDatacenter("Datacenter_0");

			// Third step: Create Broker
			broker = new DatacenterBroker("Broker");;
			int brokerId = broker.getId();

			// Fourth step: Create one virtual machine
			vmlist = new ArrayList<>();

			// VM description
			int vmid = 0;
			int mips = 1000;
			long size = 10000; // image size (MB)
			int ram = 512; // vm memory (MB)
			long bw = 1000;
			int pesNumber = 1; // number of cpus
			String vmm = "Xen"; // VMM name

			// create VM
			Vm vm = new Vm(vmid, brokerId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerTimeShared());

			// add the VM to the vmList
			vmlist.add(vm);

			// submit vm list to the broker
			broker.submitGuestList(vmlist);

			// Fifth step: Create one Cloudlet
			cloudletList = new ArrayList<>();

			// Cloudlet properties
			int id = 0;
			long length = 400000;
			long fileSize = 300;
			long outputSize = 300;
			UtilizationModel utilizationModel = new UtilizationModelFull();

			Cloudlet cloudlet = new Cloudlet(id, length, pesNumber, fileSize,
                                        outputSize, utilizationModel, utilizationModel, 
                                        utilizationModel);
			cloudlet.setUserId(brokerId);
			cloudlet.setGuestId(vmid);

			// add the cloudlet to the list
			cloudletList.add(cloudlet);

			// submit cloudlet list to the broker
			broker.submitCloudletList(cloudletList);

			// Sixth step: Starts the simulation
			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			//Final step: Print results when simulation is over
			List<Cloudlet> newList = broker.getCloudletReceivedList();
			printCloudletList(newList);

			Log.println("CloudSimExample1 finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.println("Unwanted errors happen");
		}
	}

	/**
	 * Creates the datacenter.
	 *
	 * @param name the name
	 *
	 * @return the datacenter
	 */
	private static Datacenter createDatacenter(String name) {

		// Here are the steps needed to create a PowerDatacenter:
		// 1. We need to create a list to store
		// our machine
		List<Host> hostList = new ArrayList<>();

		// 2. A Machine contains one or more PEs or CPUs/Cores.
		// In this example, it will have only one core.
		List<Pe> peList = new ArrayList<>();

		int mips = 1000;

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerSimple(mips))); // need to store Pe id and MIPS Rating

		// 4. Create Host with its id and list of PEs and add them to the list
		// of machines
		int hostId = 0;
		int ram = 2048; // host memory (MB)
		long storage = 1000000; // host storage
		int bw = 10000;

		hostList.add(
			new Host(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerSimple(bw),
				storage,
				peList,
				new VmSchedulerTimeShared(peList)
			)
		); // This is our machine

		// 5. Create a DatacenterCharacteristics object that stores the
		// properties of a data center: architecture, OS, list of
		// Machines, allocation policy: time- or space-shared, time zone
		// and its price (G$/Pe time unit).
		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<>(); // we are not adding SAN
													// devices by now

		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
				arch, os, vmm, hostList, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		// 6. Finally, we need to create a PowerDatacenter object.
		Datacenter datacenter = null;
		try {
			datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return datacenter;
	}

	/**
	 * Prints the Cloudlet objects.
	 *
	 * @param list list of Cloudlets
	 */
	private static void printCloudletList(List<Cloudlet> list) {
		int size = list.size();
		Cloudlet cloudlet;

		String indent = "    ";
		Log.println();
		Log.println("========== OUTPUT ==========");
		Log.println("Cloudlet ID" + indent + "STATUS" + indent
				+ "Data center ID" + indent + "VM ID" + indent + "Time" + indent
				+ "Start Time" + indent + "Finish Time");

		DecimalFormat dft = new DecimalFormat("###.##");
		for (Cloudlet value : list) {
			cloudlet = value;
			Log.print(indent + cloudlet.getCloudletId() + indent + indent);

			if (cloudlet.getStatus() == Cloudlet.CloudletStatus.SUCCESS) {
				Log.print("SUCCESS");

				Log.println(indent + indent + cloudlet.getResourceId()
						+ indent + indent + indent + cloudlet.getGuestId()
						+ indent + indent
						+ dft.format(cloudlet.getActualCPUTime()) + indent
						+ indent + dft.format(cloudlet.getExecStartTime())
						+ indent + indent
						+ dft.format(cloudlet.getExecFinishTime()));
			}
		}
	}

    public static class AlgorithmsComparisonMigration {
        public static DatacenterBroker broker;

        /** The vmlist. */
        private static List<Vm> vmlist;

        /** Global Variables for all algorithms **/
        // General
        static int NUM_HOSTS = 4;
        static int INITIAL_VMS = 3;
        static int MAX_VMS = 25;
        static int INCREMENT_VAL = 3;
        static int MONTE_CARLO_ITERS = 1000;

        // Hosts Specs (will be kept constant for migration testing)
        static double[] C;
        static double[] M;
        static double[] N;
        static double[] D;

        // VMs Specs (will be expanded incrementally)
        static double[] c;
        static double[] m;
        static double[] n;
        static double[] d;

        // Migration tracking structures
        static class AllocationResult {
            int[][] allocation; // [host][vm] - which VMs are on which hosts
            boolean[] vmAllocated; // which VMs are successfully allocated
            int totalAllocated;
            int migrationCount;

            AllocationResult(int numHosts, int numVMs) {
                allocation = new int[numHosts][numVMs];
                vmAllocated = new boolean[numVMs];
                totalAllocated = 0;
                migrationCount = 0;

                // Initialize allocation matrix
                for (int i = 0; i < numHosts; i++) {
                    Arrays.fill(allocation[i], -1); // -1 means no VM
                }
            }
        }

        // Previous allocation state for migration tracking
        static AllocationResult previousResult;

        // VM migration history for tracing
        static List<String> migrationHistory;

        // Flag for CSVWriter append
        static boolean flag;

        // Enhanced heuristic algorithm that can perform migration
        public static AllocationResult enhancedHeuristicAlgorithm(SelectionPolicy<HostEntity> selectionPolicy, int currentVMs, AllocationResult previousAllocation) {
            AllocationResult result = new AllocationResult(NUM_HOSTS, currentVMs);

            try {
                // First step: Initialize the CloudSim package
                int num_user = 1;
                Calendar calendar = Calendar.getInstance();
                boolean trace_flag = false;

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

                // Create datacenter
                String arch = "x86", os = "Linux", vmm = "Xen";
                double time_zone = 10.0, cost = 3.0, costPerMem = 0.05, costPerStorage = 0.001, costPerBw = 0.0;
                LinkedList<Storage> storageList = new LinkedList<>();

                DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                        arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw
                );

                Datacenter datacenter = new Datacenter(name, characteristics,
                        new VmAllocationWithSelectionPolicy(hostList, selectionPolicy), storageList, 0);

                // Create broker
                broker = new DatacenterBroker("Broker");
                int brokerId = broker.getId();

                // ENHANCED: Perform migration-aware allocation
                if (previousAllocation != null && currentVMs > INITIAL_VMS) {
                    // Start with previous allocation and try to optimize
                    result = performMigrationAwareAllocation(selectionPolicy, currentVMs, previousAllocation);
                } else {
                    // First allocation - use standard approach
                    result = performStandardAllocation(selectionPolicy, currentVMs);
                }

                // Create VMs based on result
                vmlist = new ArrayList<>();
                for (int i = 0; i < currentVMs; i++) {
                    if (result.vmAllocated[i]) {
                        vmlist.add(new Vm(i, brokerId, c[i], 1, (int) m[i], (int) n[i], (int) d[i], vmm, new CloudletSchedulerTimeShared()));
                    }
                }

                broker.submitGuestList(vmlist);
                CloudSim.startSimulation();

                result.totalAllocated = broker.getAllocatedVMs();

                // Calculate utilization
                double cpuTotal = 0, cpuAvail = 0;
                long ramTotal = 0, ramAvail = 0, netTotal = 0, netAvail = 0, diskTotal = 0, diskAvail = 0;

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
                long ramUsed = ramTotal - ramAvail, netUsed = netTotal - netAvail, diskUsed = diskTotal - diskAvail;

                // Write results to CSV
                writeResultsToCSV(name + "_migration.csv", currentVMs, result.totalAllocated,
                        cpuUsed, cpuTotal, ramUsed, ramTotal, netUsed, netTotal, diskUsed, diskTotal, result.migrationCount);

                broker.clearDatacenters();
                CloudSim.stopSimulation();

                Log.println(name + " Finished! Migrations: " + result.migrationCount);

            } catch (Exception e) {
                e.printStackTrace();
            }

            return result;
        }

        // Migration-aware allocation that can move VMs for better optimization
        private static AllocationResult performMigrationAwareAllocation(SelectionPolicy<HostEntity> selectionPolicy, int currentVMs, AllocationResult previousAllocation) {
            AllocationResult result = new AllocationResult(NUM_HOSTS, currentVMs);

            // Copy previous allocation for VMs that were already allocated
            int prevVMs = previousAllocation.vmAllocated.length;
            for (int i = 0; i < Math.min(currentVMs, prevVMs); i++) {
                result.vmAllocated[i] = previousAllocation.vmAllocated[i];
            }

            // Try to allocate new VMs first using standard approach
            for (int vmId = prevVMs; vmId < currentVMs; vmId++) {
                int bestHost = selectBestHost(selectionPolicy, vmId, result);
                if (bestHost != -1) {
                    result.vmAllocated[vmId] = true;
                    addVMToHost(result, bestHost, vmId);
                    result.totalAllocated++;
                }
            }

            // If allocation failed for new VMs, try migration to create space
            for (int vmId = prevVMs; vmId < currentVMs; vmId++) {
                if (!result.vmAllocated[vmId]) {
                    if (tryMigrationForAllocation(selectionPolicy, vmId, result, previousAllocation)) {
                        result.vmAllocated[vmId] = true;
                        result.totalAllocated++;
                        result.migrationCount++;
                    }
                }
            }

            // Calculate migration count by comparing with previous allocation
            result.migrationCount += calculateMigrations(result, previousAllocation);

            return result;
        }

        // Standard allocation without migration consideration
        private static AllocationResult performStandardAllocation(SelectionPolicy<HostEntity> selectionPolicy, int currentVMs) {
            AllocationResult result = new AllocationResult(NUM_HOSTS, currentVMs);

            for (int vmId = 0; vmId < currentVMs; vmId++) {
                int bestHost = selectBestHost(selectionPolicy, vmId, result);
                if (bestHost != -1) {
                    result.vmAllocated[vmId] = true;
                    addVMToHost(result, bestHost, vmId);
                    result.totalAllocated++;
                }
            }

            return result;
        }

        // Select best host based on policy
        private static int selectBestHost(SelectionPolicy<HostEntity> selectionPolicy, int vmId, AllocationResult result) {
            String policyName = selectionPolicy.getClass().getSimpleName();

            for (int hostId = 0; hostId < NUM_HOSTS; hostId++) {
                if (canHostAccommodateVM(hostId, vmId, result)) {
                    if (policyName.contains("FirstFit")) {
                        return hostId; // First fit - return first available
                    }
                }
            }

            // For other policies, find the best among available hosts
            int bestHost = -1;
            double bestScore = -1;

            for (int hostId = 0; hostId < NUM_HOSTS; hostId++) {
                if (canHostAccommodateVM(hostId, vmId, result)) {
                    double score = calculateHostScore(hostId, result, policyName);
                    if (bestHost == -1 ||
                            (policyName.contains("MostFull") && score > bestScore) ||
                            (policyName.contains("LeastFull") && score < bestScore) ||
                            (policyName.contains("Random") && Math.random() > 0.5)) {
                        bestHost = hostId;
                        bestScore = score;
                    }
                }
            }

            return bestHost;
        }

        // Check if host can accommodate VM
        private static boolean canHostAccommodateVM(int hostId, int vmId, AllocationResult result) {
            double cpuUsed = 0, ramUsed = 0, netUsed = 0, diskUsed = 0;

            // Calculate current usage
            for (int i = 0; i < result.allocation[hostId].length; i++) {
                if (result.allocation[hostId][i] != -1) {
                    int allocatedVM = result.allocation[hostId][i];
                    cpuUsed += c[allocatedVM];
                    ramUsed += m[allocatedVM];
                    netUsed += n[allocatedVM];
                    diskUsed += d[allocatedVM];
                }
            }

            // Check if adding new VM would exceed capacity
            return (cpuUsed + c[vmId] <= C[hostId]) &&
                    (ramUsed + m[vmId] <= M[hostId]) &&
                    (netUsed + n[vmId] <= N[hostId]) &&
                    (diskUsed + d[vmId] <= D[hostId]);
        }

        // Calculate host utilization score for selection policies
        private static double calculateHostScore(int hostId, AllocationResult result, String policyName) {
            double cpuUsed = 0, ramUsed = 0, netUsed = 0, diskUsed = 0;

            for (int i = 0; i < result.allocation[hostId].length; i++) {
                if (result.allocation[hostId][i] != -1) {
                    int allocatedVM = result.allocation[hostId][i];
                    cpuUsed += c[allocatedVM];
                    ramUsed += m[allocatedVM];
                    netUsed += n[allocatedVM];
                    diskUsed += d[allocatedVM];
                }
            }

            // Return utilization percentage
            double cpuUtil = cpuUsed / C[hostId];
            double ramUtil = ramUsed / M[hostId];
            double netUtil = netUsed / N[hostId];
            double diskUtil = diskUsed / D[hostId];

            return (cpuUtil + ramUtil + netUtil + diskUtil) / 4.0; // Average utilization
        }

        // Add VM to host in allocation matrix
        private static void addVMToHost(AllocationResult result, int hostId, int vmId) {
            for (int i = 0; i < result.allocation[hostId].length; i++) {
                if (result.allocation[hostId][i] == -1) {
                    result.allocation[hostId][i] = vmId;
                    break;
                }
            }
        }

        // Try migration to create space for new VM
        private static boolean tryMigrationForAllocation(SelectionPolicy<HostEntity> selectionPolicy, int newVmId,
                                                         AllocationResult result, AllocationResult previousAllocation) {
            // Try to find a VM to migrate that would free up enough space
            for (int srcHost = 0; srcHost < NUM_HOSTS; srcHost++) {
                for (int i = 0; i < result.allocation[srcHost].length; i++) {
                    if (result.allocation[srcHost][i] != -1) {
                        int vmToMigrate = result.allocation[srcHost][i];

                        // Try migrating this VM to another host
                        for (int dstHost = 0; dstHost < NUM_HOSTS; dstHost++) {
                            if (dstHost != srcHost && canHostAccommodateVM(dstHost, vmToMigrate, result)) {
                                // Remove VM from source
                                result.allocation[srcHost][i] = -1;

                                // Check if new VM can fit in source host
                                if (canHostAccommodateVM(srcHost, newVmId, result)) {
                                    // Perform migration
                                    addVMToHost(result, dstHost, vmToMigrate);
                                    addVMToHost(result, srcHost, newVmId);

                                    // Log migration
                                    migrationHistory.add("VM " + vmToMigrate + " migrated from Host " + srcHost + " to Host " + dstHost);
                                    return true;
                                } else {
                                    // Restore VM to original host
                                    result.allocation[srcHost][i] = vmToMigrate;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }

        // Calculate number of migrations between two allocation states
        private static int calculateMigrations(AllocationResult current, AllocationResult previous) {
            if (previous == null) return 0;

            int migrations = 0;
            int prevVMs = Math.min(current.vmAllocated.length, previous.vmAllocated.length);

            for (int vmId = 0; vmId < prevVMs; vmId++) {
                if (current.vmAllocated[vmId] && previous.vmAllocated[vmId]) {
                    int currentHost = findVMHost(current, vmId);
                    int previousHost = findVMHost(previous, vmId);

                    if (currentHost != -1 && previousHost != -1 && currentHost != previousHost) {
                        migrations++;
                        migrationHistory.add("VM " + vmId + " migrated from Host " + previousHost + " to Host " + currentHost);
                    }
                }
            }

            return migrations;
        }

        // Find which host a VM is allocated to
        private static int findVMHost(AllocationResult result, int vmId) {
            for (int hostId = 0; hostId < NUM_HOSTS; hostId++) {
                for (int i = 0; i < result.allocation[hostId].length; i++) {
                    if (result.allocation[hostId][i] == vmId) {
                        return hostId;
                    }
                }
            }
            return -1;
        }

        public static AllocationResult BranchAndBoundAlgorithm(double[] C, double[] M, double[] N, double[] D,
                                                               double[] c, double[] m, double[] n, double[] d,
                                                               int numHosts, int currentVMs, AllocationResult previousAllocation) throws IOException {

            AllocationResult result = new AllocationResult(numHosts, currentVMs);

            // Set up linear program
            int numVars = numHosts * currentVMs;
            double[] objFun = new double[numVars];
            Arrays.fill(objFun, 1.0);
            LinearProgram lp = new LinearProgram(objFun);

            // Add constraints (same as before)
            addResourceConstraints(lp, C, M, N, D, c, m, n, d, numHosts, currentVMs);
            addAssignmentConstraints(lp, numHosts, currentVMs);

            // Set binary constraints
            for (int i = 0; i < numVars; i++) {
                lp.setBinary(i);
            }

            // Solve
            LinearProgramSolver solver = SolverFactory.newDefault();
            double[] solution = solver.solve(lp);

            if (solution != null) {
                // Extract allocation from solution
                for (int i = 0; i < numHosts; i++) {
                    int slotIndex = 0;
                    for (int j = 0; j < currentVMs; j++) {
                        int varIndex = i * currentVMs + j;
                        if (solution[varIndex] >= 0.5) {
                            result.allocation[i][slotIndex++] = j;
                            result.vmAllocated[j] = true;
                            result.totalAllocated++;
                        }
                    }
                }

                // Calculate migrations
                result.migrationCount = calculateMigrations(result, previousAllocation);
            }

            // Write results
            double[] utilization = calculateUtilization(result, C, M, N, D, c, m, n, d, numHosts);
            writeResultsToCSV("BranchAndBoundAlgorithm_migration.csv", currentVMs, result.totalAllocated,
                    utilization[0], utilization[1], (long)utilization[2], (long)utilization[3],
                    (long)utilization[4], (long)utilization[5], (long)utilization[6], (long)utilization[7], result.migrationCount);

            return result;
        }

        public static AllocationResult LinearRelaxationAlgorithm(double[] C, double[] M, double[] N, double[] D,
                                                                 double[] c, double[] m, double[] n, double[] d,
                                                                 int numHosts, int currentVMs, AllocationResult previousAllocation) throws IOException {

            AllocationResult result = new AllocationResult(numHosts, currentVMs);

            // Set up linear program (same as B&B but with relaxation)
            int numVars = numHosts * currentVMs;
            double[] objFun = new double[numVars];
            Arrays.fill(objFun, 1.0);
            LinearProgram lp = new LinearProgram(objFun);

            addResourceConstraints(lp, C, M, N, D, c, m, n, d, numHosts, currentVMs);
            addAssignmentConstraints(lp, numHosts, currentVMs);

            // Set bounds (relaxation)
            double[] lowerBounds = new double[numVars];
            double[] upperBounds = new double[numVars];
            Arrays.fill(upperBounds, 1.0);
            lp.setLowerbound(lowerBounds);
            lp.setUpperbound(upperBounds);

            // Solve and apply rounding + first-fit heuristic
            LinearProgramSolver solver = SolverFactory.newDefault();
            double[] solution = solver.solve(lp);

            if (solution != null) {
                // Phase 1: Round solutions > 0.5
                boolean[] allocated = new boolean[currentVMs];
                double[][] hostUsage = new double[numHosts][4]; // CPU, RAM, Net, Disk

                for (int i = 0; i < numHosts; i++) {
                    int slotIndex = 0;
                    for (int j = 0; j < currentVMs; j++) {
                        int varIndex = i * currentVMs + j;
                        if (solution[varIndex] > 0.5 && !allocated[j]) {
                            if (hostUsage[i][0] + c[j] <= C[i] && hostUsage[i][1] + m[j] <= M[i] &&
                                    hostUsage[i][2] + n[j] <= N[i] && hostUsage[i][3] + d[j] <= D[i]) {

                                result.allocation[i][slotIndex++] = j;
                                result.vmAllocated[j] = true;
                                allocated[j] = true;
                                result.totalAllocated++;

                                hostUsage[i][0] += c[j];
                                hostUsage[i][1] += m[j];
                                hostUsage[i][2] += n[j];
                                hostUsage[i][3] += d[j];
                            }
                        }
                    }
                }

                // Phase 2: First-fit for remaining VMs
                for (int j = 0; j < currentVMs; j++) {
                    if (!allocated[j]) {
                        for (int i = 0; i < numHosts; i++) {
                            if (hostUsage[i][0] + c[j] <= C[i] && hostUsage[i][1] + m[j] <= M[i] &&
                                    hostUsage[i][2] + n[j] <= N[i] && hostUsage[i][3] + d[j] <= D[i]) {

                                // Find next available slot
                                for (int slot = 0; slot < currentVMs; slot++) {
                                    if (result.allocation[i][slot] == -1) {
                                        result.allocation[i][slot] = j;
                                        break;
                                    }
                                }

                                result.vmAllocated[j] = true;
                                result.totalAllocated++;

                                hostUsage[i][0] += c[j];
                                hostUsage[i][1] += m[j];
                                hostUsage[i][2] += n[j];
                                hostUsage[i][3] += d[j];
                                break;
                            }
                        }
                    }
                }

                // Calculate migrations
                result.migrationCount = calculateMigrations(result, previousAllocation);
            }

            // Write results
            double[] utilization = calculateUtilization(result, C, M, N, D, c, m, n, d, numHosts);
            writeResultsToCSV("LinearRelaxationAlgorithm_migration.csv", currentVMs, result.totalAllocated,
                    utilization[0], utilization[1], (long)utilization[2], (long)utilization[3],
                    (long)utilization[4], (long)utilization[5], (long)utilization[6], (long)utilization[7], result.migrationCount);

            return result;
        }

        // Helper methods
        private static void addResourceConstraints(LinearProgram lp, double[] C, double[] M, double[] N, double[] D,
                                                   double[] c, double[] m, double[] n, double[] d, int numHosts, int currentVMs) {
            int numVars = numHosts * currentVMs;

            for (int i = 0; i < numHosts; i++) {
                // CPU constraint
                double[] cpuConstraint = new double[numVars];
                for (int j = 0; j < currentVMs; j++) {
                    cpuConstraint[i * currentVMs + j] = c[j];
                }
                lp.addConstraint(new LinearSmallerThanEqualsConstraint(cpuConstraint, C[i], "cpu_host_" + i));

                // RAM constraint
                double[] ramConstraint = new double[numVars];
                for (int j = 0; j < currentVMs; j++) {
                    ramConstraint[i * currentVMs + j] = m[j];
                }
                lp.addConstraint(new LinearSmallerThanEqualsConstraint(ramConstraint, M[i], "ram_host_" + i));

                // Network constraint
                double[] netConstraint = new double[numVars];
                for (int j = 0; j < currentVMs; j++) {
                    netConstraint[i * currentVMs + j] = n[j];
                }
                lp.addConstraint(new LinearSmallerThanEqualsConstraint(netConstraint, N[i], "network_host_" + i));

                // Disk constraint
                double[] diskConstraint = new double[numVars];
                for (int j = 0; j < currentVMs; j++) {
                    diskConstraint[i * currentVMs + j] = d[j];
                }
                lp.addConstraint(new LinearSmallerThanEqualsConstraint(diskConstraint, D[i], "disk_host_" + i));
            }
        }

        private static void addAssignmentConstraints(LinearProgram lp, int numHosts, int currentVMs) {
            for (int j = 0; j < currentVMs; j++) {
                double[] assignmentConstraint = new double[numHosts * currentVMs];
                for (int i = 0; i < numHosts; i++) {
                    assignmentConstraint[i * currentVMs + j] = 1.0;
                }
                lp.addConstraint(new LinearSmallerThanEqualsConstraint(assignmentConstraint, 1.0, "vm_assignment_" + j));
            }
        }

        private static double[] calculateUtilization(AllocationResult result, double[] C, double[] M, double[] N, double[] D,
                                                     double[] c, double[] m, double[] n, double[] d, int numHosts) {
            double cpuTotal = 0, cpuUsed = 0;
            long ramTotal = 0, ramUsed = 0, netTotal = 0, netUsed = 0, diskTotal = 0, diskUsed = 0;

            for (int i = 0; i < numHosts; i++) {
                cpuTotal += C[i];
                ramTotal += (long) M[i];
                netTotal += (long) N[i];
                diskTotal += (long) D[i];

                for (int j = 0; j < result.allocation[i].length; j++) {
                    if (result.allocation[i][j] != -1) {
                        int vmId = result.allocation[i][j];
                        cpuUsed += c[vmId];
                        ramUsed += (long) m[vmId];
                        netUsed += (long) n[vmId];
                        diskUsed += (long) d[vmId];
                    }
                }
            }

            return new double[]{cpuUsed, cpuTotal, ramUsed, ramTotal, netUsed, netTotal, diskUsed, diskTotal};
        }

        private static void writeResultsToCSV(String filename, int currentVMs, int allocated,
                                              double cpuUsed, double cpuTotal, long ramUsed, long ramTotal,
                                              long netUsed, long netTotal, long diskUsed, long diskTotal, int migrationCount) {
            try {
                Path RESULTS_DIR = Paths.get("../results/CSV Files/");
                java.nio.file.Files.createDirectories(RESULTS_DIR);
                Path outFile = RESULTS_DIR.resolve(filename);

                try (CSVWriter w = new CSVWriter(new FileWriter(outFile.toFile(), flag))) {
                    boolean header = java.nio.file.Files.notExists(outFile) || java.nio.file.Files.size(outFile) == 0;
                    if (header) {
                        w.writeNext(new String[]{
                                "currentVMs", "placedVMs", "allocRate", "cpuUtilRate", "ramUtilRate",
                                "netUtilRate", "diskUtilRate", "migrationCount", "migrationRate"
                        });
                    }

                    double migrationRate = currentVMs > 0 ? (double) migrationCount / currentVMs * 100.0 : 0.0;

                    w.writeNext(new String[]{
                            String.valueOf(currentVMs), String.valueOf(allocated),
                            String.valueOf((double) allocated / currentVMs * 100.0),
                            String.valueOf(cpuUsed / cpuTotal * 100.0),
                            String.valueOf((double) ramUsed / ramTotal * 100.0),
                            String.valueOf((double) netUsed / netTotal * 100.0),
                            String.valueOf((double) diskUsed / diskTotal * 100.0),
                            String.valueOf(migrationCount),
                            String.valueOf(migrationRate)
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Helper for random generation
        private static int randInt(int min, int max) {
            return min + (int) Math.floor(Math.random() * (max - min + 1));
        }

        // Generate host specs once per Monte Carlo iteration
        private static void randomizeHostSpecs() {
            final int C_MIN = 5000, C_MAX = 50000;
            final int M_MIN = 64000, M_MAX = 512000;
            final int N_MIN = 1000, N_MAX = 10000;
            final int D_MIN = 1000, D_MAX = 10000;

            for (int i = 0; i < NUM_HOSTS; i++) {
                C[i] = randInt(C_MIN, C_MAX);
                M[i] = randInt(M_MIN, M_MAX);
                N[i] = randInt(N_MIN, N_MAX);
                D[i] = randInt(D_MIN, D_MAX);
            }
        }

        // Generate all VM specs once per Monte Carlo iteration
        private static void randomizeAllVMSpecs() {
            final int c_MIN = 500, c_MAX = 5000;
            final int m_MIN = 16384, m_MAX = 64000;
            final int n_MIN = 100, n_MAX = 1000;
            final int d_MIN = 100, d_MAX = 1000;

            for (int j = 0; j < MAX_VMS; j++) {
                c[j] = randInt(c_MIN, c_MAX);
                m[j] = randInt(m_MIN, m_MAX);
                n[j] = randInt(n_MIN, n_MAX);
                d[j] = randInt(d_MIN, d_MAX);
            }
        }

        // Print VM migration history
        private static void printMigrationHistory(int mcIteration) {
            System.out.println("\n=== Migration History for Monte Carlo Iteration " + (mcIteration + 1) + " ===");
            if (migrationHistory.isEmpty()) {
                System.out.println("No migrations occurred in this iteration.");
            } else {
                for (String migration : migrationHistory) {
                    System.out.println(migration);
                }
            }
            System.out.println("Total migrations: " + migrationHistory.size());
            System.out.println("===============================================================\n");
        }

        // Print allocation state for debugging
        private static void printAllocationState(AllocationResult result, String algorithmName, int currentVMs) {
            System.out.println("\n--- " + algorithmName + " Allocation State (VMs: " + currentVMs + ") ---");
            for (int hostId = 0; hostId < NUM_HOSTS; hostId++) {
                System.out.print("Host " + hostId + ": ");
                boolean hasVMs = false;
                for (int i = 0; i < result.allocation[hostId].length; i++) {
                    if (result.allocation[hostId][i] != -1) {
                        System.out.print("VM" + result.allocation[hostId][i] + " ");
                        hasVMs = true;
                    }
                }
                if (!hasVMs) {
                    System.out.print("(empty)");
                }
                System.out.println();
            }
            System.out.println("Total allocated: " + result.totalAllocated + ", Migrations: " + result.migrationCount);
        }

        public static void main(String[] args) throws IOException {
            final int COL_SCP = 0, COL_LR = 1, COL_FF = 2, COL_MF = 3, COL_LF = 4, COL_RD = 5;
            int steps = ((MAX_VMS - INITIAL_VMS) / INCREMENT_VAL) + 1;

            System.out.println("Starting Migration Rate Testing");
            System.out.println("Hosts: " + NUM_HOSTS + ", Initial VMs: " + INITIAL_VMS + ", Max VMs: " + MAX_VMS);
            System.out.println("Increment: " + INCREMENT_VAL + ", Monte Carlo Iterations: " + MONTE_CARLO_ITERS);
            System.out.println("=================================================================\n");

            // Initialize arrays
            C = new double[NUM_HOSTS];
            M = new double[NUM_HOSTS];
            N = new double[NUM_HOSTS];
            D = new double[NUM_HOSTS];
            c = new double[MAX_VMS];
            m = new double[MAX_VMS];
            n = new double[MAX_VMS];
            d = new double[MAX_VMS];

            // Results tracking
            double[][][] allocationResults = new double[MONTE_CARLO_ITERS][steps][6]; // allocation rates
            double[][][] migrationResults = new double[MONTE_CARLO_ITERS][steps][6];  // migration rates

            for (int mcIter = 0; mcIter < MONTE_CARLO_ITERS; mcIter++) {
                System.out.println("Monte Carlo Iteration: " + (mcIter + 1) + "/" + MONTE_CARLO_ITERS);

                // Initialize migration history for this iteration
                migrationHistory = new ArrayList<>();

                // Generate host and VM specs once per iteration
                randomizeHostSpecs();
                randomizeAllVMSpecs();

                // Track results for each algorithm
                AllocationResult[] previousResults = new AllocationResult[6];
                AllocationResult[] currentResults = new AllocationResult[6];

                int stepIndex = 0;

                // Incrementally add VMs
                for (int currentVMs = INITIAL_VMS; currentVMs <= MAX_VMS; currentVMs += INCREMENT_VAL, stepIndex++) {
                    System.out.println("  Testing with " + currentVMs + " VMs");

                    try {
                        // Branch and Bound
                        currentResults[COL_SCP] = BranchAndBoundAlgorithm(C, M, N, D, c, m, n, d, NUM_HOSTS, currentVMs, previousResults[COL_SCP]);
                        allocationResults[mcIter][stepIndex][COL_SCP] = currentResults[COL_SCP].totalAllocated;
                        migrationResults[mcIter][stepIndex][COL_SCP] = currentResults[COL_SCP].migrationCount;

                        // Linear Relaxation
                        currentResults[COL_LR] = LinearRelaxationAlgorithm(C, M, N, D, c, m, n, d, NUM_HOSTS, currentVMs, previousResults[COL_LR]);
                        allocationResults[mcIter][stepIndex][COL_LR] = currentResults[COL_LR].totalAllocated;
                        migrationResults[mcIter][stepIndex][COL_LR] = currentResults[COL_LR].migrationCount;

                        // Enhanced heuristic algorithms (migration-aware)
                        currentResults[COL_FF] = enhancedHeuristicAlgorithm(new SelectionPolicyFirstFit<>(), currentVMs, previousResults[COL_FF]);
                        allocationResults[mcIter][stepIndex][COL_FF] = currentResults[COL_FF].totalAllocated;
                        migrationResults[mcIter][stepIndex][COL_FF] = currentResults[COL_FF].migrationCount;

                        currentResults[COL_MF] = enhancedHeuristicAlgorithm(new SelectionPolicyMostFull<>(), currentVMs, previousResults[COL_MF]);
                        allocationResults[mcIter][stepIndex][COL_MF] = currentResults[COL_MF].totalAllocated;
                        migrationResults[mcIter][stepIndex][COL_MF] = currentResults[COL_MF].migrationCount;

                        currentResults[COL_LF] = enhancedHeuristicAlgorithm(new SelectionPolicyLeastFull<>(), currentVMs, previousResults[COL_LF]);
                        allocationResults[mcIter][stepIndex][COL_LF] = currentResults[COL_LF].totalAllocated;
                        migrationResults[mcIter][stepIndex][COL_LF] = currentResults[COL_LF].migrationCount;

                        currentResults[COL_RD] = enhancedHeuristicAlgorithm(new SelectionPolicyRandomSelection<>(), currentVMs, previousResults[COL_RD]);
                        allocationResults[mcIter][stepIndex][COL_RD] = currentResults[COL_RD].totalAllocated;
                        migrationResults[mcIter][stepIndex][COL_RD] = currentResults[COL_RD].migrationCount;

                        // Enable CSV append after first write
                        if (!flag) flag = true;

                        // Optional: Print allocation states for debugging (uncomment if needed)
                        // printAllocationState(currentResults[COL_SCP], "Branch&Bound", currentVMs);
                        // printAllocationState(currentResults[COL_LR], "LinearRelax", currentVMs);

                    } catch (Exception e) {
                        System.err.println("Error in iteration " + mcIter + ", step " + stepIndex + ": " + e.getMessage());
                        e.printStackTrace();
                    }

                    // Copy current results to previous for next iteration
                    System.arraycopy(currentResults, 0, previousResults, 0, 6);
                }

                // Print migration history for this Monte Carlo iteration
                printMigrationHistory(mcIter);

                System.out.println("Completed Monte Carlo iteration " + (mcIter + 1));
                System.out.println("-----------------------------------------------------------------");

                // Reset flag for next iteration
                flag = false;
            }

            // Calculate and display final results
            System.out.println("\n\t\t\t=== Final Results ===\n");

            // Allocation Rate Results
            System.out.println("=== Allocation Rate Results (%) ===");
            System.out.println("numVMs\tBranch&Bound\tLinearRelax\tFirstFit\tMostFull\tLeastFull\tRandom");

            for (int step = 0; step < steps; step++) {
                int vmsAtStep = INITIAL_VMS + step * INCREMENT_VAL;
                System.out.print(vmsAtStep + "\t");

                for (int alg = 0; alg < 6; alg++) {
                    double totalAllocated = 0;
                    for (int mcIter = 0; mcIter < MONTE_CARLO_ITERS; mcIter++) {
                        totalAllocated += allocationResults[mcIter][step][alg];
                    }
                    double avgAllocationRate = (totalAllocated / (MONTE_CARLO_ITERS * vmsAtStep)) * 100.0;
                    System.out.printf("%.2f%%\t\t", avgAllocationRate);
                }
                System.out.println();
            }

            System.out.println("\n=== Migration Rate Results (avg migrations per step) ===");
            System.out.println("numVMs\tBranch&Bound\tLinearRelax\tFirstFit\tMostFull\tLeastFull\tRandom");

            for (int step = 0; step < steps; step++) {
                int vmsAtStep = INITIAL_VMS + step * INCREMENT_VAL;
                System.out.print(vmsAtStep + "\t");

                for (int alg = 0; alg < 6; alg++) {
                    double totalMigrations = 0;
                    for (int mcIter = 0; mcIter < MONTE_CARLO_ITERS; mcIter++) {
                        totalMigrations += migrationResults[mcIter][step][alg];
                    }
                    double avgMigrations = totalMigrations / MONTE_CARLO_ITERS;
                    System.out.printf("%.2f\t\t", avgMigrations);
                }
                System.out.println();
            }

            // Migration Rate as percentage
            System.out.println("\n=== Migration Rate Results (% of VMs migrated per step) ===");
            System.out.println("numVMs\tBranch&Bound\tLinearRelax\tFirstFit\tMostFull\tLeastFull\tRandom");

            for (int step = 0; step < steps; step++) {
                int vmsAtStep = INITIAL_VMS + step * INCREMENT_VAL;
                System.out.print(vmsAtStep + "\t");

                for (int alg = 0; alg < 6; alg++) {
                    double totalMigrations = 0;
                    for (int mcIter = 0; mcIter < MONTE_CARLO_ITERS; mcIter++) {
                        totalMigrations += migrationResults[mcIter][step][alg];
                    }
                    double avgMigrationRate = (totalMigrations / (MONTE_CARLO_ITERS * vmsAtStep)) * 100.0;
                    System.out.printf("%.2f%%\t\t", avgMigrationRate);
                }
                System.out.println();
            }

            System.out.println("\n=== Testing Complete ===");
        }
    }
}