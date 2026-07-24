package org;

public class SimulationConfig {

    public static final int     NUM_USERS       = 1;
    public static final boolean TRACE_FLAG      = false;

    public static final String  DC_ARCH         = "x86";
    public static final String  DC_OS           = "Linux";
    public static final String  DC_VMM          = "Xen";
    public static final double  DC_TIME_ZONE    = 10.0;
    public static final double  DC_COST         = 3.0;
    public static final double  DC_COST_MEM     = 0.05;
    public static final double  DC_COST_STORAGE = 0.001;
    public static final double  DC_COST_BW      = 0.0;

    public static final int     NUM_HOSTS       = 6;
    public static final int     HOST_MIPS       = 8000;
    public static final int     HOST_PES        = 4;
    public static final int     HOST_RAM        = 32768;
    public static final long    HOST_STORAGE    = 1_000_000;
    public static final int     HOST_BW         = 100_000;

    public static final int     NUM_VMS         = 4;
    public static final int     MIN_VMS         = 2;
    public static final int     MAX_VMS         = 8;

    public static final int[]   VM_MIPS_VALUES  = {2000, 3000, 4000, 5000};

    public static final int     VM_PES          = 1;
    public static final int     VM_RAM          = 1024;
    public static final long    VM_BW           = 1000;
    public static final long    VM_SIZE         = 10_000;
    public static final String  VM_VMM          = "Xen";

    public static final int     CONCURRENCY_FACTOR = 2;

    public static final int     NUM_CLOUDLETS    = 20;
    public static final int     CL_PES           = 1;
    public static final long    CL_FILE_SIZE     = 300;
    public static final long    CL_OUTPUT_SIZE   = 300;

    public static final long    CL_LENGTH_SHORT_MIN  =  1200;
    public static final long    CL_LENGTH_SHORT_MAX  =  1800;
    public static final long    CL_LENGTH_MEDIUM_MIN =  2500;
    public static final long    CL_LENGTH_MEDIUM_MAX =  3500;
    public static final long    CL_LENGTH_LONG_MIN   =  4000;
    public static final long    CL_LENGTH_LONG_MAX   =  5500;

    public static final long    CLOUDLET_SEED        = 42L;

    public static final double  IDLE_POWER       = 100.0;
    public static final double  MAX_POWER        = 200.0;

    public static SimulationMode SIMULATION_MODE          = SimulationMode.DYNAMIC;
    public static final double   WORKLOAD_DURATION       = 60.0;
    public static final double   MONITORING_SAMPLE_INTERVAL = 1.0;
    public static final double   MONITORING_WINDOW_SIZE  = 10.0;
    public static final double   MEAN_ARRIVAL_RATE       = 4.0;
    public static final long     WORKLOAD_RANDOM_SEED    = 42L;
    public static final double   RESPONSE_TIME_SLA       = 1.0;
    public static final double   TELEMETRY_PRINT_INTERVAL = 5.0;

    // High decision density: 30 decisions per 60s episode
    public static final double   AUTOSCALING_INTERVAL    = 2.0;

    private SimulationConfig() {}
}