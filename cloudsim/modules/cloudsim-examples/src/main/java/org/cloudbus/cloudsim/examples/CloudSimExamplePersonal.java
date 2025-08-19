package org.cloudbus.cloudsim.examples;

/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation
 *               of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009, The University of Melbourne, Australia
 */

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 * A simple example showing how to create a data center with one host and run one cloudlet on it.
 */
public class CloudSimExamplePersonal {
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
			Vm vm0 = new Vm(0, brokerId, 2500, pesNumber, 512, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm1 = new Vm(1, brokerId, 3000, pesNumber, 1024, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm2 = new Vm(2, brokerId, 1500, pesNumber, 512, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm3 = new Vm(3, brokerId, 4000, 2, 2048, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm4 = new Vm(4, brokerId, 2000, pesNumber, 512, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm5 = new Vm(5, brokerId, 3500, 2, 1024, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm6 = new Vm(6, brokerId, 1800, pesNumber, 768, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm7 = new Vm(7, brokerId, 2200, pesNumber, 512, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm8 = new Vm(8, brokerId, 2800, 2, 1024, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm9 = new Vm(9, brokerId, 2600, pesNumber, 512, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm10 = new Vm(10, brokerId, 500, pesNumber, 256, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm11 = new Vm(11, brokerId, 3200, pesNumber, 1024, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm12 = new Vm(12, brokerId, 4500, 2, 2048, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm13 = new Vm(13, brokerId, 1000, pesNumber, 512, bw, size, vmm, new CloudletSchedulerTimeShared());
			Vm vm14  = new Vm(14, brokerId, 3000, pesNumber, 1024, bw, size, vmm, new CloudletSchedulerTimeShared());

			// add the VM to the vmList
			vmlist.add(vm0);
			vmlist.add(vm1);
			vmlist.add(vm2);
			vmlist.add(vm3);
			vmlist.add(vm4);
			vmlist.add(vm5);
			vmlist.add(vm6);
			vmlist.add(vm7);
			vmlist.add(vm8);
			vmlist.add(vm9);
			vmlist.add(vm10);
			vmlist.add(vm11);
			vmlist.add(vm12);
			vmlist.add(vm13);
			vmlist.add(vm14);

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
		List<Host> hostList = new ArrayList<>();

//		// === HOST 0 ===
//		List<Pe> peList0 = new ArrayList<>();
//		for (int i = 0; i < 4; i++) peList0.add(new Pe(i, new PeProvisionerSimple(10000)));
//		hostList.add(new Host(
//				0,
//				new RamProvisionerSimple(8192),
//				new BwProvisionerSimple(10000),
//				1000000,
//				peList0,
//				new VmSchedulerTimeShared(peList0)
//		));

		// === HOST 1 ===
		List<Pe> peList1 = new ArrayList<>();
		for (int i = 0; i < 2; i++) peList1.add(new Pe(i, new PeProvisionerSimple(8000)));
		hostList.add(new Host(
				1,
				new RamProvisionerSimple(4096),
				new BwProvisionerSimple(10000),
				1000000,
				peList1,
				new VmSchedulerTimeShared(peList1)
		));

//		// === HOST 2 ===
//		List<Pe> peList2 = new ArrayList<>();
//		for (int i = 0; i < 4; i++) peList2.add(new Pe(i, new PeProvisionerSimple(6000)));
//		hostList.add(new Host(
//				2,
//				new RamProvisionerSimple(6144),
//				new BwProvisionerSimple(10000),
//				1000000,
//				peList2,
//				new VmSchedulerTimeShared(peList2)
//		));

		// === HOST 3 ===
		List<Pe> peList3 = new ArrayList<>();
		for (int i = 0; i < 2; i++) peList3.add(new Pe(i, new PeProvisionerSimple(5000)));
		hostList.add(new Host(
				3,
				new RamProvisionerSimple(2048),
				new BwProvisionerSimple(10000),
				1000000,
				peList3,
				new VmSchedulerTimeShared(peList3)
		));

		// === HOST 4 ===
		List<Pe> peList4 = new ArrayList<>();
		peList4.add(new Pe(0, new PeProvisionerSimple(4000)));
		hostList.add(new Host(
				4,
				new RamProvisionerSimple(1024),
				new BwProvisionerSimple(10000),
				1000000,
				peList4,
				new VmSchedulerTimeShared(peList4)
		));

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

}