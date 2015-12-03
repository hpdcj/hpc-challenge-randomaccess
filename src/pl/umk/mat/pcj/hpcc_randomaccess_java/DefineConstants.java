package pl.umk.mat.pcj.hpcc_randomaccess_java;

/**
 *
 * @author Łukasz
 */
public final class DefineConstants
{
    public static final long POLY = 0x0000000000000007;
    public static final long PERIOD = 1317624576693539401L;
    public static final int WANT_MPI2_TEST = 0;
    public static final int HPCC_TRUE = 1;
    public static final int HPCC_FALSE = 0;
    public static final int HPCC_DONE = 0;
    public static final int FINISHED_TAG = 1;
    public static final int UPDATE_TAG = 2;
    public static final int USE_NONBLOCKING_SEND = 1;
    public static final int MAX_TOTAL_PENDING_UPDATES = 1024;
    public static final int USE_MULTIPLE_RECV = 1;
    public static final int MAX_RECV = 16;
    //public static final int MAX_RECV = 1;
    public static final long LCG_MUL64 = 6364136223846793005L;
    public static final int LCG_ADD64 = 1;
   // public static final int HPCC_RA_ALGORITHM = 1;
    //public static final int HPCC_RA_ALGORITHM = 2;
    public static final int HPCC_RA_ALGORITHM = 0;
    public static final int HPCC_RA_STDALG = 1;
    public static final int RA_TIME_BOUND = 1;
    public static final int TIME_BOUND = 60;
    public static final int RA_SAMPLE_FACTOR = 100;
}
