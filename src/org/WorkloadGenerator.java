package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * WorkloadGenerator.java
 *
 * Classic CloudSim SimEntity responsible for dynamically generating and submitting cloudlets over time.
 */
public class WorkloadGenerator extends SimEntity {

    public static final int EVENT_GENERATE_WORKLOAD = 9001;

    private final ArrivalDistribution distribution;
    private final DynamicScheduler scheduler;
    private final double workloadDuration;
    private final Map<Integer, Double> cloudletArrivalTimes = new HashMap<>();
    private final Random randomLength;
    private final int brokerId;

    private int nextCloudletId = 0;
    private long totalArrivals = 0;

    public WorkloadGenerator(String name,
                             ArrivalDistribution distribution,
                             DynamicScheduler scheduler,
                             double workloadDuration,
                             long seed,
                             int brokerId) {
        super(name);
        this.distribution = distribution;
        this.scheduler = scheduler;
        this.workloadDuration = workloadDuration;
        this.randomLength = new Random(seed);
        this.brokerId = brokerId;
    }

    @Override
    public void startEntity() {
        schedule(getId(), 0.1, EVENT_GENERATE_WORKLOAD);
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() == EVENT_GENERATE_WORKLOAD) {
            double currentTime = CloudSim.clock();

            if (currentTime <= workloadDuration) {
                int batchSize = distribution.nextBatchSize();
                UtilizationModel um = new UtilizationModelFull();

                for (int i = 0; i < batchSize; i++) {
                    int cloudletId = nextCloudletId++;
                    long length = generateRandomLength();

                    Cloudlet cloudlet = new Cloudlet(
                            cloudletId,
                            length,
                            SimulationConfig.CL_PES,
                            SimulationConfig.CL_FILE_SIZE,
                            SimulationConfig.CL_OUTPUT_SIZE,
                            um, um, um
                    );

                    // Crucial: Bind cloudlet to broker ID so Datacenter locates host mapping properly
                    cloudlet.setUserId(brokerId);

                    cloudletArrivalTimes.put(cloudletId, currentTime);
                    totalArrivals++;

                    scheduler.scheduleArrival(cloudlet);
                }

                double nextInterval = distribution.nextInterArrivalTime();
                double nextEventTime = currentTime + nextInterval;

                if (nextEventTime <= workloadDuration) {
                    schedule(getId(), nextInterval, EVENT_GENERATE_WORKLOAD);
                }
            }
        }
    }

    @Override
    public void shutdownEntity() {}

    private long generateRandomLength() {
        int tier = randomLength.nextInt(3);
        long min, max;
        if (tier == 0) {
            min = SimulationConfig.CL_LENGTH_SHORT_MIN;
            max = SimulationConfig.CL_LENGTH_SHORT_MAX;
        } else if (tier == 1) {
            min = SimulationConfig.CL_LENGTH_MEDIUM_MIN;
            max = SimulationConfig.CL_LENGTH_MEDIUM_MAX;
        } else {
            min = SimulationConfig.CL_LENGTH_LONG_MIN;
            max = SimulationConfig.CL_LENGTH_LONG_MAX;
        }
        return min + (long) (randomLength.nextDouble() * (max - min));
    }

    public Map<Integer, Double> getCloudletArrivalTimes() {
        return Collections.unmodifiableMap(cloudletArrivalTimes);
    }

    public long getTotalArrivals() {
        return totalArrivals;
    }
}