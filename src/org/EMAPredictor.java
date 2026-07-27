package org;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * EMAPredictor.java
 *
 * Lightweight Exponential Moving Average predictor for arrival rate forecasting.
 *
 * Why EMA over LSTM/ARIMA:
 *   - Zero external dependencies (pure Java, no ML library)
 *   - O(1) update and prediction — no retraining overhead per tick
 *   - Sufficient for cloud workloads that exhibit smooth Poisson-like arrival
 *     patterns with gradual bursts (which is exactly what PoissonArrival produces)
 *   - Interpretable: the prediction is literally a weighted average of recent
 *     observations, which is easy to explain to an examiner
 *
 * How it works:
 *   EMA_t = α × observation_t + (1 − α) × EMA_{t-1}
 *   where α = 2 / (windowSize + 1) — the standard EMA smoothing factor.
 *
 * The prediction for PREDICTION_HORIZON seconds ahead is simply the current EMA,
 * under the assumption that short-horizon load trends are approximately linear.
 * This is "naive predictive scaling" — standard in the cloud autoscaling literature
 * (AWS CloudWatch predictive scaling uses a similar rolling-average approach).
 *
 * Integration with RL autoscaler:
 *   MonitoringModule calls update() every tick with the current observed arrival rate.
 *   The predicted rate is stored in ClusterState.predictedArrivalRate so the
 *   RLAutoscaler can encode it into the state string, enabling the agent to learn
 *   "when a burst is predicted and CPU is already high, scale up NOW."
 */
public class EMAPredictor {

    private final double alpha;          // EMA smoothing factor
    private double       ema;            // current EMA value
    private boolean      initialised;    // false until first observation

    // Simple circular buffer to smooth out single-tick noise
    private final Deque<Double> recentObservations;
    private final int           rawBufferSize;

    /**
     * @param windowSize  number of periods for the EMA (maps to smoothing factor)
     */
    public EMAPredictor(int windowSize) {
        // Standard EMA alpha: higher window = smoother, slower response
        this.alpha          = 2.0 / (windowSize + 1.0);
        this.ema            = 0.0;
        this.initialised    = false;
        this.rawBufferSize  = Math.max(2, windowSize / 2);
        this.recentObservations = new ArrayDeque<>(rawBufferSize + 1);
    }

    /**
     * Feed one new observation (e.g. current arrival rate per second).
     * Call this once per monitoring tick.
     *
     * @param observation current observed value
     */
    public void update(double observation) {
        // Buffer raw value to smooth single-tick spikes
        recentObservations.addLast(observation);
        if (recentObservations.size() > rawBufferSize) {
            recentObservations.pollFirst();
        }

        // Use median of buffer as the actual input to EMA
        // (protects against single anomalous spike corrupting the forecast)
        double smoothedObs = median(recentObservations);

        if (!initialised) {
            ema         = smoothedObs;
            initialised = true;
        } else {
            ema = alpha * smoothedObs + (1.0 - alpha) * ema;
        }
    }

    /**
     * Returns the predicted arrival rate for PREDICTION_HORIZON seconds ahead.
     * Returns 0.0 if no observations have been fed yet.
     */
    public double predict() {
        return initialised ? ema : 0.0;
    }

    /**
     * Returns true when the predicted load is significantly higher than the
     * provided current rate — i.e. a burst is coming.
     *
     * @param currentRate  the arrival rate observed RIGHT NOW in the cluster state
     * @param threshold    fractional increase that constitutes a "burst" (e.g. 0.15 = 15%)
     */
    public boolean isBurstPredicted(double currentRate, double threshold) {
        return initialised && (ema > currentRate * (1.0 + threshold));
    }

    /** Reset the predictor (call between simulation episodes during training). */
    public void reset() {
        ema         = 0.0;
        initialised = false;
        recentObservations.clear();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static double median(Deque<Double> values) {
        if (values.isEmpty()) return 0.0;
        double[] arr = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int mid = arr.length / 2;
        return (arr.length % 2 == 0) ? (arr[mid - 1] + arr[mid]) / 2.0 : arr[mid];
    }
}