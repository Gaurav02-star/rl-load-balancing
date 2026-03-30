package org;

import java.text.DecimalFormat;
import java.util.*;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.*;

/**
 * HeuristicsComparison.java
 *
 * Compares four load balancing heuristics in CloudSim:
 *   1. FCFS         – First Come, First Served
 *   2. Round Robin  – Cyclic distribution
 *   3. Least Loaded - Task to least-loaded VM
 *   3. Min-Min      – Shortest task to least-loaded VM
 *   4. Max-Min      – Longest task to least-loaded VM
 *
 * For each heuristic the simulation is re-initialised from scratch,
 * the same set of cloudlets is assigned to VMs, results are collected,
 * and the following metrics are computed:
 *   Makespan, Avg Response Time, Avg Turnaround Time, Throughput,
 *   VM Utilisation (per VM + average), Load Imbalance (std-dev),
 *   Degree of Imbalance, Total Energy Consumption.
 *
 * A comparison summary table is printed at the end.
 *
 * NOTE:  Drop this file next to LoadBalancingSimulation.java.
 *        Your existing simulation file does NOT need to change.
 */
public class HeuristicsComparison {

    // ---------------------------------------------------------------
    //  Simulation parameters – keep identical to your base simulation
    // ---------------------------------------------------------------
    private static final int    NUM_VMS        = 4;
    private static final int    NUM_CLOUDLETS  = 20;
    private static final int    VM_MIPS        = 1000;
    private static final int    VM_RAM         = 1024;   // MB
    private static final long   VM_SIZE        = 10000;  // MB
    private static final long   VM_BW          = 1000;
    private static final int    VM_PES         = 1;
    private static final String VM_VMM         = "Xen";

    private static final long   CL_FILE_SIZE   = 300;
    private static final long   CL_OUTPUT_SIZE = 300;

    // Energy model constants
    private static final double IDLE_POWER      = 100.0;  // Watts
    private static final double MAX_POWER       = 200.0;  // Watts

    // Heuristic name tags
    private static final String[] HEURISTIC_NAMES = {
            "FCFS", "Round Robin", "Min-Min", "Max-Min"
    };

    // Summary storage: one row per heuristic [makespan, avgRT, avgTAT, throughput,
    //                                          avgUtil, imbalanceSD, di, energy]
    private static final double[][] summary = new double[4][8];

    // ---------------------------------------------------------------
    //  Entry point
    // ---------------------------------------------------------------
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║       CloudSim Load Balancing Heuristic Comparison   ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        runHeuristic(0, "FCFS");
        runHeuristic(1, "RoundRobin");
        runHeuristic(2, "MinMin");
        runHeuristic(3, "MaxMin");

        printSummaryTable();
    }

    // ---------------------------------------------------------------
    //  Run one complete simulation for the given heuristic index
    // ---------------------------------------------------------------
    private static void runHeuristic(int idx, String heuristicTag) {

        System.out.println("\n\n══════════════════════════════════════════════════════");
        System.out.println("  Heuristic: " + HEURISTIC_NAMES[idx]);
        System.out.println("══════════════════════════════════════════════════════");

        try {
            // --- 1. Initialise CloudSim ---
            CloudSim.init(1, Calendar.getInstance(), false);

            // --- 2. Create datacenter ---
            createDatacenter("Datacenter_0");

            // --- 3. Create broker ---
            DatacenterBroker broker = new DatacenterBroker("Broker");
            int brokerId = broker.getId();

            // --- 4. Create VMs ---
            List<Vm> vmList = createVms(brokerId);
            broker.submitVmList(vmList);

            // --- 5. Create cloudlets (unbound) ---
            List<Cloudlet> cloudlets = createCloudlets(brokerId);

            // --- 6. Assign cloudlets → VMs using the chosen heuristic ---
            switch (heuristicTag) {
                case "FCFS":       assignCloudletsFCFS(cloudlets, vmList);       break;
                case "RoundRobin": assignCloudletsRoundRobin(cloudlets, vmList); break;
                case "MinMin":     assignCloudletsMinMin(cloudlets, vmList);     break;
                case "MaxMin":     assignCloudletsMaxMin(cloudlets, vmList);     break;
            }

            broker.submitCloudletList(cloudlets);

            // --- 7. Run ---
            CloudSim.startSimulation();
            List<Cloudlet> results = broker.getCloudletReceivedList();
            CloudSim.stopSimulation();

            // --- 8. Print table & metrics ---
            printCloudletTable(results);
            computeAndPrintMetrics(idx, results, vmList);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ===============================================================
    //  HEURISTIC ASSIGNMENT METHODS
    // ===============================================================

    /**
     * FCFS – assign cloudlets in arrival order, one per VM slot
     * (wraps around if more cloudlets than VMs, just like round-robin
     *  but respects submission order with no load awareness).
     */
    private static void assignCloudletsFCFS(List<Cloudlet> cloudlets, List<Vm> vmList) {
        int numVms = vmList.size();
        for (int i = 0; i < cloudlets.size(); i++) {
            // Assign in strict arrival order, cycling through VMs
            cloudlets.get(i).setVmId(vmList.get(i % numVms).getId());
        }
    }

    /**
     * Round Robin – distribute cloudlets cyclically across all VMs.
     */
    private static void assignCloudletsRoundRobin(List<Cloudlet> cloudlets, List<Vm> vmList) {
        int numVms = vmList.size();
        for (int i = 0; i < cloudlets.size(); i++) {
            cloudlets.get(i).setVmId(vmList.get(i % numVms).getId());
        }
    }

    /**
     * Min-Min – at each step assign the SHORTEST remaining cloudlet
     * to the VM that would finish it the EARLIEST (least current load).
     *
     * Steps:
     *  1. Sort remaining cloudlets ascending by length.
     *  2. Pick the shortest → assign to VM with min accumulated load.
     *  3. Update that VM's accumulated execution time.
     *  4. Repeat until all cloudlets are assigned.
     */
    private static void assignCloudletsMinMin(List<Cloudlet> cloudlets, List<Vm> vmList) {
        // Track accumulated MI load per VM slot (index matches vmList)
        double[] vmLoad = new double[vmList.size()];

        // Work on a copy sorted by length ascending
        List<Cloudlet> remaining = new ArrayList<>(cloudlets);
        remaining.sort(Comparator.comparingLong(Cloudlet::getCloudletLength));

        for (Cloudlet cl : remaining) {
            int bestVmIdx = indexOfMin(vmLoad);
            cl.setVmId(vmList.get(bestVmIdx).getId());
            // Accumulate execution time (length / MIPS) for that VM
            vmLoad[bestVmIdx] += (double) cl.getCloudletLength() / VM_MIPS;
        }
    }

    /**
     * Max-Min – at each step assign the LONGEST remaining cloudlet
     * to the VM that would finish it the EARLIEST (least current load).
     *
     * Steps:
     *  1. Sort remaining cloudlets descending by length.
     *  2. Pick the longest → assign to VM with min accumulated load.
     *  3. Update that VM's accumulated execution time.
     *  4. Repeat until all cloudlets are assigned.
     */
    private static void assignCloudletsMaxMin(List<Cloudlet> cloudlets, List<Vm> vmList) {
        double[] vmLoad = new double[vmList.size()];

        // Work on a copy sorted by length descending
        List<Cloudlet> remaining = new ArrayList<>(cloudlets);
        remaining.sort((a, b) -> Long.compare(b.getCloudletLength(), a.getCloudletLength()));

        for (Cloudlet cl : remaining) {
            int bestVmIdx = indexOfMin(vmLoad);
            cl.setVmId(vmList.get(bestVmIdx).getId());
            vmLoad[bestVmIdx] += (double) cl.getCloudletLength() / VM_MIPS;
        }
    }

    // ===============================================================
    //  METRICS
    // ===============================================================

    /**
     * Compute all 8 metrics, print them, and store in summary[idx].
     *
     * @param idx      heuristic index (0-3)
     * @param results  completed cloudlets returned by broker
     * @param vmList   list of VMs used in this run
     */
    private static void computeAndPrintMetrics(int idx,
                                               List<Cloudlet> results,
                                               List<Vm> vmList) {
        int n  = results.size();
        int nv = vmList.size();

        // ── A. Makespan ──────────────────────────────────────────────
        double makespan = 0;
        for (Cloudlet c : results) {
            makespan = Math.max(makespan, c.getFinishTime());
        }

        // ── B. Average Response Time (= Avg Start Time, submission=0) ─
        double sumStart = 0;
        for (Cloudlet c : results) sumStart += c.getExecStartTime();
        double avgResponseTime = sumStart / n;

        // ── C. Average Turnaround Time (= Avg Finish Time) ───────────
        double sumFinish = 0;
        for (Cloudlet c : results) sumFinish += c.getFinishTime();
        double avgTurnaround = sumFinish / n;

        // ── D. Throughput ─────────────────────────────────────────────
        double throughput = (makespan > 0) ? (double) n / makespan : 0;

        // ── E. VM Utilisation ─────────────────────────────────────────
        // Total actual execution time per VM
        double[] vmExecTime = new double[nv];
        Map<Integer, Integer> vmIdToIdx = new HashMap<>();
        for (int i = 0; i < nv; i++) vmIdToIdx.put(vmList.get(i).getId(), i);

        for (Cloudlet c : results) {
            int vi = vmIdToIdx.getOrDefault(c.getVmId(), -1);
            if (vi >= 0) vmExecTime[vi] += c.getActualCPUTime();
        }

        double[] vmUtil = new double[nv];
        for (int i = 0; i < nv; i++) {
            vmUtil[i] = (makespan > 0) ? vmExecTime[i] / makespan : 0;
        }
        double avgUtil = mean(vmUtil);

        // ── F. Load Imbalance – standard deviation of per-VM exec times ─
        double imbalanceSD = stdDev(vmExecTime);

        // ── G. Degree of Imbalance ────────────────────────────────────
        double tMax = max(vmExecTime);
        double tMin = min(vmExecTime);
        double tAvg = mean(vmExecTime);
        double di   = (tAvg > 0) ? (tMax - tMin) / tAvg : 0;

        // ── H. Energy Consumption ─────────────────────────────────────
        double totalEnergy = 0;
        for (int i = 0; i < nv; i++) {
            double u = vmUtil[i];
            // Energy (J) = Power (W) × Time (s)
            double energy = (u * MAX_POWER + (1 - u) * IDLE_POWER) * makespan;
            totalEnergy += energy;
        }

        // ── Store in summary ──────────────────────────────────────────
        summary[idx][0] = makespan;
        summary[idx][1] = avgResponseTime;
        summary[idx][2] = avgTurnaround;
        summary[idx][3] = throughput;
        summary[idx][4] = avgUtil;
        summary[idx][5] = imbalanceSD;
        summary[idx][6] = di;
        summary[idx][7] = totalEnergy;

        // ── Print ─────────────────────────────────────────────────────
        DecimalFormat df = new DecimalFormat("0.0000");
        System.out.println("\n--- Metrics ---");
        System.out.printf("  Makespan                  : %s s%n",  df.format(makespan));
        System.out.printf("  Avg Response Time         : %s s%n",  df.format(avgResponseTime));
        System.out.printf("  Avg Turnaround Time       : %s s%n",  df.format(avgTurnaround));
        System.out.printf("  Throughput                : %s tasks/s%n", df.format(throughput));
        System.out.println("  VM Utilisation:");
        for (int i = 0; i < nv; i++) {
            System.out.printf("      VM %-2d : %s%n", vmList.get(i).getId(),
                    df.format(vmUtil[i]));
        }
        System.out.printf("    Average Utilisation     : %s%n",   df.format(avgUtil));
        System.out.printf("  Load Imbalance (Std Dev)  : %s s%n", df.format(imbalanceSD));
        System.out.printf("  Degree of Imbalance (DI)  : %s%n",   df.format(di));
        System.out.printf("  Total Energy Consumption  : %s J%n", df.format(totalEnergy));
    }

    // ===============================================================
    //  OUTPUT – Cloudlet execution table
    // ===============================================================

    private static void printCloudletTable(List<Cloudlet> list) {
        DecimalFormat df = new DecimalFormat("0.00");
        String sep = "+-------------+-------+------------+-------------+-----------+";

        System.out.println("\n--- Cloudlet Execution Results ---");
        System.out.println(sep);
        System.out.printf("| %-11s | %-5s | %-10s | %-11s | %-9s |%n",
                "Cloudlet ID", "VM ID", "Start Time", "Finish Time", "Exec Time");
        System.out.println(sep);

        for (Cloudlet c : list) {
            if (c.getCloudletStatus() == Cloudlet.SUCCESS) {
                System.out.printf("| %-11d | %-5d | %-10s | %-11s | %-9s |%n",
                        c.getCloudletId(),
                        c.getVmId(),
                        df.format(c.getExecStartTime()),
                        df.format(c.getFinishTime()),
                        df.format(c.getActualCPUTime()));
            }
        }
        System.out.println(sep);
    }

    // ===============================================================
    //  COMPARISON SUMMARY TABLE  (Bonus)
    // ===============================================================

    private static void printSummaryTable() {
        DecimalFormat df = new DecimalFormat("0.0000");

        System.out.println("\n\n╔══════════════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                                  COMPARISON SUMMARY TABLE                                           ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  %-20s %-12s %-12s %-12s %-12s %-12s %-12s %-12s %-12s ║%n",
                "Heuristic", "Makespan", "AvgRT(s)", "AvgTAT(s)",
                "Throughput", "AvgUtil", "ImbalSD", "DI", "Energy(J)");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════════════════════╣");

        for (int i = 0; i < HEURISTIC_NAMES.length; i++) {
            System.out.printf("║  %-20s %-12s %-12s %-12s %-12s %-12s %-12s %-12s %-12s ║%n",
                    HEURISTIC_NAMES[i],
                    df.format(summary[i][0]),
                    df.format(summary[i][1]),
                    df.format(summary[i][2]),
                    df.format(summary[i][3]),
                    df.format(summary[i][4]),
                    df.format(summary[i][5]),
                    df.format(summary[i][6]),
                    df.format(summary[i][7]));
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("  Legend:");
        System.out.println("    AvgRT    = Average Response Time  (avg start time, submission=0)");
        System.out.println("    AvgTAT   = Average Turnaround Time (avg finish time)");
        System.out.println("    Throughput = cloudlets / makespan");
        System.out.println("    AvgUtil  = average VM utilisation");
        System.out.println("    ImbalSD  = std-dev of per-VM execution times");
        System.out.println("    DI       = Degree of Imbalance = (Tmax - Tmin) / Tavg");
        System.out.println("    Energy   = Σ [ (util×200 + (1-util)×100) × makespan ] per VM");
    }

    // ===============================================================
    //  FACTORY HELPERS
    // ===============================================================

    /** Create 4 identical VMs for the given broker. */
    private static List<Vm> createVms(int brokerId) {
        List<Vm> list = new ArrayList<>();
        for (int i = 0; i < NUM_VMS; i++) {
            list.add(new Vm(
                    i, brokerId, VM_MIPS, VM_PES, VM_RAM,
                    VM_BW, VM_SIZE, VM_VMM,
                    new CloudletSchedulerTimeShared()));
        }
        return list;
    }

    /**
     * Create 20 cloudlets with the same length pattern as the base simulation:
     *   id%3==0 → 1000 MI,  id%3==1 → 2000 MI,  id%3==2 → 3000 MI.
     * Cloudlets are NOT bound to any VM here; assignment happens in the heuristics.
     */
    private static List<Cloudlet> createCloudlets(int brokerId) {
        List<Cloudlet> list = new ArrayList<>();
        UtilizationModel um = new UtilizationModelFull();

        for (int i = 0; i < NUM_CLOUDLETS; i++) {
            long length;
            if      (i % 3 == 0) length = 1000;
            else if (i % 3 == 1) length = 2000;
            else                  length = 3000;

            Cloudlet cl = new Cloudlet(
                    i, length, VM_PES,
                    CL_FILE_SIZE, CL_OUTPUT_SIZE,
                    um, um, um);
            cl.setUserId(brokerId);
            list.add(cl);
        }
        return list;
    }

    /** Mirror of the datacenter from LoadBalancingSimulation (2 hosts, 2 PEs each). */
    private static Datacenter createDatacenter(String name) throws Exception {
        List<Host> hostList = new ArrayList<>();
        int mips = VM_MIPS;

        for (int h = 0; h < 2; h++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new Pe(0, new PeProvisionerSimple(mips)));
            peList.add(new Pe(1, new PeProvisionerSimple(mips)));

            hostList.add(new Host(
                    h,
                    new RamProvisionerSimple(4096),
                    new BwProvisionerSimple(10000),
                    1000000L,
                    peList,
                    new VmSchedulerTimeShared(peList)));
        }

        DatacenterCharacteristics dc = new DatacenterCharacteristics(
                "x86", "Linux", "Xen",
                hostList, 10.0, 3.0, 0.05, 0.001, 0.0);

        return new Datacenter(
                name, dc,
                new VmAllocationPolicySimple(hostList),
                new LinkedList<Storage>(), 0);
    }

    // ===============================================================
    //  MATH UTILITIES
    // ===============================================================

    private static int indexOfMin(double[] arr) {
        int idx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] < arr[idx]) idx = i;
        }
        return idx;
    }

    private static double mean(double[] arr) {
        double s = 0;
        for (double v : arr) s += v;
        return s / arr.length;
    }

    private static double stdDev(double[] arr) {
        double mu = mean(arr);
        double variance = 0;
        for (double v : arr) variance += (v - mu) * (v - mu);
        return Math.sqrt(variance / arr.length);
    }

    private static double max(double[] arr) {
        double m = arr[0];
        for (double v : arr) if (v > m) m = v;
        return m;
    }

    private static double min(double[] arr) {
        double m = arr[0];
        for (double v : arr) if (v < m) m = v;
        return m;
    }
}