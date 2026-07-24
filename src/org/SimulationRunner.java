package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicySimple;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.*;

public class SimulationRunner {

    public static DynamicSimulationResult runDynamic(AssignmentStrategy strategy, String strategyName, Autoscaler autoscaler) {
        try {
            CloudSim.init(SimulationConfig.NUM_USERS, Calendar.getInstance(), SimulationConfig.TRACE_FLAG);

            String safeName = strategyName.replaceAll("\\s+", "_").replaceAll("[^a-zA-Z0-9_]", "");

            Datacenter datacenter = createDatacenter("Datacenter_" + safeName);
            DynamicBroker broker = new DynamicBroker("Broker_" + safeName);
            CloudSimGateway gateway = new CloudSimGateway(broker);

            PendingTaskQueue pendingQueue = new PendingTaskQueue();
            TaskDispatcher dispatcher = new TaskDispatcher(pendingQueue, gateway, strategy);

            broker.setTaskDispatcher(dispatcher);
            gateway.setTaskDispatcher(dispatcher);

            List<Vm> initialVms = createInitialVmList(broker.getId(), SimulationConfig.MIN_VMS);
            broker.submitVmList(initialVms);

            VmLifecycleManager lifecycleManager = new VmLifecycleManager(
                    broker,
                    datacenter.getId(),
                    SimulationConfig.MIN_VMS
            );

            ArrivalDistribution distribution = new PoissonArrival(
                    SimulationConfig.MEAN_ARRIVAL_RATE,
                    SimulationConfig.WORKLOAD_RANDOM_SEED
            );

            DynamicScheduler scheduler = new DynamicScheduler(pendingQueue, dispatcher);

            WorkloadGenerator workloadGenerator = new WorkloadGenerator(
                    "WorkloadGenerator_" + safeName,
                    distribution,
                    scheduler,
                    SimulationConfig.WORKLOAD_DURATION,
                    SimulationConfig.WORKLOAD_RANDOM_SEED,
                    broker.getId()
            );

            MonitoringModule monitoringModule = new MonitoringModule(
                    "MonitoringModule_" + safeName,
                    gateway,
                    pendingQueue,
                    dispatcher,
                    workloadGenerator,
                    lifecycleManager,
                    autoscaler,
                    SimulationConfig.MONITORING_SAMPLE_INTERVAL,
                    SimulationConfig.MONITORING_WINDOW_SIZE,
                    SimulationConfig.RESPONSE_TIME_SLA
            );

            CloudSim.startSimulation();
            double finishTime = CloudSim.clock(); // Capture exact simulation finish timestamp
            CloudSim.stopSimulation();

            List<ClusterState> history = monitoringModule.getHistory();
            List<Cloudlet> completedCloudlets = gateway.getCompletedCloudlets();

            long totalArrivals = workloadGenerator.getTotalArrivals();
            int pendingCloudlets = pendingQueue.size();

            double totalResponseTime = 0.0;
            double totalTurnaroundTime = 0.0;
            long slaViolations = 0;
            Map<Integer, Double> arrivalTimes = workloadGenerator.getCloudletArrivalTimes();

            for (Cloudlet c : completedCloudlets) {
                Double arrTime = arrivalTimes.get(c.getCloudletId());
                double arrival = (arrTime != null) ? arrTime : c.getSubmissionTime();

                double responseTime = Math.max(0.0, c.getExecStartTime() - arrival);
                double turnaroundTime = Math.max(0.0, c.getFinishTime() - arrival);

                totalResponseTime += responseTime;
                totalTurnaroundTime += turnaroundTime;

                if (responseTime > SimulationConfig.RESPONSE_TIME_SLA) {
                    slaViolations++;
                }
            }

            int count = completedCloudlets.size();
            double avgResponseTime = (count > 0) ? (totalResponseTime / count) : 0.0;
            double avgTurnaroundTime = (count > 0) ? (totalTurnaroundTime / count) : 0.0;
            double slaViolationRate = (count > 0) ? ((double) slaViolations / count) : 0.0;

            double overallThroughput = (finishTime > 0) ? ((double) count / finishTime) : 0.0;

            double peakArrival = 0.0;
            double peakQueue = 0.0;
            double sumCpuUtil = 0.0;
            double sumVmCount = 0.0;
            double totalVmSeconds = 0.0; // <--- Time-integral accumulator

            int historySize = history.size();
            for (int i = 0; i < historySize; i++) {
                ClusterState current = history.get(i);

                if (current.getArrivalRate() > peakArrival) peakArrival = current.getArrivalRate();
                if (current.getAverageQueueLength() > peakQueue) peakQueue = current.getAverageQueueLength();
                sumCpuUtil += current.getAverageCpuUtilisation();
                sumVmCount += current.getActiveVmCount();

                // Compute exact delta to next tick or simulation finish time
                double timeDelta;
                if (i < historySize - 1) {
                    timeDelta = history.get(i + 1).getTime() - current.getTime();
                } else {
                    timeDelta = Math.max(0.0, finishTime - current.getTime());
                }

                // Time-integrate active VM count across this tick interval
                totalVmSeconds += current.getActiveVmCount() * timeDelta;
            }

            double avgCpuUtil = (!history.isEmpty()) ? (sumCpuUtil / historySize) : 0.0;
            double avgVmCount = (!history.isEmpty()) ? (sumVmCount / historySize) : 0.0;

            return new DynamicSimulationResult(
                    strategyName,
                    totalArrivals,
                    completedCloudlets,
                    pendingCloudlets,
                    avgResponseTime,
                    avgTurnaroundTime,
                    overallThroughput,
                    peakArrival,
                    peakQueue,
                    avgCpuUtil,
                    avgVmCount,
                    totalVmSeconds, // <--- Pass total VM-seconds
                    slaViolationRate,
                    history
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static List<Vm> createInitialVmList(int brokerId, int count) {
        List<Vm> vmList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int mips = SimulationConfig.VM_MIPS_VALUES[i % SimulationConfig.VM_MIPS_VALUES.length];
            Vm vm = new Vm(
                    i,
                    brokerId,
                    mips,
                    SimulationConfig.VM_PES,
                    SimulationConfig.VM_RAM,
                    SimulationConfig.VM_BW,
                    SimulationConfig.VM_SIZE,
                    SimulationConfig.VM_VMM,
                    new CloudletSchedulerTimeShared()
            );
            vmList.add(vm);
        }
        return vmList;
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

        DatacenterCharacteristics dc = new DatacenterCharacteristics(
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
                dc,
                new VmAllocationPolicySimple(hostList),
                new LinkedList<Storage>(),
                0
        );
    }
}