package org;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
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

/**
 * SimulationRunner.java
 * Supports static runs and event-driven dynamic runs with autoscaling while preventing memory leaks.
 */
public class SimulationRunner {

    private SimulationRunner() {}

    public static SimulationResult run(AssignmentStrategy strategy, String strategyName) {
        try {
            // Disable event tracing to conserve JVM memory
            CloudSim.init(SimulationConfig.NUM_USERS, Calendar.getInstance(), false);

            @SuppressWarnings("unused")
            Datacenter datacenter = createDatacenter("Datacenter_0");

            DatacenterBroker broker = new DatacenterBroker("Broker_0");
            int brokerId = broker.getId();

            List<Vm> vms = createVms(brokerId);
            broker.submitVmList(vms);

            List<Cloudlet> cloudlets = createCloudlets(brokerId);
            strategy.assign(cloudlets, vms);

            broker.submitCloudletList(cloudlets);

            CloudSim.startSimulation();

            List<Cloudlet> completed = broker.getCloudletReceivedList();

            CloudSim.stopSimulation();

            return new SimulationResult(strategyName, completed, vms);

        } catch (Exception e) {
            throw new RuntimeException("Simulation run failed for strategy: " + strategyName, e);
        }
    }

    public static DynamicSimulationResult runDynamic(AssignmentStrategy strategy, String strategyName) {
        return runDynamic(strategy, strategyName, null);
    }

    public static DynamicSimulationResult runDynamic(AssignmentStrategy strategy, String strategyName, Autoscaler autoscaler) {
        try {
            // Disable event tracing to conserve JVM memory
            CloudSim.init(SimulationConfig.NUM_USERS, Calendar.getInstance(), false);

            Datacenter datacenter = createDatacenter("Datacenter_Dynamic");

            DynamicBroker broker = new DynamicBroker("DynamicBroker");
            int brokerId = broker.getId();

            List<Vm> vms = createVms(brokerId);
            broker.submitVmList(vms);

            CloudSimGateway gateway = new CloudSimGateway(broker);
            DynamicScheduler scheduler = new DynamicScheduler(strategy, gateway);

            VmLifecycleManager lifecycleManager = new VmLifecycleManager(broker, datacenter.getId(), vms.size());

            ArrivalDistribution distribution = new PoissonArrival(
                    SimulationConfig.MEAN_ARRIVAL_RATE,
                    SimulationConfig.WORKLOAD_RANDOM_SEED
            );

            WorkloadGenerator workloadGenerator = new WorkloadGenerator(
                    "WorkloadGenerator",
                    distribution,
                    scheduler,
                    SimulationConfig.WORKLOAD_DURATION,
                    SimulationConfig.WORKLOAD_RANDOM_SEED,
                    brokerId
            );

            MonitoringModule monitoringModule = new MonitoringModule(
                    "MonitoringModule",
                    gateway,
                    workloadGenerator,
                    lifecycleManager,
                    autoscaler,
                    SimulationConfig.MONITORING_SAMPLE_INTERVAL,
                    SimulationConfig.MONITORING_WINDOW_SIZE,
                    SimulationConfig.RESPONSE_TIME_SLA
            );

            CloudSim.startSimulation();

            List<Cloudlet> completed = broker.getCompletedCloudletList();

            CloudSim.stopSimulation();

            return new DynamicSimulationResult(
                    strategyName,
                    completed,
                    vms,
                    monitoringModule.getHistory(),
                    workloadGenerator.getCloudletArrivalTimes(),
                    workloadGenerator.getTotalArrivals()
            );

        } catch (Exception e) {
            throw new RuntimeException("Dynamic simulation run failed for strategy: " + strategyName, e);
        }
    }

    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hostList = new ArrayList<>();

        for (int h = 0; h < SimulationConfig.NUM_HOSTS; h++) {
            List<Pe> peList = new ArrayList<>();
            for (int p = 0; p < SimulationConfig.HOST_PES; p++) {
                peList.add(new Pe(p, new PeProvisionerSimple(SimulationConfig.HOST_MIPS)));
            }

            hostList.add(new Host(
                    h,
                    new RamProvisionerSimple(SimulationConfig.HOST_RAM),
                    new BwProvisionerSimple(SimulationConfig.HOST_BW),
                    SimulationConfig.HOST_STORAGE,
                    peList,
                    new VmSchedulerTimeShared(peList)
            ));
        }

        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
                SimulationConfig.DC_ARCH,
                SimulationConfig.DC_OS,
                SimulationConfig.DC_VMM,
                hostList,
                SimulationConfig.DC_TIME_ZONE,
                SimulationConfig.DC_COST,
                SimulationConfig.DC_COST_MEM,
                SimulationConfig.DC_COST_STORAGE,
                SimulationConfig.DC_COST_BW
        );

        return new Datacenter(
                name,
                characteristics,
                new VmAllocationPolicySimple(hostList),
                new LinkedList<Storage>(),
                0
        );
    }

    private static List<Vm> createVms(int brokerId) {
        List<Vm> vms = new ArrayList<>();
        for (int i = 0; i < SimulationConfig.NUM_VMS; i++) {
            int mips = SimulationConfig.VM_MIPS_VALUES[i % SimulationConfig.VM_MIPS_VALUES.length];

            vms.add(new Vm(
                    i,
                    brokerId,
                    mips,
                    SimulationConfig.VM_PES,
                    SimulationConfig.VM_RAM,
                    SimulationConfig.VM_BW,
                    SimulationConfig.VM_SIZE,
                    SimulationConfig.VM_VMM,
                    new CloudletSchedulerTimeShared()
            ));
        }
        return vms;
    }

    private static List<Cloudlet> createCloudlets(int brokerId) {
        List<Cloudlet> cloudlets = new ArrayList<>();
        UtilizationModel um = new UtilizationModelFull();
        Random rng = new Random(SimulationConfig.CLOUDLET_SEED);

        for (int i = 0; i < SimulationConfig.NUM_CLOUDLETS; i++) {
            int tier = i % 3;
            long minLen, maxLen;

            if (tier == 0) {
                minLen = SimulationConfig.CL_LENGTH_SHORT_MIN;
                maxLen = SimulationConfig.CL_LENGTH_SHORT_MAX;
            } else if (tier == 1) {
                minLen = SimulationConfig.CL_LENGTH_MEDIUM_MIN;
                maxLen = SimulationConfig.CL_LENGTH_MEDIUM_MAX;
            } else {
                minLen = SimulationConfig.CL_LENGTH_LONG_MIN;
                maxLen = SimulationConfig.CL_LENGTH_LONG_MAX;
            }

            long length = minLen + (long) (rng.nextDouble() * (maxLen - minLen));

            Cloudlet cl = new Cloudlet(
                    i,
                    length,
                    SimulationConfig.CL_PES,
                    SimulationConfig.CL_FILE_SIZE,
                    SimulationConfig.CL_OUTPUT_SIZE,
                    um, um, um
            );
            cl.setUserId(brokerId);
            cloudlets.add(cl);
        }
        return cloudlets;
    }
}