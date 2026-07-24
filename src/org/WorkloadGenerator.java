package org;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

import java.util.*;

/**
 * WorkloadGenerator.java
 * SimEntity generating dynamic arrival streams using Poisson arrival intervals.
 */
public class WorkloadGenerator extends SimEntity {

    public static final int EVENT_GENERATE_ARRIVALS = 9001;

    private final ArrivalDistribution distribution;
    private final DynamicScheduler scheduler;
    private final double duration;
    private final Random lengthRandom;
    private final int userId;

    private int cloudletCounter = 0;
    private long totalArrivals = 0;
    private final Map<Integer, Double> cloudletArrivalTimes = new HashMap<>();

    public WorkloadGenerator(String name,
                             ArrivalDistribution distribution,
                             DynamicScheduler scheduler,
                             double duration,
                             long seed,
                             int userId) {
        super(name);
        this.distribution = distribution;
        this.scheduler = scheduler;
        this.duration = duration;
        this.lengthRandom = new Random(seed);
        this.userId = userId;
    }

    @Override
    public void startEntity() {
        scheduleNextArrival(0.0);
    }

    @Override
    public void processEvent(SimEvent ev) {
        if (ev.getTag() == EVENT_GENERATE_ARRIVALS) {
            double currentTime = CloudSim.clock();
            if (currentTime <= duration) {
                // Generate batch size based on ArrivalDistribution interface
                int batchSize = Math.max(1, distribution.nextBatchSize());
                for (int b = 0; b < batchSize; b++) {
                    generateAndScheduleCloudlet(currentTime);
                }

                // Call nextInterArrivalTime() from ArrivalDistribution interface
                double nextInterval = distribution.nextInterArrivalTime();
                if (currentTime + nextInterval <= duration) {
                    schedule(getId(), nextInterval, EVENT_GENERATE_ARRIVALS);
                }
            }
        }
    }

    @Override
    public void shutdownEntity() {}

    private void scheduleNextArrival(double delay) {
        schedule(getId(), delay, EVENT_GENERATE_ARRIVALS);
    }

    private void generateAndScheduleCloudlet(double arrivalTime) {
        int id = cloudletCounter++;
        long length = getRandomLength();

        Cloudlet c = new Cloudlet(
                id,
                length,
                SimulationConfig.CL_PES,
                SimulationConfig.CL_FILE_SIZE,
                SimulationConfig.CL_OUTPUT_SIZE,
                new UtilizationModelFull(),
                new UtilizationModelFull(),
                new UtilizationModelFull()
        );
        c.setUserId(userId);

        totalArrivals++;
        cloudletArrivalTimes.put(id, arrivalTime);

        scheduler.scheduleArrival(c);
    }

    private long getRandomLength() {
        int type = lengthRandom.nextInt(3);
        if (type == 0) {
            return SimulationConfig.CL_LENGTH_SHORT_MIN + (long) (lengthRandom.nextDouble() * (SimulationConfig.CL_LENGTH_SHORT_MAX - SimulationConfig.CL_LENGTH_SHORT_MIN));
        } else if (type == 1) {
            return SimulationConfig.CL_LENGTH_MEDIUM_MIN + (long) (lengthRandom.nextDouble() * (SimulationConfig.CL_LENGTH_MEDIUM_MAX - SimulationConfig.CL_LENGTH_MEDIUM_MIN));
        } else {
            return SimulationConfig.CL_LENGTH_LONG_MIN + (long) (lengthRandom.nextDouble() * (SimulationConfig.CL_LENGTH_LONG_MAX - SimulationConfig.CL_LENGTH_LONG_MIN));
        }
    }

    public long getTotalArrivals() { return totalArrivals; }
    public Map<Integer, Double> getCloudletArrivalTimes() { return Collections.unmodifiableMap(cloudletArrivalTimes); }
}