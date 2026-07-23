package org;

import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final int TRAINING_EPISODES = 50;

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
            SimulationRunner.run(rlLoadBalancer, "Training_LB");
        }
        rlLoadBalancer.setEpsilon(0.0);
        System.out.println("  ✔ RL Load Balancer trained successfully.\n");

        // 2. Train RL Autoscaler Agent
        System.out.println("[ 2/3 Training RL Autoscaler Agent... ]");
        RLAutoscaler rlAutoscaler = new RLAutoscaler();
        for (int ep = 1; ep <= TRAINING_EPISODES; ep++) {
            SimulationRunner.runDynamic(rlLoadBalancer, "Training_Autoscaler", rlAutoscaler);
        }
        rlAutoscaler.setEpsilon(0.0); // Switch to deterministic evaluation mode
        System.out.println("  ✔ RL Autoscaler trained successfully.\n");

        // 3. Run Dynamic Comparative Evaluations
        System.out.println("[ 3/3 Running Dynamic Comparative Evaluations ]");
        List<DynamicSimulationResult> dynamicResults = new ArrayList<>();

        dynamicResults.add(runOneDynamic(rlLoadBalancer, "No Autoscaling (Static 4 VMs)", null));
        dynamicResults.add(runOneDynamic(rlLoadBalancer, "Rule-Based Threshold Autoscaler", new ThresholdAutoscaler()));
        dynamicResults.add(runOneDynamic(rlLoadBalancer, "Dual-RL (Load Balancer + RL Autoscaler)", rlAutoscaler));

        // 4. Print final comparison summary table
        ResultPrinter.printDynamicComparisonTable(dynamicResults);
    }

    private static DynamicSimulationResult runOneDynamic(AssignmentStrategy strategy, String name, Autoscaler autoscaler) {
        DynamicSimulationResult res = SimulationRunner.runDynamic(strategy, name, autoscaler);
        ResultPrinter.printDynamic(res);
        System.gc();
        return res;
    }
}