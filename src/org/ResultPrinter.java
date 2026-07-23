package org;

import org.cloudbus.cloudsim.Cloudlet;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

/**
 * ResultPrinter.java
 * Handles console output for static and dynamic simulations.
 */
public class ResultPrinter {

    private static final DecimalFormat DF4 = new DecimalFormat("0.0000");
    private static final DecimalFormat DF2 = new DecimalFormat("0.00");

    private ResultPrinter() {}

    public static void printSectionHeader(String strategyName) {
        System.out.println("\n");
        System.out.println("══════════════════════════════════════════════════════════");
        System.out.printf ("  Strategy: %s%n", strategyName);
        System.out.println("══════════════════════════════════════════════════════════");
    }

    public static void printCloudletTable(SimulationResult result) {
        List<Cloudlet> list = result.completedCloudlets;

        String row = "+-------------+-------+------------+-------------+-----------+";
        System.out.println("\n  Cloudlet Execution Table");
        System.out.println(row);
        System.out.printf("| %-11s | %-5s | %-10s | %-11s | %-9s |%n",
                "Cloudlet ID", "VM ID", "Start Time", "Finish Time", "Exec Time");
        System.out.println(row);

        for (Cloudlet cl : list) {
            if (cl.getCloudletStatus() == Cloudlet.SUCCESS) {
                System.out.printf("| %-11d | %-5d | %-10s | %-11s | %-9s |%n",
                        cl.getCloudletId(),
                        cl.getVmId(),
                        DF2.format(cl.getExecStartTime()),
                        DF2.format(cl.getFinishTime()),
                        DF2.format(cl.getActualCPUTime()));
            }
        }
        System.out.println(row);
    }

    public static void printMetrics(Metrics m) {
        System.out.println("\n  Metrics");
        System.out.println("  ───────────────────────────────────────────");
        System.out.printf("  Makespan                 : %s s%n",     DF4.format(m.makespan));
        System.out.printf("  Avg Response Time        : %s s%n",     DF4.format(m.avgResponseTime));
        System.out.printf("  Avg Turnaround Time      : %s s%n",     DF4.format(m.avgTurnaround));
        System.out.printf("  Throughput               : %s tasks/s%n",DF4.format(m.throughput));

        System.out.println("  VM Utilisation");
        for (int i = 0; i < m.vmUtilisation.length; i++) {
            System.out.printf("      VM %-3d : %s%n", i, DF4.format(m.vmUtilisation[i]));
        }
        System.out.printf("    Average Utilisation    : %s%n",       DF4.format(m.avgUtilisation));
        System.out.printf("  Load Imbalance (Std Dev) : %s s%n",     DF4.format(m.loadImbalanceSD));
        System.out.printf("  Degree of Imbalance (DI) : %s%n",       DF4.format(m.degreeOfImbalance));
        System.out.printf("  Total Energy             : %s J%n",     DF4.format(m.totalEnergy));
        System.out.println("  ───────────────────────────────────────────");
    }

    public static void printComparisonTable(List<Metrics> allMetrics) {
        int colW = 14;
        int nameW = 16;

        System.out.println("\n\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                                                       COMPARISON SUMMARY                                                                 ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣");

        System.out.printf("║ %-" + nameW + "s", "Strategy");
        String[] cols = {
                "Makespan(s)", "AvgRT(s)", "AvgTAT(s)",
                "Throughput", "AvgUtil", "ImbalSD(s)", "DI", "Energy(J)"
        };
        for (String col : cols) {
            System.out.printf(" %-" + colW + "s", col);
        }
        System.out.println(" ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣");

        for (Metrics m : allMetrics) {
            System.out.printf("║ %-" + nameW + "s", m.strategyName);
            System.out.printf(" %-" + colW + "s", DF4.format(m.makespan));
            System.out.printf(" %-" + colW + "s", DF4.format(m.avgResponseTime));
            System.out.printf(" %-" + colW + "s", DF4.format(m.avgTurnaround));
            System.out.printf(" %-" + colW + "s", DF4.format(m.throughput));
            System.out.printf(" %-" + colW + "s", DF4.format(m.avgUtilisation));
            System.out.printf(" %-" + colW + "s", DF4.format(m.loadImbalanceSD));
            System.out.printf(" %-" + colW + "s", DF4.format(m.degreeOfImbalance));
            System.out.printf(" %-" + colW + "s", DF4.format(m.totalEnergy));
            System.out.println(" ║");
        }

        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════╝");
    }

    /**
     * Prints dynamic simulation summary metrics and telemetry timeline (Phase 2).
     */
    public static void printDynamic(DynamicSimulationResult res) {
        List<Cloudlet> completed = res.completedCloudlets;
        Map<Integer, Double> arrTimes = res.cloudletArrivalTimes;
        int n = completed.size();

        double sumRT = 0.0;
        double sumTAT = 0.0;
        double maxFinish = 0.0;
        long slaViolations = 0;

        for (Cloudlet c : completed) {
            Double arr = arrTimes.get(c.getCloudletId());
            double arrival = (arr != null) ? arr : c.getSubmissionTime();

            double rt = Math.max(0.0, c.getExecStartTime() - arrival);
            double tat = Math.max(0.0, c.getFinishTime() - arrival);

            sumRT += rt;
            sumTAT += tat;
            maxFinish = Math.max(maxFinish, c.getFinishTime());

            if (rt > SimulationConfig.RESPONSE_TIME_SLA) {
                slaViolations++;
            }
        }

        double avgRT = n > 0 ? sumRT / n : 0.0;
        double avgTAT = n > 0 ? sumTAT / n : 0.0;
        double throughput = maxFinish > 0.0 ? (double) n / maxFinish : 0.0;
        double slaViolRate = n > 0 ? (double) slaViolations / n : 0.0;

        double peakArrivalRate = 0.0;
        double peakQueue = 0.0;
        double sumCpu = 0.0;

        for (ClusterState s : res.telemetryHistory) {
            peakArrivalRate = Math.max(peakArrivalRate, s.getArrivalRate());
            peakQueue = Math.max(peakQueue, s.getAvgQueueLength());
            sumCpu += s.getAvgCpuUtilisation();
        }

        double avgCpuUtil = !res.telemetryHistory.isEmpty() ? (sumCpu / res.telemetryHistory.size()) : 0.0;
        int pending = (int) Math.max(0, res.totalArrivals - n);

        System.out.println("\n══════════════════════════════════════════════════════════");
        System.out.printf("  Dynamic Simulation Summary Strategy: %s%n", res.strategyName);
        System.out.println("══════════════════════════════════════════════════════════");
        System.out.printf("  Total Arrivals           : %d%n", res.totalArrivals);
        System.out.printf("  Total Completions        : %d%n", n);
        System.out.printf("  Pending Cloudlets        : %d%n", pending);
        System.out.printf("  Avg Response Time        : %s s%n", DF4.format(avgRT));
        System.out.printf("  Avg Turnaround Time      : %s s%n", DF4.format(avgTAT));
        System.out.printf("  Overall Throughput       : %s tasks/s%n", DF4.format(throughput));
        System.out.printf("  Peak Arrival Rate        : %s tasks/s%n", DF2.format(peakArrivalRate));
        System.out.printf("  Peak Queue Length        : %s%n", DF2.format(peakQueue));
        System.out.printf("  Average CPU Utilisation  : %s%%%n", DF2.format(avgCpuUtil * 100.0));
        System.out.printf("  SLA Violation Rate       : %s%%%n", DF2.format(slaViolRate * 100.0));
        System.out.println("──────────────────────────────────────────────────────────");

        System.out.println("\n--- Telemetry Timeline ---");
        System.out.println("+---------+-------------+------------+---------+---------+---------+-----------+");
        System.out.printf("| %-7s | %-11s | %-10s | %-7s | %-7s | %-7s | %-9s |%n",
                "Time(s)", "ArrRate/s", "Thrp/s", "AvgCPU%", "Queue", "AvgRT(s)", "ActiveVMs");
        System.out.println("+---------+-------------+------------+---------+---------+---------+-----------+");

        for (ClusterState cs : res.telemetryHistory) {
            if (cs.getSimulationTime() % SimulationConfig.TELEMETRY_PRINT_INTERVAL == 0.0
                    || cs.getSimulationTime() == 1.0) {
                System.out.printf("| %-7s | %-11s | %-10s | %-7s | %-7s | %-7s | %-9d |%n",
                        DF2.format(cs.getSimulationTime()),
                        DF2.format(cs.getArrivalRate()),
                        DF2.format(cs.getThroughput()),
                        DF2.format(cs.getAvgCpuUtilisation() * 100.0),
                        DF2.format(cs.getAvgQueueLength()),
                        DF4.format(cs.getAvgResponseTime()),
                        cs.getActiveVmCount());
            }
        }
        System.out.println("+---------+-------------+------------+---------+---------+---------+-----------+");
    }
}