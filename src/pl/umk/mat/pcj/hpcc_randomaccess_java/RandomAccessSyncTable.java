package pl.umk.mat.pcj.hpcc_randomaccess_java;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import pl.umk.mat.pcj.FutureResponse;
import pl.umk.mat.pcj.PCJ;
import pl.umk.mat.pcj.Shared;
import pl.umk.mat.pcj.StartPoint;
import pl.umk.mat.pcj.Storage;
import static pl.umk.mat.pcj.hpcc_randomaccess_java.MPIRandomAccess.HPCC_Table;

public abstract class RandomAccessSyncTable extends Storage implements StartPoint {
//
//    public boolean performVerification = true;
//    public boolean timeBound = false;
//    /* Allocate main table (in global memory) */
//    public static /* unsigned */ long[] HPCC_Table;
//    
//    @Shared
//    public boolean[] blockade;
//    
//    public /* unsigned*/ long[] LocalSendBuffer = new long[DefineConstants.MAX_TOTAL_PENDING_UPDATES];
//    //  public /* unsigned */ long[] LocalRecvBuffer = new long[DefineConstants.MAX_RECV *DefineConstants.MAX_TOTAL_PENDING_UPDATES];
//    public FutureResponse[] LocalRecvBuffer = new FutureResponse[DefineConstants.MAX_RECV * DefineConstants.MAX_TOTAL_PENDING_UPDATES];
//
//    /* "Updates" shared variable holds the updates that have been sent from other threads */
//    @Shared
//    long[][] updates;
//    long[][] updatesBuffer; //updates that are to be send to participating processes; 
//    // updatesBuffer[i][0] stands for number of updates in the buffer
//    ArrayList<Long> savedUpdates[];
//    @Shared
//    long GlbNumUpdates;
//
//    /* OK, we'll try implementing prof. Bała's algorithm with separate array used for
//     * inter - thread synchronisation. The algorithm is to be following:
//     * - generate random number,
//     * - mark a cell in target thread's "blocking array"
//     * - perform update, if cell is marked as not blocked,
//     * - sync
//     * - and so ad infinitum
//     */
//    public void AnyNodesMPIRandomAccessUpdate(HPCC_RandomAccess_tabparams_s tparams) {
//        long i = 0, j;
//        int proc_count;
//
//        long WhichPe;
//        /* unsigned */ long Ran, GlobalOffset, LocalOffset = 0;
//        int NumberReceiving = tparams.NumProcs - 1;
//        long SendCnt = tparams.ProcNumUpdates; /* SendCnt = (4 * LocalTableSize); */
//        Ran = Utility.HPCC_starts(4 * tparams.GlobalStartMyProc);
//
//        long start = System.currentTimeMillis();
//
//        while (i < SendCnt) {
//            // LG's second coming of pitiful algorithm:
//            
//            //1. would you kindly generate a random number?
//            Ran = (Ran << 1) ^ ((long) Ran < 0L ? DefineConstants.POLY : 0L);          
//            
//            //2. where does this remarkable outcome of arcane arts of number manipulation
//            // point to?
//
//                GlobalOffset = Ran & (tparams.TableSize - 1);
//                if (GlobalOffset < tparams.Top) {
//                    WhichPe = (GlobalOffset / (tparams.MinLocalTableSize + 1));
//                } else {
//                    WhichPe = ((GlobalOffset - tparams.Remainder) / tparams.MinLocalTableSize);
//                }
//
//                
//                
//                
//                if (WhichPe == tparams.MyProc) {
//                    LocalOffset = (Ran & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
//                    HPCC_Table[(int) LocalOffset] ^= Ran;
//                    GlbNumUpdates++;
//                } else {
//                    int z = (int) ++updatesBuffer[(int) WhichPe][0]; //increase number of elements
//                    ///in updates buffer
//                    updatesBuffer[(int) WhichPe][z] = Ran; //add element to buffer
//                }
//
//                if (performVerification == true) {
//                    savedUpdates[(int) WhichPe].add(Ran);
//                }
//                i++;
//            }
//            PCJ.sync(); //no errors please!
//
//            //1. Update the indexes; note the safeguards for the first loop, when,
//            // for every i, updates[i] == null;
//            for (int k = 0; k < updates.length; k++) {
//                if (updates[k] != null && k != PCJ.myNode()) {
//                    for (int l = 1; l <= updates[k][0]; l++) {
//                        long inmsg = updates[k][l];
//                        LocalOffset = (inmsg & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
//                        HPCC_Table[(int) LocalOffset] ^= inmsg;
//                        GlbNumUpdates++;
//                    }
//                    updates[k][0] = 0;
//                }
//            }
//
//
//            //send the numbers;
//            for (int k = 0; k < updatesBuffer.length; k++) {
//                if (k == tparams.MyProc) {
//                    continue;
//                }
//                PCJ.put(k, "updates", updatesBuffer[k], tparams.MyProc);
//            }
//            PCJ.waitFor("updates", PCJ.numNodes() - 1);
//            PCJ.monitor("updates");
//
//            if (timeBound && (System.currentTimeMillis() - start) > 60_000) {
//                break;
//            }
//        }
////perform the final update
//        for (int k = 0; k < updates.length; k++) {
//            if (updates[k] != null && k != PCJ.myNode()) {
//                for (int l = 1; l <= updates[k][0]; l++) {
//                    long inmsg = updates[k][l];
//                    LocalOffset = (inmsg & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
//                    HPCC_Table[(int) LocalOffset] ^= inmsg;
//                    GlbNumUpdates++;
//                }
//                updates[k][0] = 0;
//            }
//        }
//    }
//    @Shared
//    long errors = 0;
//    @Shared
//    boolean wouldthePreviousThreadBeSoKindAndWakeMeUp = false;
//
//    private void verifyAnyNodes(HPCC_RandomAccess_tabparams_s tparams) {
//        PCJ.monitor("updates");
//        PCJ.sync();
//        if (PCJ.myNode() != 0) {
//            PCJ.waitFor("wouldthePreviousThreadBeSoKindAndWakeMeUp");
//        }
//        for (int i = 0; i < savedUpdates.length; i++) {
//            long[] arr = new long[savedUpdates[i].size()];
//            for (int j = 0; j < arr.length; j++) {
//                arr[j] = savedUpdates[i].get(j);
//            }
//            PCJ.put(i, "updates", arr, PCJ.myNode());
//        }
//        if (PCJ.myNode() != PCJ.numNodes() - 1) {
//            PCJ.put(PCJ.myNode() + 1, "wouldthePreviousThreadBeSoKindAndWakeMeUp", true);
//        }
//        PCJ.waitFor("updates", PCJ.numNodes());
//        PCJ.sync();
//        for (int i = 0; i < updates.length; i++) {
//            if (updates[i] != null) {
//                for (int j = 0; j < updates[i].length; j++) {
//                    long LocalOffset = (updates[i][j] & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
//                    HPCC_Table[(int) LocalOffset] ^= updates[i][j];
//                }
//            }
//        }
//        for (int i = 0; i < HPCC_Table.length; i++) {
//            if (HPCC_Table[i] != i + tparams.GlobalStartMyProc) {
//                errors++;
//            }
//        }
//
//        PCJ.sync();
//        if (PCJ.myNode() == 0) {
//            for (int i = 1; i < PCJ.numNodes(); i++) {
//                errors += (long) PCJ.get(i, "errors");
//            }
//        }
//        PCJ.sync();
//    }
//
//    public void HPCC_MPIRandomAccess(long bits, String outFileName) {
//
//        long i, NumErrors, GlbNumErrors;
//
//        double CPUTime; // CPU  time to update table
//        double RealTime; // Real time to update table
//
//        double TotalMem;
//        int sAbort;
//        int rAbort;
//        int PowerofTwo;
//
//        double timeBound = -1; // OPTIONAL time bound for execution time
//      /* unsigned */ long NumUpdates_Default; // Number of updates to table (suggested: 4x number of table entries)
//      /* unsigned */ long NumUpdates; /* actual number of updates to table - may be smaller than                    * NumUpdates_Default due to execution time bounds */
//
//        //C++ TO JAVA CONVERTER TODO TASK: There is no preprocessor in Java:
//        ///#if RA_TIME_BOUND
//        long localProcNumUpdates; // for reduction
//
//
//
//
//        long MPIRandomAccess_ExeUpdates;
//        double MPIRandomAccess_TimeBound;
//
//
//        //LG's pitiful algorithm!!!!
//        updates = new long[PCJ.numNodes()][];
//        updatesBuffer = new long[PCJ.numNodes()][];
//        savedUpdates = new ArrayList[PCJ.numNodes()];
//        for (int k = 0; k < savedUpdates.length; k++) {
//            savedUpdates[k] = new ArrayList<>();
//            updatesBuffer[k] = new long[1025];
//            //         updates[k] = new long[1];
//        }
//
//        PrintWriter outFile = null;
//
//        double GUPs;
//
//        HPCC_RandomAccess_tabparams_s tparams = new HPCC_RandomAccess_tabparams_s();
//
//        tparams.NumProcs = PCJ.numNodes();
//        tparams.MyProc = PCJ.myNode();
//
//        if (0 == tparams.MyProc) {
//            if (outFileName != null) {
//                try {
//                    outFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFileName))));
//                } catch (Throwable t) {
//                    outFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.err)));
//                }
//            } else {
//                outFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.err)));
//            }
//        }
//
//        /*  TotalMem = HPLMaxProcMem; // max single node memory
//         TotalMem *= tparams.NumProcs; // max memory in tparams.NumProcs nodes
//         TotalMem /= 8; //sizeof(u64Int);*/
//
//        TotalMem = Math.pow(2, bits);
//
//
//        /* calculate TableSize --- the size of update array (must be a power of 2) */
//        for (TotalMem *= 0.5, tparams.logTableSize = 0, tparams.TableSize = 1; TotalMem >= 1.0; TotalMem *= 0.5, tparams.logTableSize++, tparams.TableSize <<= 1) {
//            ; // EMPTY
//        }
//
//        /* determine whether the number of processors is a power of 2 */
//        for (i = 1, tparams.logNumProcs = 0;; tparams.logNumProcs++, i <<= 1) {
//            if (i == tparams.NumProcs) {
//                PowerofTwo = DefineConstants.HPCC_TRUE;
//                tparams.Remainder = 0;
//                tparams.Top = 0;
//                tparams.MinLocalTableSize = (tparams.TableSize / tparams.NumProcs);
//                tparams.LocalTableSize = tparams.MinLocalTableSize;
//                tparams.GlobalStartMyProc = (tparams.MinLocalTableSize * tparams.MyProc);
//                break;
//
//                /* number of processes is not a power 2 (too many shifts may introduce negative values or 0) */
//
//            } else if (i > tparams.NumProcs || i <= 0) {
//                PowerofTwo = DefineConstants.HPCC_FALSE;
//                //Tangible multiline preserve/* Minimum local table size --- some processors have an additional entry */
//                tparams.MinLocalTableSize = (tparams.TableSize / tparams.NumProcs);
//                //Tangible multiline preserve/* Number of processors with (tparams.LocalTableSize + 1) entries */
//                tparams.Remainder = (int) (tparams.TableSize - (tparams.MinLocalTableSize * tparams.NumProcs));
//                //Tangible multiline preserve/* Number of table entries in top of Table */
//                tparams.Top = (tparams.MinLocalTableSize + 1) * tparams.Remainder;
//                //Tangible multiline preserve/* Local table size */
//                if (tparams.MyProc < tparams.Remainder) {
//                    tparams.LocalTableSize = (tparams.MinLocalTableSize + 1);
//                    tparams.GlobalStartMyProc = ((tparams.MinLocalTableSize + 1) * tparams.MyProc);
//                } else {
//                    tparams.LocalTableSize = tparams.MinLocalTableSize;
//                    tparams.GlobalStartMyProc = ((tparams.MinLocalTableSize * tparams.MyProc) + tparams.Remainder);
//                }
//                break;
//
//            } // end else if
//        } // end for i
//
//
//        sAbort = 0;
//        HPCC_Table = new /* unsigned */ long[(int) tparams.LocalTableSize];
//        blockade = new boolean [(int)tparams.LocalTableSize];
//        
//        long MPIRandomAccess_N = (long) tparams.TableSize;
//
//        //Tangible multiline preserve/* Default number of global updates to table: 4x number of table entries */
//        NumUpdates_Default = 4 * tparams.TableSize;
//        tparams.ProcNumUpdates = 4 * tparams.LocalTableSize;
//        NumUpdates = NumUpdates_Default;
//
//
//        tparams.ProcNumUpdates = /*Math.min(GlbNumUpdates, (*/ ((4 * tparams.LocalTableSize));
//        /* works for both PowerofTwo and AnyNodes */
//        NumUpdates = Math.min((tparams.ProcNumUpdates * tparams.NumProcs), (long) NumUpdates_Default);
//
//
//
//
//
//        /* Initialize main table */
//        for (i = 0; i < tparams.LocalTableSize; i++) {
//            HPCC_Table[(int) i] = i + tparams.GlobalStartMyProc;
//        }
//
//        PCJ.sync();
//
//
//        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
//        boolean CPUTimeSupported = bean.isCurrentThreadCpuTimeSupported();
//        CPUTime = 0;
//        if (CPUTimeSupported) {
//            CPUTime = -bean.getCurrentThreadCpuTime();
//        }
//        RealTime = -System.nanoTime();
//        AnyNodesMPIRandomAccessUpdate(tparams);
//        /* End timed section */
//        if (CPUTimeSupported) {
//            CPUTime += bean.getCurrentThreadCpuTime();
//        }
//        RealTime += System.nanoTime();
//
//        /* Print timing results */
//
//        if (this.timeBound == true) {
//            PCJ.sync();
//            if (PCJ.myNode() == 0) {
//                for (int t = 1; t < PCJ.numNodes(); t++) {
//                    GlbNumUpdates += (long) PCJ.get(t, "GlbNumUpdates");
//                }
//                NumUpdates = GlbNumUpdates;
//            }
//        }
//
//        if (tparams.MyProc == 0) {
//            outFile.printf("Running on %d processors%s\n", tparams.NumProcs, PowerofTwo != 0 ? " (PowerofTwo)" : "");
//            outFile.printf("Total Main table size = 2^%d = %d words\n", tparams.logTableSize, tparams.TableSize);
//            if (PowerofTwo != 0) {
//                outFile.printf("PE Main table size = 2^%d = %d words/PE\n", (tparams.logTableSize - tparams.logNumProcs), tparams.TableSize / tparams.NumProcs);
//            } else {
//                outFile.printf("PE Main table size = (2^%d)/%d  = %d words/PE MAX\n", tparams.logTableSize, tparams.NumProcs, tparams.LocalTableSize);
//            }
//
//            outFile.printf("Default number of updates (RECOMMENDED) = %d\n", NumUpdates_Default);
//            if (this.timeBound == true) {
//                outFile.printf("Number of updates EXECUTED = %d (for a TIME BOUND of %.2f secs)\n", NumUpdates, 60.0);
//            }
//            MPIRandomAccess_ExeUpdates = NumUpdates;
//            MPIRandomAccess_TimeBound = timeBound;
//        }
//
//        CPUTime *= 1e-9;
//        RealTime *= 1e-9;
//        if (tparams.MyProc == 0) {
//            GUPs = 1e-9 * NumUpdates / RealTime;
//            outFile.printf("CPU time used = %.6f seconds\n", CPUTime);
//            outFile.printf("Real time used = %.6f seconds\n", RealTime);
//            outFile.printf("%.9f Billion(10^9) Updates    per second [GUP/s]\n", GUPs);
//            outFile.printf("%.9f Billion(10^9) Updates/PE per second [GUP/s]\n", GUPs / tparams.NumProcs);
//
//        }
//
//        /* Verification phase */
//
//        /* Begin timing here */
//        /* End timed section */
//        if (CPUTimeSupported) {
//            CPUTime = -bean.getCurrentThreadCpuTime();
//        }
//        RealTime = -System.nanoTime();
//
//        if (performVerification) {
//            verifyAnyNodes(tparams);
//        }
//
//        /* End timed section */
//        if (CPUTimeSupported) {
//            CPUTime += bean.getCurrentThreadCpuTime();
//        }
//        RealTime += System.nanoTime();
//
//        CPUTime *= 1e-9;
//        RealTime *= 1e-9;
//        if (tparams.MyProc == 0) {
//            outFile.printf("Verification:  CPU time used = %.6f seconds\n", CPUTime);
//            outFile.printf("Verification:  Real time used = %.6f seconds\n", RealTime);
//            outFile.printf("Found %d errors in %d locations (%s).\n", errors, tparams.TableSize, (errors <= 0.01 * tparams.TableSize) ? "passed" : "failed");
//        }
//
//        if (0 == PCJ.myNode() && outFile != null) {
//            outFile.close();
//        }
//    }
//
//    @Override
//    public void main() throws Throwable {
//        int tableBits = 22;
//        PCJ.sync();
//        try {
//            BufferedReader reader = new BufferedReader(new FileReader("table.file"));
//            tableBits = Integer.parseInt(reader.readLine());
//            performVerification = Boolean.parseBoolean(reader.readLine());
//            timeBound = Boolean.parseBoolean(reader.readLine());
//            reader.close();
//        } catch (Throwable t) {
//            //... do nothing
//        }
//        HPCC_MPIRandomAccess(tableBits, null);
//    }
//
//    public static void main(String[] args) {
//        PCJ.deploy(RandomAccessSyncTable.class, RandomAccessSyncTable.class);
//    }
}
