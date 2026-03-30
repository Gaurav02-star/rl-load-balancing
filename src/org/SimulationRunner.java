package org;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.*;

import java.util.*;

/**
 * SimulationRunner.java
 *
 * The simulation engine. Mirrors the original LoadBalancingSimulation
 * structure exactly — same datacenter, same hosts, same VM and cloudlet
 * parameters — refactored into a reusable static method.
 *
 * Every call to {@link #run} is a completely independent CloudSim lifecycle:
 *   init → create datacenter → create broker → create VMs → create cloudlets
 *   → apply strategy → start → collect → stop
 *
 * The runner knows nothing about which algorithm is used; it delegates
 * purely to the {@link AssignmentStrategy} it receives.
 */
public class SimulationRunner {

    // Private constructor — this class is never instantiated; use run() directly.
    private SimulationRunner() {}

    /**
     * Run one complete, isolated CloudSim simulation.
     *
     * @param strategy     the load-balancing algorithm to evaluate
     * @param strategyName display label stored in the returned result
     * @return             a {@link SimulationResult} holding the completed
     *                     cloudlets, VMs, and strategy label
     * @throws RuntimeException wrapping any CloudSim exception
     */
    public static SimulationResult run(AssignmentStrategy strategy,
                                       String strategyName) {
        try {
            // ── Step 1: Initialise CloudSim ───────────────────────────────────
            // CloudSim uses static state, so init() MUST be called before every
            // independent run to avoid contamination from the previous run.
            CloudSim.init(
                SimulationConfig.NUM_USERS,
                Calendar.getInstance(),
                SimulationConfig.TRACE_FLAG
            );

            // ── Step 2: Create datacenter ─────────────────────────────────────
            // Mirrors original createDatacenter("Datacenter_0")
            createDatacenter("Datacenter_0");

            // ── Step 3: Create broker ─────────────────────────────────────────
            // Mirrors original createBroker()
            DatacenterBroker broker = createBroker();
            int brokerId = broker.getId();

            // ── Step 4: Create VMs ────────────────────────────────────────────
            // Mirrors original VM creation loop
            List<Vm> vmList = createVms(brokerId);
            broker.submitVmList(vmList);

            // ── Step 5: Create cloudlets ──────────────────────────────────────
            // Mirrors original cloudlet creation loop (unbound — no setVmId yet)
            List<Cloudlet> cloudletList = createCloudlets(brokerId);

            // ── Step 6: Apply strategy ────────────────────────────────────────
            // THIS is the only line that changes between heuristic runs.
            // The strategy calls setVmId() on each cloudlet.
            strategy.assign(cloudletList, vmList);

            // ── Step 7: Submit and run ────────────────────────────────────────
            broker.submitCloudletList(cloudletList);
            CloudSim.startSimulation();
            List<Cloudlet> results = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            return new SimulationResult(strategyName, results, vmList);

        } catch (Exception e) {
            throw new RuntimeException(
                "SimulationRunner failed for strategy: " + strategyName, e);
        }
    }

    // =========================================================================
    //  Private factory helpers
    //  These are direct refactors of the original private methods.
    //  Nothing has been changed except making them instance-independent.
    // =========================================================================

    /**
     * Mirrors original {@code createDatacenter(String name)}.
     * Creates 2 hosts, each with 2 PEs, per the original configuration.
     */
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

    /**
     * Mirrors original {@code createBroker()}.
     */
    private static DatacenterBroker createBroker() throws Exception {
        return new DatacenterBroker("Broker");
    }

    /**
     * Mirrors original VM creation loop.
     * NUM_VMS VMs are created; each gets id = loop index.
     * No VM ID is hard-coded.
     */
    private static List<Vm> createVms(int brokerId) {
        List<Vm> list = new ArrayList<>();
        for (int i = 0; i < SimulationConfig.NUM_VMS; i++) {
            list.add(new Vm(
                i,
                brokerId,
                SimulationConfig.VM_MIPS,
                SimulationConfig.VM_PES,
                SimulationConfig.VM_RAM,
                SimulationConfig.VM_BW,
                SimulationConfig.VM_SIZE,
                SimulationConfig.VM_VMM,
                new CloudletSchedulerTimeShared()
            ));
        }
        return list;
    }

    /**
     * Mirrors original cloudlet creation loop.
     * Length pattern: id%3==0 → 1000 MI, id%3==1 → 2000 MI, id%3==2 → 3000 MI.
     * Cloudlets are NOT bound to any VM here; that is the strategy's job.
     */
    private static List<Cloudlet> createCloudlets(int brokerId) {
        List<Cloudlet> list = new ArrayList<>();
        UtilizationModel um = new UtilizationModelFull();

        for (int i = 0; i < SimulationConfig.NUM_CLOUDLETS; i++) {
            long length;
            if      (i % 3 == 0) length = SimulationConfig.CL_LENGTH_SHORT;
            else if (i % 3 == 1) length = SimulationConfig.CL_LENGTH_MEDIUM;
            else                  length = SimulationConfig.CL_LENGTH_LONG;

            Cloudlet cl = new Cloudlet(
                i,
                length,
                SimulationConfig.CL_PES,
                SimulationConfig.CL_FILE_SIZE,
                SimulationConfig.CL_OUTPUT_SIZE,
                um, um, um
            );
            cl.setUserId(brokerId);
            list.add(cl);
        }
        return list;
    }
}
