package org;

import org.cloudbus.cloudsim.Cloudlet;
import java.util.List;

public class ResultPrinter {

    public static void printDynamic(DynamicSimulationResult result) {
        if (result == null) return;

        System.out.println("\n==========================================================================");
        System.out.println(" Dynamic Simulation Summary Strategy: " + result.getStrategyName());
        System.out.println("==========================================================================");
        System.out.printf(" Total Arrivals            : %d%n", result.getTotalArrivals());
        System.out.printf(" Total Completions         : %d%n", result.getCompletedCloudlets().size());
        System.out.printf(" Pending Cloudlets         : %d%n", result.getPendingCloudlets());
        System.out.printf(" Avg Response Time        : %.4f s%n", result.getAverageResponseTime());
        System.out.printf(" Avg Turnaround Time      : %.4f s%n", result.getAverageTurnaroundTime());
        System.out.printf(" Overall Throughput        : %.4f tasks/s%n", result.getOverallThroughput());
        System.out.printf(" Peak Arrival Rate         : %.2f tasks/s%n", result.getPeakArrivalRate());
        System.out.printf(" Peak Queue Length         : %.2f%n", result.getPeakQueueLength());
        System.out.printf(" Average CPU Utilisation   : %.2f%%%n", result.getAverageCpuUtilisation() * 100);
        System.out.printf(" SLA Violation Rate        : %.2f%%%n", result.getSlaViolationRate() * 100);
        System.out.println("--------------------------------------------------------------------------\n");

        List<ClusterState> history = result.getHistory();
        if (history != null && !history.isEmpty()) {
            System.out.println("--- Telemetry Timeline ---");
            System.out.println("+---------+-----------+-----------+----------+-------+-----------+-----------+");
            System.out.println("| Time(s) | ArrRate/s | Thrp/s    | AvgCPU%  | Queue | AvgRT(s)  | ActiveVMs |");
            System.out.println("+---------+-----------+-----------+----------+-------+-----------+-----------+");

            for (ClusterState s : history) {
                System.out.printf("| %-7.2f | %-9.2f | %-9.2f | %-8.2f | %-5.2f | %-9.4f | %-9d |%n",
                        s.getTime(),
                        s.getArrivalRate(),
                        s.getThroughput(),
                        s.getAverageCpuUtilisation() * 100,
                        s.getAverageQueueLength(),
                        s.getAverageResponseTime(),
                        s.getActiveVmCount());
            }
            System.out.println("+---------+-----------+-----------+----------+-------+-----------+-----------+\n");
        }
    }

    public static void printDynamicComparisonTable(List<DynamicSimulationResult> results) {
        System.out.println("\n══════════════════════════════════════════════════════════════════════════════════════════════════════");
        System.out.println("                                DYNAMIC SYSTEM STRATEGY COMPARISON");
        System.out.println("══════════════════════════════════════════════════════════════════════════════════════════════════════");
        System.out.printf("%-40s | %-10s | %-11s | %-14s | %-12s | %-10s%n",
                "Strategy Name", "Arrivals", "Completions", "Turnaround (s)", "Avg CPU (%)", "SLA Viol (%)");
        System.out.println("──────────────────────────────────────────────────────────────────────────────────────────────────────");

        for (DynamicSimulationResult res : results) {
            if (res != null) {
                System.out.printf("%-40s | %-10d | %-11d | %-14.4f | %-12.2f | %-10.2f%n",
                        res.getStrategyName(),
                        res.getTotalArrivals(),
                        res.getCompletedCloudlets().size(),
                        res.getAverageTurnaroundTime(),
                        res.getAverageCpuUtilisation() * 100,
                        res.getSlaViolationRate() * 100
                );
            }
        }
        System.out.println("══════════════════════════════════════════════════════════════════════════════════════════════════════\n");
    }
}