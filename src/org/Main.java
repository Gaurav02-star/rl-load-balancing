package org;

import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final int TRAINING_EPISODES = 200;

    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("   CloudSim Adaptive Resource Management Framework (Dual-RL Evaluation)  ");
        System.out.println("==========================================================================");

        runDynamicPipeline();
    }

    private static void runDynamicPipeline() {
        System.out.printf("  Mode: DYNAMIC | Duration: %.0fs | Arrival Rate: %.1f tasks/s%n%n",
                SimulationConfig.WORKLOAD_DURATION, SimulationConfig.MEAN_ARRIVAL_RATE);

        // 1. Train Load Balancer
        System.out.println("[ 1/3 Training RL Load Balancer Agent... ]");
        RLStrategy rlLoadBalancer = new RLStrategy();
        for (int ep = 1; ep <= TRAINING_EPISODES; ep++) {
            SimulationRunner.runDynamic(rlLoadBalancer, "Training_LB", null);
        }
        rlLoadBalancer.setEpsilon(0.0);
        System.out.println("  ✔ RL Load Balancer trained successfully.\n");

        // 2. Train RL Autoscaler Agent Across Episodes
        System.out.println("[ 2/3 Training RL Autoscaler Agent... ]");
        RLAutoscaler rlAutoscaler = new RLAutoscaler();

        System.out.printf("  [Instrumentation] Training Start - Episode 1 Epsilon: %.4f%n", 1.0);

        for (int ep = 1; ep <= TRAINING_EPISODES; ep++) {
            rlAutoscaler.startEpisode(ep, TRAINING_EPISODES);
            SimulationRunner.runDynamic(rlLoadBalancer, "Training_Autoscaler", rlAutoscaler);
        }

        // Output final training instrumentation
        double finalTrainingEpsilon = Math.max(0.0, 1.0 - (double) (TRAINING_EPISODES - 1) / TRAINING_EPISODES);
        System.out.printf("  [Instrumentation] Training End - Episode %d Epsilon: %.4f%n", TRAINING_EPISODES, finalTrainingEpsilon);
        System.out.printf("  [Instrumentation] Final Trained Q-Table Size: %d states%n", rlAutoscaler.getQTableSize());
        System.out.printf("  [Instrumentation] Action Telemetry Across All Training Episodes:%n");
        System.out.printf("      - SCALE_UP Executed        : %d%n", rlAutoscaler.getScaleUpExecuted());
        System.out.printf("      - SCALE_UP Blocked (Gate)  : %d%n", rlAutoscaler.getScaleUpBlockedByGate());
        System.out.printf("      - SCALE_DOWN Executed      : %d%n", rlAutoscaler.getScaleDownExecuted());
        System.out.printf("      - NO_OP Executed          : %d%n%n", rlAutoscaler.getNoOpExecuted());

        // Instrument: Dump full Q-table values for every trained state
        rlAutoscaler.printFinalQTable();

        // Prepare RL Autoscaler for evaluation (Episode = TRAINING_EPISODES + 1 -> Epsilon = 0.0 & clear traces)
        rlAutoscaler.startEpisode(TRAINING_EPISODES + 1, TRAINING_EPISODES);
        System.out.println("  ✔ RL Autoscaler trained successfully.\n");

        // 3. Run Dynamic Comparative Evaluations
        System.out.println("[ 3/3 Running Dynamic Comparative Evaluations ]");
        List<DynamicSimulationResult> dynamicResults = new ArrayList<>();

        DynamicSimulationResult noAutoscalingRes = runOneDynamic(rlLoadBalancer, "No Autoscaling (Static 2 VMs)", null);
        DynamicSimulationResult thresholdRes     = runOneDynamic(rlLoadBalancer, "Rule-Based Threshold Autoscaler", new ThresholdAutoscaler());

        // Enable eval tick state logging on Dual-RL run
        rlAutoscaler.setEvalLogging(true);
        DynamicSimulationResult dualRlRes        = runOneDynamic(rlLoadBalancer, "Dual-RL (Load Balancer + RL Autoscaler)", rlAutoscaler);
        rlAutoscaler.setEvalLogging(false);

        dynamicResults.add(noAutoscalingRes);
        dynamicResults.add(thresholdRes);
        dynamicResults.add(dualRlRes);

        // 4. Instrumentation: Bit-for-Bit Empirical Telemetry Comparison
        printTelemetryDiff(noAutoscalingRes, dualRlRes);

        // 5. Print final comparison summary table
        ResultPrinter.printDynamicComparisonTable(dynamicResults);
    }

    private static DynamicSimulationResult runOneDynamic(AssignmentStrategy strategy, String name, Autoscaler autoscaler) {
        DynamicSimulationResult res = SimulationRunner.runDynamic(strategy, name, autoscaler);
        ResultPrinter.printDynamic(res);
        System.gc();
        return res;
    }

    private static void printTelemetryDiff(DynamicSimulationResult baseline, DynamicSimulationResult dualRl) {
        System.out.println("\n==========================================================================");
        System.out.println("   EMPIRICAL TELEMETRY DIFF: Baseline vs. Dual-RL Evaluation");
        System.out.println("==========================================================================");

        if (baseline == null || dualRl == null) {
            System.out.println("  [ERROR] Cannot compare telemetry: one or both simulation results are null.");
            return;
        }

        List<ClusterState> baseHist = baseline.getHistory();
        List<ClusterState> rlHist   = dualRl.getHistory();

        int baseSize = (baseHist != null) ? baseHist.size() : 0;
        int rlSize   = (rlHist != null) ? rlHist.size() : 0;

        if (baseSize != rlSize) {
            System.out.printf("  Telemetry Length Mismatch! Baseline has %d rows, Dual-RL has %d rows.%n", baseSize, rlSize);
        }

        int minSize = Math.min(baseSize, rlSize);
        int differingRows = 0;

        for (int i = 0; i < minSize; i++) {
            ClusterState s1 = baseHist.get(i);
            ClusterState s2 = rlHist.get(i);

            boolean vmDiff    = s1.getActiveVmCount() != s2.getActiveVmCount();
            boolean respDiff  = Math.abs(s1.getAverageResponseTime() - s2.getAverageResponseTime()) > 1e-6;
            boolean queueDiff = Math.abs(s1.getAverageQueueLength() - s2.getAverageQueueLength()) > 1e-6;

            if (vmDiff || respDiff || queueDiff) {
                differingRows++;
            }
        }

        System.out.printf("  Total Telemetry Samples Compared : %d%n", minSize);
        System.out.printf("  Differing Telemetry Ticks        : %d%n", differingRows);

        if (differingRows > 0) {
            System.out.println("  ✔ CONFIRMED: Dual-RL policy actively altered cluster behavior during evaluation.");
        } else {
            System.out.println("  ❌ WARNING: Dual-RL produces identical telemetry to No-Autoscaling.");
        }
        System.out.println("==========================================================================\n");
    }
}