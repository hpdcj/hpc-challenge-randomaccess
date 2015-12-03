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
import java.util.Iterator;
import pl.umk.mat.pcj.PCJ;
import pl.umk.mat.pcj.Shared;
import pl.umk.mat.pcj.StartPoint;
import pl.umk.mat.pcj.Storage;

public class MPIRandomAccessHypercube3 extends Storage implements StartPoint {

    public boolean performVerification = true;
    public int timeBound = -1;
    /* Allocate main table (in global memory) */
    public static /* unsigned */ long[] HPCC_Table;
    @Shared
    private long GlbNumUpdates;
    //the following counter is used to indicate, how many times a thread updated the other one; used by the verification procedure
    //in time-bound circumstances
    @Shared
    private long[] targetPECounter;
    //for timeBound algorithm; signifies that one of the threads has reached time limit of execution;
    @Shared
    private boolean bailOut;
    @Shared
    long[] thr0, thr1, thr2, thr3, thr4, thr5, thr6, thr7, thr8, thr9, thr10, thr11, thr12, thr13, thr14, thr15, thr16, thr17, thr18, thr19, thr20, thr21, thr22, thr23, thr24, thr25, thr26, thr27, thr28, thr29, thr30, thr31, thr32;    ArrayList<Long> send = new ArrayList<>();
    ArrayList<Long> keep = new ArrayList<>();
    boolean[] bailed; //for timebound execution
    int updatesNumber;

    public void Power2NodesMPIRandomAccessUpdate(HPCC_RandomAccess_tabparams_s tparams) {
        long i = 0, j;
        int proc_count;

        long WhichPe;

        int logLocalTableSize = (int) (tparams.logTableSize - tparams.logNumProcs);
        /* unsigned */ long Ran, GlobalOffset, LocalOffset = 0;
        long SendCnt = tparams.ProcNumUpdates; /* SendCnt = (4 * LocalTableSize); */
        Ran = Utility.HPCC_starts(4 * tparams.GlobalStartMyProc);
        long start = System.currentTimeMillis();
        long GlbNumUpdates = 0;
        long[] targetPECounter = PCJ.get("targetPECounter");

        int numBailed = 0;

        ArrayList<Long> list = new ArrayList<>();
        while (i < SendCnt) {
            if (timeBound != -1) {
                if (System.currentTimeMillis() - start >= timeBound) {
                    bailed[tparams.MyProc] = true;
                }


                if (bailed[tparams.MyProc] == true) {
                    numBailed = 0;
                    for (int y = 0; y < bailed.length; y++) {
                        if (bailed[y] == true) {
                            numBailed++;
                        }
                    }
                    if (numBailed == Math.log(tparams.NumProcs) / Math.log(2) + 1) {
   //                     System.err.println("thread " + tparams.MyProc + " is bailing out");
                        break;
                    }
                }
            }


            list.clear();
            //1. generate the random numbers
            for (int k = 0; k < 1024 && i < SendCnt; k++) {
                Ran = (Ran << 1) ^ ((long) Ran < 0L ? DefineConstants.POLY : 0L);
                WhichPe = (Ran >> logLocalTableSize) & (tparams.NumProcs - 1);
                if (WhichPe == tparams.MyProc) {
                    LocalOffset = (Ran & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                    HPCC_Table[(int) LocalOffset] ^= Ran;
                    GlbNumUpdates++;
                } else {
                    list.add(Ran);
                }
                if (timeBound != -1 && performVerification) {
                  targetPECounter[(int) WhichPe]++;
                }
                i++; //increase the number of generated random numbers
            }


            //all-to-all hypercube personalized communication, per 
            //http://www.sandia.gov/~sjplimp/docs/cluster06.pdf, p. 5.
            for (int dimension = 0; dimension < Math.log(tparams.NumProcs) / Math.log(2); dimension++) {
                send.clear();
                keep.clear();
                int partner = (1 << dimension) ^ tparams.MyProc;
                long mask = ((1L << dimension) << logLocalTableSize);

                if (partner > tparams.MyProc) {
                    Iterator<Long> iter = list.iterator();
                    while (iter.hasNext()) {
                        long x = iter.next();
                        if ((mask & x) != 0) {
                            send.add(x);
                        } else {
                            keep.add(x);
                        }
                    }
                } else {
                    Iterator<Long> iter = list.iterator();
                    while (iter.hasNext()) {
                        long x = iter.next();
                        if ((mask & x) != 0) {
                            keep.add(x);
                        } else {
                            send.add(x);
                        }
                    }
                }

                long[] buff = new long[send.size() + 1];
                buff[0] = send.size();
                Iterator<Long> iter = send.iterator();
                int z = 1;
                while (iter.hasNext()) {
                    buff[z++] = iter.next();
                }
                send.clear();

                if (timeBound != -1 && bailed[tparams.MyProc] == true) {
                    buff[0] += 100_000;
                }
                if (timeBound == -1 || bailed[partner] == false) {
                    PCJ.put(partner, "thr" + dimension, buff);
                    if (timeBound != -1 && bailed[tparams.MyProc] == true) {
                        bailed[partner] = true;
                    }
                    PCJ.waitFor("thr" + dimension);
                    long[] recv = PCJ.get("thr" + dimension);
                    if (timeBound != -1) {
                        if (recv[0] >= 100_000) {
                            bailed[partner] = true;
                            bailed[tparams.MyProc] = true;
                            recv[0] -= 100_000;
                        }
                    }
                    for (int zz = 1; zz <= recv[0]; zz++) {
                        keep.add(recv[zz]);
                    }
                }

                list.clear();
                Iterator<Long> iter2 = keep.iterator();
                while (iter2.hasNext()) {
                    list.add(iter2.next());
                }
            }

            Iterator iter = list.iterator();
            while (iter.hasNext()) {
                long num = (long) iter.next();
                LocalOffset = (num & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                HPCC_Table[(int) LocalOffset] ^= num;
                GlbNumUpdates++;
            }
        }

        PCJ.put("GlbNumUpdates", GlbNumUpdates);
        if (performVerification && timeBound != -1) {
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
            if (timeBound != -1) {
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
                if (WhichPe == tparams.MyProc && (timeBound == -1 || arr[(int) WhichPe]-- > 0)) {
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

        long i;

        double CPUTime; // CPU  time to update table
        double RealTime; // Real time to update table

        double TotalMem;

        /* unsigned */ long NumUpdates_Default; // Number of updates to table (suggested: 4x number of table entries)
      /* unsigned */ long NumUpdates; /* actual number of updates to table - may be smaller than                    * NumUpdates_Default due to execution time bounds */

        if (performVerification && timeBound != -1) {
            PCJ.put("targetPECounter", new long[PCJ.numNodes()]);
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

        TotalMem = Math.pow(2, bits);


        /* calculate TableSize --- the size of update array (must be a power of 2) */
        for (TotalMem *= 0.5, tparams.logTableSize = 0, tparams.TableSize = 1; TotalMem >= 1.0; TotalMem *= 0.5, tparams.logTableSize++, tparams.TableSize <<= 1) {
            ; // EMPTY
        }
        /* determine whether the number of processors is a power of 2 */
        for (i = 1, tparams.logNumProcs = 0;; tparams.logNumProcs++, i <<= 1) {
            if (i == tparams.NumProcs) {
                tparams.PowerOfTwo = true;
                tparams.Remainder = 0;
                tparams.Top = 0;
                tparams.MinLocalTableSize = (tparams.TableSize / tparams.NumProcs);
                tparams.LocalTableSize = tparams.MinLocalTableSize;
                tparams.GlobalStartMyProc = (tparams.MinLocalTableSize * tparams.MyProc);
                break;
                /* number of processes is not a power 2 (too many shifts may introduce negative values or 0) */
            } else if (i > tparams.NumProcs || i <= 0) {
                tparams.PowerOfTwo = false;
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


        HPCC_Table = new /* unsigned */ long[(int) tparams.LocalTableSize];
        bailed = new boolean[tparams.NumProcs]; //the thread has finished due to time constrints

        //Tangible multiline preserve/* Default number of global updates to table: 4x number of table entries */
        NumUpdates_Default = 4 * tparams.TableSize;
        tparams.ProcNumUpdates = 4 * tparams.LocalTableSize;

        tparams.ProcNumUpdates = /*Math.min(GlbNumUpdates, (*/ ((4 * tparams.LocalTableSize));
        /* works for both PowerofTwo and AnyNodes */
        NumUpdates = Math.min((tparams.ProcNumUpdates * tparams.NumProcs), (long) NumUpdates_Default);

        //System.err.println (PCJ.myNode() + " initializing main table");
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
        if (tparams.PowerOfTwo == true) {
            //       System.err.println ("going power of two");
            Power2NodesMPIRandomAccessUpdate(tparams);
        } else {
            System.err.println("going normal");
            System.err.println("no such procedure!!!!");
            System.exit(42);
        }
        /* End timed section */
        if (CPUTimeSupported) {
            CPUTime += bean.getCurrentThreadCpuTime();
        }
        RealTime += System.nanoTime();

        /* Print timing results */
        long GlbNumUpdates = PCJ.get("GlbNumUpdates");
        if (this.timeBound != -1) {
            PCJ.sync();
            if (PCJ.myNode() == 0) {
                for (int t = 1; t < PCJ.numNodes(); t++) {
                    GlbNumUpdates += (long) PCJ.get(t, "GlbNumUpdates");
                }
                NumUpdates = GlbNumUpdates;
            }
        }
        PCJ.sync();
        if (tparams.MyProc == 0) {
            outFile.printf("Running on %d processors%s\n", tparams.NumProcs, tparams.PowerOfTwo == true ? " (PowerofTwo)" : "");
            outFile.printf("Total Main table size = 2^%d = %d words\n", tparams.logTableSize, tparams.TableSize);
            if (tparams.PowerOfTwo == true) {
                outFile.printf("PE Main table size = 2^%d = %d words/PE\n", (tparams.logTableSize - tparams.logNumProcs), tparams.TableSize / tparams.NumProcs);
            } else {
                outFile.printf("PE Main table size = (2^%d)/%d  = %d words/PE MAX\n", tparams.logTableSize, tparams.NumProcs, tparams.LocalTableSize);
            }

            outFile.printf("Default number of updates (RECOMMENDED) = %d\n", NumUpdates_Default);
            if (this.timeBound != -1) {
                outFile.printf("Number of updates EXECUTED = %d (for a TIME BOUND of %.2f secs)\n", NumUpdates, 60.0);
            }
        }


        CPUTime *= 1e-9;
        RealTime *= 1e-9;
        if (tparams.MyProc == 0) {
            GUPs = 1e-9 * NumUpdates / RealTime;
            outFile.printf("CPU time used = %.6f seconds\n", CPUTime);
            outFile.printf("Real time used = %.6f seconds\n", RealTime);
            outFile.printf("%.9f Billion(10^9) Updates    per second [GUP/s]\n", GUPs);
            outFile.printf("%.9f Billion(10^9) Updates/PE per second [GUP/s]\n", GUPs / tparams.NumProcs);
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
            timeBound = Integer.parseInt(reader.readLine());
            updatesNumber = Integer.parseInt(reader.readLine());   
            reader.close();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
        HPCC_MPIRandomAccess(tableBits, null);
    }

    public static void main(String[] args) {
        PCJ.start(MPIRandomAccessHypercube3.class, MPIRandomAccessHypercube3.class, "nodes.file");
    }
}
