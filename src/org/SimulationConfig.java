package org;

/**
 * SimulationConfig.java
 * Single source of truth for every tunable parameter.
 */
public class SimulationConfig {

    // ── CloudSim runtime ──────────────────────────────────────────────────────
    public static final int     NUM_USERS       = 1;
    public static final boolean TRACE_FLAG      = false;

    // ── Datacenter ────────────────────────────────────────────────────────────
    public static final String  DC_ARCH         = "x86";
    public static final String  DC_OS           = "Linux";
    public static final String  DC_VMM          = "Xen";
    public static final double  DC_TIME_ZONE    = 10.0;
    public static final double  DC_COST         = 3.0;
    public static final double  DC_COST_MEM     = 0.05;
    public static final double  DC_COST_STORAGE = 0.001;
    public static final double  DC_COST_BW      = 0.0;

    // ── Hosts ─────────────────────────────────────────────────────────────────
    public static final int     NUM_HOSTS       = 6;        // Expanded host count to guarantee MIPS headroom
    public static final int     HOST_MIPS       = 8000;     // Expanded MIPS per host PE
    public static final int     HOST_PES        = 4;
    public static final int     HOST_RAM        = 32768;    // 32 GB RAM per host
    public static final long    HOST_STORAGE    = 1_000_000;
    public static final int     HOST_BW         = 100_000;  // 100,000 Bandwidth

    // ── VMs ───────────────────────────────────────────────────────────────────
    public static final int     NUM_VMS         = 4;
    public static final int     MIN_VMS         = 2;
    public static final int     MAX_VMS         = 8;

    public static final int[]   VM_MIPS_VALUES  = {500, 1000, 1500, 2000};

    public static final int     VM_PES          = 1;
    public static final int     VM_RAM          = 1024;
    public static final long    VM_BW           = 1000;
    public static final long    VM_SIZE         = 10_000;
    public static final String  VM_VMM          = "Xen";

    // ── Cloudlets ─────────────────────────────────────────────────────────────
    public static final int     NUM_CLOUDLETS    = 20;
    public static final int     CL_PES           = 1;
    public static final long    CL_FILE_SIZE     = 300;
    public static final long    CL_OUTPUT_SIZE   = 300;

    public static final long    CL_LENGTH_SHORT_MIN  =  800;  // MI
    public static final long    CL_LENGTH_SHORT_MAX  = 1200;
    public static final long    CL_LENGTH_MEDIUM_MIN = 1700;
    public static final long    CL_LENGTH_MEDIUM_MAX = 2300;
    public static final long    CL_LENGTH_LONG_MIN   = 2600;
    public static final long    CL_LENGTH_LONG_MAX   = 3400;

    public static final long    CLOUDLET_SEED        = 42L;

    // ── Energy model ──────────────────────────────────────────────────────────
    public static final double  IDLE_POWER       = 100.0;
    public static final double  MAX_POWER        = 200.0;

    // ── Dynamic & Telemetry Configuration ─────────────────────────────────────
    public static SimulationMode SIMULATION_MODE          = SimulationMode.DYNAMIC;
    public static final double   WORKLOAD_DURATION       = 100.0;
    public static final double   MONITORING_SAMPLE_INTERVAL = 1.0;
    public static final double   MONITORING_WINDOW_SIZE  = 10.0;
    public static final double   MEAN_ARRIVAL_RATE       = 2.0;
    public static final long     WORKLOAD_RANDOM_SEED    = 42L;
    public static final double   RESPONSE_TIME_SLA       = 2.0;
    public static final double   TELEMETRY_PRINT_INTERVAL = 5.0;

    // ── Autoscaling Configuration (Phase 3) ───────────────────────────────────
    public static final double   AUTOSCALING_INTERVAL    = 10.0; // Periodic scaling tick (s)

    private SimulationConfig() {}
}