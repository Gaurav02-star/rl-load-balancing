package org;

import java.util.List;

public class ResultPrinter {

    public static void printDynamic(DynamicSimulationResult res) {
        if (res == null) return;
        System.out.printf("  [Result] Strategy: %-38s | Completed: %3d/%-3d | Avg Resp: %.3fs | Avg CPU: %5.1f%% | Avg VMs: %4.2f%n",
                res.getStrategyName(),
                res.getCompletedCloudlets().size(),
                res.getTotalArrivals(),
                res.getAvgResponseTime(),
                res.getAvgCpuUtil() * 100.0,
                res.getAvgVmCount());
    }

    public static void printDynamicComparisonTable(List<DynamicSimulationResult> results) {
        System.out.println("============================================================================================================================================");
        System.out.println("   DYNAMIC SIMULATION COMPARISON SUMMARY");
        System.out.println("============================================================================================================================================");
        System.out.printf("%-42s %-9s %-10s %-8s %-12s %-12s %-14s %-15s %-11s %-11s %-10s%n",
                "Strategy", "Arrivals", "Completed", "Pending", "Avg Resp (s)", "Avg Turn (s)", "SLA Viol Rate", "Throughput (t/s)", "Peak Queue", "Avg CPU", "Avg VMs");
        System.out.println("--------------------------------------------------------------------------------------------------------------------------------------------");

        for (DynamicSimulationResult res : results) {
            if (res == null) continue;
            System.out.printf("%-42s %-9d %-10d %-8d %-12.4f %-12.4f %-14.4f %-15.4f %-11.2f %-11.4f %-10.2f%n",
                    res.getStrategyName(),
                    res.getTotalArrivals(),
                    res.getCompletedCloudlets().size(),
                    res.getPendingCloudlets(),
                    res.getAvgResponseTime(),
                    res.getAvgTurnaroundTime(),
                    res.getSlaViolationRate(),
                    res.getOverallThroughput(),
                    res.getPeakQueue(),
                    res.getAvgCpuUtil(),
                    res.getAvgVmCount());
        }
        System.out.println("============================================================================================================================================\n");
    }
}