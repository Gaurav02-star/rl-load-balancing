package org.gau1;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

public class LoadBalancingSimulation {

    private static List<Cloudlet> cloudletList;
    private static List<Vm> vmlist;

    public static void main(String[] args) {
        Log.printLine("Starting LoadBalancingSimulation...");

        try {
            // Step 1: Initialize CloudSim
            int numUser = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;

            CloudSim.init(numUser, calendar, traceFlag);

            // Step 2: Create Datacenter
            Datacenter datacenter0 = createDatacenter("Datacenter_0");

            // Step 3: Create Broker
            DatacenterBroker broker = createBroker();
            int brokerId = broker.getId();

            // Step 4: Create multiple VMs
            vmlist = new ArrayList<Vm>();

            int mips = 1000;
            long size = 10000;     // image size (MB)
            int ram = 1024;        // VM memory (MB)
            long bw = 1000;
            int pesNumber = 1;     // number of CPU cores per VM
            String vmm = "Xen";

            int numberOfVms = 4;

            for (int i = 0; i < numberOfVms; i++) {
                Vm vm = new Vm(
                        i,
                        brokerId,
                        mips,
                        pesNumber,
                        ram,
                        bw,
                        size,
                        vmm,
                        new CloudletSchedulerTimeShared()
                );
                vmlist.add(vm);
            }

            broker.submitVmList(vmlist);

            // Step 5: Create multiple Cloudlets
            cloudletList = new ArrayList<Cloudlet>();

            long fileSize = 300;
            long outputSize = 300;
            UtilizationModel utilizationModel = new UtilizationModelFull();

            int numberOfCloudlets = 20;

            for (int i = 0; i < numberOfCloudlets; i++) {
                long length;

                // Different task sizes for basic workload variation
                if (i % 3 == 0) {
                    length = 1000;
                } else if (i % 3 == 1) {
                    length = 2000;
                } else {
                    length = 3000;
                }

                Cloudlet cloudlet = new Cloudlet(
                        i,
                        length,
                        pesNumber,
                        fileSize,
                        outputSize,
                        utilizationModel,
                        utilizationModel,
                        utilizationModel
                );

                cloudlet.setUserId(brokerId);
                cloudletList.add(cloudlet);
            }

            broker.submitCloudletList(cloudletList);

            // Step 6: Start Simulation
            CloudSim.startSimulation();

            List<Cloudlet> newList = broker.getCloudletReceivedList();

            CloudSim.stopSimulation();

            // Step 7: Print results
            printCloudletList(newList);

            Log.printLine("LoadBalancingSimulation finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("An error occurred during the simulation.");
        }
    }

    private static Datacenter createDatacenter(String name) {

        List<Host> hostList = new ArrayList<Host>();

        int mips = 1000;

        // -------- Host 0 --------
        List<Pe> peList1 = new ArrayList<Pe>();
        peList1.add(new Pe(0, new PeProvisionerSimple(mips)));
        peList1.add(new Pe(1, new PeProvisionerSimple(mips)));

        int hostId0 = 0;
        int ram0 = 4096;          // host memory (MB)
        long storage0 = 1000000;  // host storage
        int bw0 = 10000;

        hostList.add(
                new Host(
                        hostId0,
                        new RamProvisionerSimple(ram0),
                        new BwProvisionerSimple(bw0),
                        storage0,
                        peList1,
                        new VmSchedulerTimeShared(peList1)
                )
        );

        // -------- Host 1 --------
        List<Pe> peList2 = new ArrayList<Pe>();
        peList2.add(new Pe(0, new PeProvisionerSimple(mips)));
        peList2.add(new Pe(1, new PeProvisionerSimple(mips)));

        int hostId1 = 1;
        int ram1 = 4096;
        long storage1 = 1000000;
        int bw1 = 10000;

        hostList.add(
                new Host(
                        hostId1,
                        new RamProvisionerSimple(ram1),
                        new BwProvisionerSimple(bw1),
                        storage1,
                        peList2,
                        new VmSchedulerTimeShared(peList2)
                )
        );

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double timeZone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;

        LinkedList<Storage> storageList = new LinkedList<Storage>();

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                arch,
                os,
                vmm,
                hostList,
                timeZone,
                cost,
                costPerMem,
                costPerStorage,
                costPerBw
        );

        Datacenter datacenter = null;
        try {
            datacenter = new Datacenter(
                    name,
                    characteristics,
                    new VmAllocationPolicySimple(hostList),
                    storageList,
                    0
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return datacenter;
    }

    private static DatacenterBroker createBroker() {
        DatacenterBroker broker = null;
        try {
            broker = new DatacenterBroker("Broker");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return broker;
    }

    private static void printCloudletList(List<Cloudlet> list) {
        int size = list.size();
        Cloudlet cloudlet;

        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent
                + "Data center ID" + indent + "VM ID" + indent + "Time" + indent
                + "Start Time" + indent + "Finish Time");

        DecimalFormat dft = new DecimalFormat("###.##");

        for (int i = 0; i < size; i++) {
            cloudlet = list.get(i);
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);

            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                Log.print("SUCCESS");
                Log.printLine(indent + indent + cloudlet.getResourceId()
                        + indent + indent + indent + cloudlet.getVmId()
                        + indent + indent + dft.format(cloudlet.getActualCPUTime())
                        + indent + indent + dft.format(cloudlet.getExecStartTime())
                        + indent + indent + dft.format(cloudlet.getFinishTime()));
            }
        }
    }
}