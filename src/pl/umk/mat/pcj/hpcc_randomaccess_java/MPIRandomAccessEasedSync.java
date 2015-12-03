package pl.umk.mat.pcj.hpcc_randomaccess_java;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.logging.Level;
import java.util.logging.Logger;
import pl.umk.mat.pcj.PCJ;
import pl.umk.mat.pcj.Shared;
import pl.umk.mat.pcj.StartPoint;
import pl.umk.mat.pcj.Storage;

public class MPIRandomAccessEasedSync extends Storage implements StartPoint {

    public boolean performVerification = true;
    public boolean timeBound = false;
    /* Allocate main table (in global memory) */
    public static /* unsigned */ long[] HPCC_Table;

    /* "Updates" shared variable holds the updates that have been sent from other threads */
    @Shared
    private long[][] updates;
    long[][] updatesBuffer; //updates that are to be send to participating processes; 
    @Shared
    private long GlbNumUpdates;
    //the following counter is used to indicate, how many times a thread updated the other one; used by the verification procedure
    //in time-bound circumstances
    @Shared
    private long[] targetPECounter;
    //for timeBound algorithm; signifies that one of the threads has reached time limit of execution;
    @Shared
    private boolean bailOut;
    /* OK, so I don't think MPI's communication algorithm translates very well into PGAS 
     * programming paradigm. I am certainly missing somethong, but for the time being
     * I aim to implement just the following algorithm:
     * 
     * 1. Update array indexes, per notifications that have been PUT by other threads
     * 2. Generate 1024 random numbers and distribute them among relevant threads
     * 3. Sync
     * 
     * To account for RandomAccess constraints (i.e. the 1024 elements limit on 
     * incoming and outgoing messages, the following safeguards are put in place:
     * - firstly, we generate only 1024 random elements,
     * - secondly, the amount of elements a thread can PUT into others shared memory equals
     *   1024/number_of_threads
     */

    public void AnyNodesMPIRandomAccessUpdate(HPCC_RandomAccess_tabparams_s tparams) {
        long i = 0, j;
        int proc_count;

        long WhichPe;
        /* unsigned */ long Ran, GlobalOffset, LocalOffset = 0;
        int NumberReceiving = tparams.NumProcs - 1;
        long SendCnt = tparams.ProcNumUpdates; /* SendCnt = (4 * LocalTableSize); */
        Ran = Utility.HPCC_starts(4 * tparams.GlobalStartMyProc);
        long start = System.currentTimeMillis();
        long[][] updates = PCJ.get("updates");
        long GlbNumUpdates = 0;
        long[] targetPECounter = PCJ.get("targetPECounter");
        while (i < SendCnt) {
            updates = PCJ.get("updates");
            // LG's pitiful algorithm is coming!

            //1. Update the indexes; note the safeguards for the first loop, when,
            // for every i, updates[i] == null;


            for (int k = 0; k < updates.length; k++) {
                if (updates[k] != null && k != PCJ.myNode()) {
                    for (int l = 1; l <= updates[k][0]; l++) {
                        long inmsg = updates[k][l];
                        LocalOffset = (inmsg & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                        HPCC_Table[(int) LocalOffset] ^= inmsg;
                        GlbNumUpdates++;
                    }
                    updates[k][0] = 0;
                }
            }

            //2. generate the random numbers and put them into temporary storage
            for (int k = 0; k < updatesBuffer.length; k++) {
                updatesBuffer[k][0] = 0;
            }
            for (int k = 0; k < 1024 && i < SendCnt; k++) {
                Ran = (Ran << 1) ^ ((long) Ran < 0L ? DefineConstants.POLY : 0L);
                GlobalOffset = Ran & (tparams.TableSize - 1);
                if (GlobalOffset < tparams.Top) {
                    WhichPe = (GlobalOffset / (tparams.MinLocalTableSize + 1));
                } else {
                    WhichPe = ((GlobalOffset - tparams.Remainder) / tparams.MinLocalTableSize);
                }
                if (this.timeBound && performVerification == true) {
                    targetPECounter[(int) WhichPe]++;
                }
                if (WhichPe == tparams.MyProc) {
                    LocalOffset = (Ran & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                    HPCC_Table[(int) LocalOffset] ^= Ran;
                    GlbNumUpdates++;
                } else {
                    int z = (int) ++updatesBuffer[(int) WhichPe][0]; //increase number of elements
                    ///in updates buffer
                    updatesBuffer[(int) WhichPe][z] = Ran; //add element to buffer
                }
                i++;
            }
            if (timeBound && (System.currentTimeMillis() - start) > 60_000) {
                PCJ.broadcast("bailOut", true);
            }
      //      PCJ.sync();
            //send the numbers;
            for (int k = 0; k < updatesBuffer.length; k++) {
                int loc = (k + tparams.MyProc) % tparams.NumProcs;
                if (loc == tparams.MyProc) {
                    continue;
                }
                long[] buff = new long[(int) updatesBuffer[loc][0] + 1];
                for (int z = 0; z < buff.length; z++) {
                    buff[z] = updatesBuffer[loc][z];
                }
                PCJ.put(loc, "updates", buff, tparams.MyProc);
            }
            //receive the numbers
            PCJ.waitFor("updates", PCJ.numNodes() - 1);
       //     PCJ.monitor("updates");


            boolean bail = PCJ.get("bailOut");
            if (timeBound && bail == true) {
                break;
            }
        }


        //perform the final update
        for (int k = 0; k < updates.length; k++) {
            if (updates[k] != null && k != PCJ.myNode()) {
                for (int l = 1; l <= updates[k][0]; l++) {
                    long inmsg = updates[k][l];
                    LocalOffset = (inmsg & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                    HPCC_Table[(int) LocalOffset] ^= inmsg;
                    GlbNumUpdates++;
                }
                updates[k][0] = 0;
            }
        }
        PCJ.put("GlbNumUpdates", GlbNumUpdates);
        if (performVerification && timeBound) {
            PCJ.put("targetPECounter", targetPECounter);
        }
    }
    
    public void Power2NodesMPIRandomAccessUpdate(HPCC_RandomAccess_tabparams_s tparams) {
        long i = 0, j;
        int proc_count;

        long WhichPe;
         int logLocalTableSize = (int) (tparams.logTableSize - tparams.logNumProcs);        
        /* unsigned */ long Ran, GlobalOffset, LocalOffset = 0;
        int NumberReceiving = tparams.NumProcs - 1;
        long SendCnt = tparams.ProcNumUpdates; /* SendCnt = (4 * LocalTableSize); */
        Ran = Utility.HPCC_starts(4 * tparams.GlobalStartMyProc);
        long start = System.currentTimeMillis();
        long[][] updates = PCJ.get("updates");
        long GlbNumUpdates = 0;
        long[] targetPECounter = PCJ.get("targetPECounter");
        while (i < SendCnt) {
            updates = PCJ.get("updates");
            // LG's pitiful algorithm is coming!

            //1. Update the indexes; note the safeguards for the first loop, when,
            // for every i, updates[i] == null;


            for (int k = 0; k < updates.length; k++) {
                if (updates[k] != null && k != PCJ.myNode()) {
                    for (int l = 1; l <= updates[k][0]; l++) {
                        long inmsg = updates[k][l];
                        LocalOffset = (inmsg & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                        HPCC_Table[(int) LocalOffset] ^= inmsg;
                        GlbNumUpdates++;
                    }
                    updates[k][0] = 0;
                }
            }

            //2. generate the random numbers and put them into temporary storage
            for (int k = 0; k < updatesBuffer.length; k++) {
                updatesBuffer[k][0] = 0;
            }
            for (int k = 0; k < 1024 && i < SendCnt; k++) {
                Ran = (Ran << 1) ^ ((long) Ran < 0L ? DefineConstants.POLY : 0L);
                WhichPe = (Ran >> logLocalTableSize) & (tparams.NumProcs - 1);
                if (this.timeBound && performVerification == true) {
                    targetPECounter[(int) WhichPe]++;
                }
                if (WhichPe == tparams.MyProc) {
                    LocalOffset = (Ran & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                    HPCC_Table[(int) LocalOffset] ^= Ran;
                    GlbNumUpdates++;
                } else {
                    int z = (int) ++updatesBuffer[(int) WhichPe][0]; //increase number of elements
                    ///in updates buffer
                    updatesBuffer[(int) WhichPe][z] = Ran; //add element to buffer
                }
                i++;
            }
            if (timeBound && (System.currentTimeMillis() - start) > 60_000) {
                PCJ.broadcast("bailOut", true);
            }
    //        PCJ.sync();
            //send the numbers;
            for (int k = 0; k < updatesBuffer.length; k++) {
                int loc = (k + tparams.MyProc) % tparams.NumProcs;
                if (loc == tparams.MyProc) {
                    continue;
                }
                long[] buff = new long[(int) updatesBuffer[loc][0] + 1];
                for (int z = 0; z < buff.length; z++) {
                    buff[z] = updatesBuffer[loc][z];
                }
                PCJ.put(loc, "updates", buff, tparams.MyProc);
            }
            //receive the numbers
            PCJ.waitFor("updates", PCJ.numNodes() - 1);
      //      PCJ.monitor("updates");


            boolean bail = PCJ.get("bailOut");
            if (timeBound && bail == true) {
                break;
            }
        }


        //perform the final update
        for (int k = 0; k < updates.length; k++) {
            if (updates[k] != null && k != PCJ.myNode()) {
                for (int l = 1; l <= updates[k][0]; l++) {
                    long inmsg = updates[k][l];
                    LocalOffset = (inmsg & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                    HPCC_Table[(int) LocalOffset] ^= inmsg;
                    GlbNumUpdates++;
                }
                updates[k][0] = 0;
            }
        }
        PCJ.put("GlbNumUpdates", GlbNumUpdates);
        if (performVerification && timeBound) {
            PCJ.put("targetPECounter", targetPECounter);
        }
    }
    
    
    
    
    
    
    
    
    @Shared
    private long errors = 0;

    private void verifyAnyNodes(HPCC_RandomAccess_tabparams_s tparams) {
        long errors = PCJ.get("errors");
        for (int myProc = 0; myProc < PCJ.numNodes(); myProc++) {
            long GlobalStartMyProc = -1;
            /* determine whether the number of processors is a power of 2 */
            int i;
            for (i = 1, tparams.logNumProcs = 0;; tparams.logNumProcs++, i <<= 1) {
                if (i == tparams.NumProcs) {
                    tparams.Remainder = 0;
                    tparams.Top = 0;
                    tparams.MinLocalTableSize = (tparams.TableSize / tparams.NumProcs);
                    tparams.LocalTableSize = tparams.MinLocalTableSize;
                    GlobalStartMyProc = (tparams.MinLocalTableSize * myProc);
                    break;

                    /* number of processes is not a power 2 (too many shifts may introduce negative values or 0) */

                } else if (i > tparams.NumProcs || i <= 0) {
                    //Tangible multiline preserve/* Minimum local table size --- some processors have an additional entry */
                    tparams.MinLocalTableSize = (tparams.TableSize / tparams.NumProcs);
                    //Tangible multiline preserve/* Number of processors with (tparams.LocalTableSize + 1) entries */
                    tparams.Remainder = (int) (tparams.TableSize - (tparams.MinLocalTableSize * tparams.NumProcs));
                    //Tangible multiline preserve/* Number of table entries in top of Table */
                    tparams.Top = (tparams.MinLocalTableSize + 1) * tparams.Remainder;
                    //Tangible multiline preserve/* Local table size */
                    if (myProc < tparams.Remainder) {
                        tparams.LocalTableSize = (tparams.MinLocalTableSize + 1);
                        GlobalStartMyProc = ((tparams.MinLocalTableSize + 1) * myProc);
                    } else {
                        tparams.LocalTableSize = tparams.MinLocalTableSize;
                        GlobalStartMyProc = ((tparams.MinLocalTableSize * myProc) + tparams.Remainder);
                    }
                    break;

                } // end else if
            } // end for i            
            tparams.ProcNumUpdates = 4 * tparams.LocalTableSize;
            long Ran = Utility.HPCC_starts(4 * GlobalStartMyProc);
            long limit = tparams.ProcNumUpdates;
            long[] arr = null;
            if (timeBound == true) {
                arr = PCJ.get(myProc, "targetPECounter");
            }
            for (i = 0; i < tparams.ProcNumUpdates && i < limit; i++) {
                Ran = (Ran << 1) ^ ((long) Ran < 0L ? DefineConstants.POLY : 0L);
                long GlobalOffset = Ran & (tparams.TableSize - 1);
                long WhichPe = -1;
                if (GlobalOffset < tparams.Top) {
                    WhichPe = (GlobalOffset / (tparams.MinLocalTableSize + 1));
                } else {
                    WhichPe = ((GlobalOffset - tparams.Remainder) / tparams.MinLocalTableSize);
                }
                long LocalOffset = (Ran & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                if (WhichPe == tparams.MyProc && (!timeBound || arr[(int) WhichPe]-- > 0)) {
                    HPCC_Table[(int) LocalOffset] ^= Ran;
                }
            }
        }
        for (int i = 0; i < HPCC_Table.length; i++) {
            if (HPCC_Table[i] != i + tparams.GlobalStartMyProc) {
                errors++;
            }
        }
        PCJ.sync();
        if (PCJ.myNode() == 0) {
            for (int i = 1; i < PCJ.numNodes(); i++) {
                errors += (long) PCJ.get(i, "errors");
            }
            PCJ.put("errors", errors);
        }
        PCJ.sync();
    }

    public void HPCC_MPIRandomAccess(long bits, String outFileName) {

        long i, NumErrors, GlbNumErrors;

        double CPUTime; // CPU  time to update table
        double RealTime; // Real time to update table

        double TotalMem;
        int sAbort;
        int rAbort;
        int PowerofTwo;

        double timeBound = -1; // OPTIONAL time bound for execution time
      /* unsigned */ long NumUpdates_Default; // Number of updates to table (suggested: 4x number of table entries)
      /* unsigned */ long NumUpdates; /* actual number of updates to table - may be smaller than                    * NumUpdates_Default due to execution time bounds */

        //C++ TO JAVA CONVERTER TODO TASK: There is no preprocessor in Java:
        ///#if RA_TIME_BOUND
        long localProcNumUpdates; // for reduction




        long MPIRandomAccess_ExeUpdates;
        double MPIRandomAccess_TimeBound;


        //LG's pitiful algorithm!!!!
        PCJ.put("updates", new long[PCJ.numNodes()][]);
        PCJ.monitor("updates");
        updatesBuffer = new long[PCJ.numNodes()][];
        if (performVerification && this.timeBound == true) {
            PCJ.put("targetPECounter", new long[PCJ.numNodes()]);
        }
        for (int k = 0; k < updatesBuffer.length; k++) {
            //           savedUpdates[k] = new ArrayList<>();
            updatesBuffer[k] = new long[1025];
            //         updates[k] = new long[1];
        }

        PrintWriter outFile = null;

        double GUPs;

        HPCC_RandomAccess_tabparams_s tparams = new HPCC_RandomAccess_tabparams_s();

        tparams.NumProcs = PCJ.numNodes();
        tparams.MyProc = PCJ.myNode();

        if (0 == tparams.MyProc) {
            if (outFileName != null) {
                try {
                    outFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFileName))));
                } catch (Throwable t) {
                    outFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.err)));
                }
            } else {
                outFile = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.err)));
            }
        }

        /*  TotalMem = HPLMaxProcMem; // max single node memory
         TotalMem *= tparams.NumProcs; // max memory in tparams.NumProcs nodes
         TotalMem /= 8; //sizeof(u64Int);*/

        TotalMem = Math.pow(2, bits);


        /* calculate TableSize --- the size of update array (must be a power of 2) */
        for (TotalMem *= 0.5, tparams.logTableSize = 0, tparams.TableSize = 1; TotalMem >= 1.0; TotalMem *= 0.5, tparams.logTableSize++, tparams.TableSize <<= 1) {
            ; // EMPTY
        }
        /* determine whether the number of processors is a power of 2 */
        for (i = 1, tparams.logNumProcs = 0;; tparams.logNumProcs++, i <<= 1) {
            if (i == tparams.NumProcs) {
                PowerofTwo = DefineConstants.HPCC_TRUE;
                tparams.Remainder = 0;
                tparams.Top = 0;
                tparams.MinLocalTableSize = (tparams.TableSize / tparams.NumProcs);
                tparams.LocalTableSize = tparams.MinLocalTableSize;
                tparams.GlobalStartMyProc = (tparams.MinLocalTableSize * tparams.MyProc);
                break;

                /* number of processes is not a power 2 (too many shifts may introduce negative values or 0) */

            } else if (i > tparams.NumProcs || i <= 0) {
                PowerofTwo = DefineConstants.HPCC_FALSE;
                //Tangible multiline preserve/* Minimum local table size --- some processors have an additional entry */
                tparams.MinLocalTableSize = (tparams.TableSize / tparams.NumProcs);
                //Tangible multiline preserve/* Number of processors with (tparams.LocalTableSize + 1) entries */
                tparams.Remainder = (int) (tparams.TableSize - (tparams.MinLocalTableSize * tparams.NumProcs));
                //Tangible multiline preserve/* Number of table entries in top of Table */
                tparams.Top = (tparams.MinLocalTableSize + 1) * tparams.Remainder;
                //Tangible multiline preserve/* Local table size */
                if (tparams.MyProc < tparams.Remainder) {
                    tparams.LocalTableSize = (tparams.MinLocalTableSize + 1);
                    tparams.GlobalStartMyProc = ((tparams.MinLocalTableSize + 1) * tparams.MyProc);
                } else {
                    tparams.LocalTableSize = tparams.MinLocalTableSize;
                    tparams.GlobalStartMyProc = ((tparams.MinLocalTableSize * tparams.MyProc) + tparams.Remainder);
                }
                break;

            } // end else if
        } // end for i


        sAbort = 0;
        HPCC_Table = new /* unsigned */ long[(int) tparams.LocalTableSize];

        long MPIRandomAccess_N = (long) tparams.TableSize;

        //Tangible multiline preserve/* Default number of global updates to table: 4x number of table entries */
        NumUpdates_Default = 4 * tparams.TableSize;
        tparams.ProcNumUpdates = 4 * tparams.LocalTableSize;
        NumUpdates = NumUpdates_Default;


        tparams.ProcNumUpdates = /*Math.min(GlbNumUpdates, (*/ ((4 * tparams.LocalTableSize));
        /* works for both PowerofTwo and AnyNodes */
        NumUpdates = Math.min((tparams.ProcNumUpdates * tparams.NumProcs), (long) NumUpdates_Default);





        /* Initialize main table */
        for (i = 0; i < tparams.LocalTableSize; i++) {
            HPCC_Table[(int) i] = i + tparams.GlobalStartMyProc;
        }

        PCJ.sync();


        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        boolean CPUTimeSupported = bean.isCurrentThreadCpuTimeSupported();
        CPUTime = 0;
        if (CPUTimeSupported) {
            CPUTime = -bean.getCurrentThreadCpuTime();
        }
        RealTime = -System.nanoTime();
     //   if (tparams.PowerOfTwo) {
            AnyNodesMPIRandomAccessUpdate(tparams);
   //     } else {
   //         Power2NodesMPIRandomAccessUpdate(tparams);
    //    }
       /* End timed section */
        if (CPUTimeSupported) {
            CPUTime += bean.getCurrentThreadCpuTime();
        }
        RealTime += System.nanoTime();

        /* Print timing results */
        long GlbNumUpdates = PCJ.get("GlbNumUpdates");
        if (this.timeBound == true) {
            PCJ.sync();
            if (PCJ.myNode() == 0) {
                for (int t = 1; t < PCJ.numNodes(); t++) {
                    GlbNumUpdates += (long) PCJ.get(t, "GlbNumUpdates");
                }
                NumUpdates = GlbNumUpdates;
            }
        }
        if (tparams.MyProc == 0) {
            outFile.printf("Running on %d processors%s\n", tparams.NumProcs, PowerofTwo != 0 ? " (PowerofTwo)" : "");
            outFile.printf("Total Main table size = 2^%d = %d words\n", tparams.logTableSize, tparams.TableSize);
            if (PowerofTwo != 0) {
                outFile.printf("PE Main table size = 2^%d = %d words/PE\n", (tparams.logTableSize - tparams.logNumProcs), tparams.TableSize / tparams.NumProcs);
            } else {
                outFile.printf("PE Main table size = (2^%d)/%d  = %d words/PE MAX\n", tparams.logTableSize, tparams.NumProcs, tparams.LocalTableSize);
            }

            outFile.printf("Default number of updates (RECOMMENDED) = %d\n", NumUpdates_Default);
            if (this.timeBound == true) {
                outFile.printf("Number of updates EXECUTED = %d (for a TIME BOUND of %.2f secs)\n", NumUpdates, 60.0);
            }
            MPIRandomAccess_ExeUpdates = NumUpdates;
            MPIRandomAccess_TimeBound = timeBound;
        }

        CPUTime *= 1e-9;
        RealTime *= 1e-9;
        if (tparams.MyProc == 0) {
            GUPs = 1e-9 * NumUpdates / RealTime;
            outFile.printf("CPU time used = %.6f seconds\n", CPUTime);
            outFile.printf("Real time used = %.6f seconds\n", RealTime);
            outFile.printf("%.9f Billion(10^9) Updates    per second [GUP/s]\n", GUPs);
            outFile.printf("%.9f Billion(10^9) Updates/PE per second [GUP/s]\n", GUPs / tparams.NumProcs);
            System.err.printf("%.9f Billion(10^9) Updates    per second [GUP/s]\n", GUPs);
            System.err.printf("%.9f Billion(10^9) Updates/PE per second [GUP/s]\n", GUPs / tparams.NumProcs);
        }

        /* Verification phase */

        /* Begin timing here */
        /* End timed section */
        if (CPUTimeSupported) {
            CPUTime = -bean.getCurrentThreadCpuTime();
        }
        RealTime = -System.nanoTime();

        if (performVerification) {
            PCJ.sync();
            verifyAnyNodes(tparams);
        }

        /* End timed section */
        if (CPUTimeSupported) {
            CPUTime += bean.getCurrentThreadCpuTime();
        }
        RealTime += System.nanoTime();

        CPUTime *= 1e-9;
        RealTime *= 1e-9;
        if (tparams.MyProc == 0) {
            outFile.printf("Verification:  CPU time used = %.6f seconds\n", CPUTime);
            outFile.printf("Verification:  Real time used = %.6f seconds\n", RealTime);
            errors = PCJ.get("errors");
            outFile.printf("Found %d errors in %d locations (%s).\n", errors, tparams.TableSize, (errors <= 0.01 * tparams.TableSize) ? "passed" : "failed");
        }

        if (0 == PCJ.myNode() && outFile != null) {
            outFile.close();
        }
    }

    @Override
    public void main() throws Throwable {
        int tableBits = 22;
        PCJ.sync();
        try {
            BufferedReader reader = new BufferedReader(new FileReader("table.file"));
            tableBits = Integer.parseInt(reader.readLine());
            performVerification = Boolean.parseBoolean(reader.readLine());
            timeBound = Boolean.parseBoolean(reader.readLine());
            reader.close();
        } catch (Throwable t) {
            //... do nothing
        }
        System.err.println ("thread#" + PCJ.myNode() + " available CPUs = " + Runtime.getRuntime().availableProcessors());
        HPCC_MPIRandomAccess(tableBits, null);
    }

    public static void main(String[] args) {
        PCJ.start(MPIRandomAccessEasedSync.class, MPIRandomAccessEasedSync.class, "nodes.file");
    }
}
