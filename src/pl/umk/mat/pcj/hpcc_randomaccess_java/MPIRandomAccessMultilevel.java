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

public class MPIRandomAccessMultilevel extends Storage implements StartPoint {

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
    private int threadsPerNode;
    private int updatesNumber;
    @Shared
    long[][] updatesFromLocalThreads; // updates that have been done by threads residing on the same computing node
    @Shared
    long[] updatesToBeSentToRemoteThreadRepresentative;
    long[][] bufferedUpdates;
    long[][] sameNodeUpdatesBufferSend;
    @Shared
    long[][] sameNodeUpdatesBufferRecv;
    // buffer with updates that are to be sent to remote nodes
    //the buffer is structured as follows:
    // first dimension - index of PE that is sending the updates
    // second  "       - index of PE that the updates pertain to
    // third   "       - the updates themselves
    @Shared
    long[][][] representativeUpdatesBuffer;
    //in the following table the memory layout is changed:
    // 1st dimension - representative id
    // 2n dimension - PE identifier, which subordinates to that representative
    // 3rd dimension - updates for that PE
    long[][][] updatesForOtherRepresentatives;
    // first dimension - source representative
    // second target threads
    // third updates
    @Shared
    long[][][] updatesFromOtherRepresentatives;
    long[][] updatesForRepresentative;
    long[][] updatesForRepresentativeShortened;
    //first dimension - sender id
    //second - updates
    @Shared
    long[][] updatesFromRemoteThreads;

    public void AnyNodesMPIRandomAccessUpdate(HPCC_RandomAccess_tabparams_s tparams) {
        int proc_count;

        long WhichPe;
        /* unsigned */ long Ran, GlobalOffset, LocalOffset = 0;
        int NumberReceiving = tparams.NumProcs - 1;
        long SendCnt = tparams.ProcNumUpdates; /* SendCnt = (4 * LocalTableSize); */
        Ran = Utility.HPCC_starts(4 * tparams.GlobalStartMyProc);
        //       System.err.println ("First = " + Ran);
        long start = System.currentTimeMillis();
        long[][] updates = PCJ.get("updates");
        long GlbNumUpdates = 0;
        long[] targetPECounter = PCJ.get("targetPECounter");


        long myNode = tparams.MyProc / threadsPerNode; //my computing node location
        long representative = myNode * threadsPerNode;

        long thisNodeThreadsNumber = threadsPerNode;

        int numberOfRepresentatives = (int) Math.ceil((double) tparams.NumProcs / threadsPerNode);
        if (thisNodeThreadsNumber * (myNode + 1) > tparams.NumProcs) {
            thisNodeThreadsNumber = tparams.NumProcs % threadsPerNode;
        }
        int[] localThreads = new int[(int) thisNodeThreadsNumber];
        for (int i = 0; i < thisNodeThreadsNumber; i++) {
            localThreads[i] = i + (int) representative;
        }

        sameNodeUpdatesBufferSend = new long[(int) thisNodeThreadsNumber][1025];
        sameNodeUpdatesBufferRecv = new long[(int) thisNodeThreadsNumber][];
        updatesForRepresentative = new long[tparams.NumProcs][1025];
        updatesForRepresentativeShortened = new long[tparams.NumProcs][];
        updatesFromOtherRepresentatives = new long[numberOfRepresentatives][][];
        updatesFromRemoteThreads = new long[numberOfRepresentatives][];
        int sizes[] = new int[tparams.NumProcs];
        int savedPositions[] = new int[tparams.NumProcs];
        if (tparams.MyProc == representative) {
            representativeUpdatesBuffer = new long[(int) thisNodeThreadsNumber][][];
            updatesForOtherRepresentatives = new long[numberOfRepresentatives][threadsPerNode][];
        }
        for (int generated = 0; generated < SendCnt;) {
            //     System.err.println (generated);
            //1. generate the random numbers; modify the table in situ, put the updates to local threads, send the remote to representative

            //clean the storage tables
            for (int k = 0; k < sameNodeUpdatesBufferSend.length; k++) {
                sameNodeUpdatesBufferSend[k][0] = 0;
            }
            for (int k = 0; k < updatesForRepresentative.length; k++) {
                updatesForRepresentative[k][0] = 0;
            }
            //generate the data and put it in relevant buffer
            for (int k = 0; k < 1024 && generated < SendCnt; k++, generated++) {
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
                if (WhichPe == tparams.MyProc) { //same-thread updates
                    LocalOffset = (Ran & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                    HPCC_Table[(int) LocalOffset] ^= Ran;
                    //            System.err.println ("Thread #" + PCJ.myNode() + " is putting a value of " + Ran + " into local table at offset " + LocalOffset);
                    GlbNumUpdates++;
                } else if (WhichPe / threadsPerNode == myNode) { //same computing node
                    int z = (int) ++sameNodeUpdatesBufferSend[(int) (WhichPe - representative)][0]; //increase number of elements in updates buffer
                    sameNodeUpdatesBufferSend[(int) (WhichPe - representative)][z] = Ran; //add element to buffer
                    //         System.err.println ("Thread #" + PCJ.myNode() + " is putting a value of " + Ran + " into local thread no " + WhichPe);                    
                } else { //remote computing node -> put the updated into array that is to be send to representative
                    int z = (int) ++updatesForRepresentative[(int) WhichPe][0];
                    updatesForRepresentative[(int) WhichPe][z] = Ran;
                    //       System.err.println ("Thread #" + PCJ.myNode() + " is putting a value of " + Ran + " into remote thread no " + WhichPe);
                }
            }

//            String output = "Remote send buffer for thread #" + PCJ.myNode() + " is following-looking: \n";
//            for (int i = 0; i < updatesForRepresentative.length; i++) {
//                output += " target = " + i + " values = ";
//                for (int j = 0; j <= updatesForRepresentative[i][0]; j++) {
//                    output += updatesForRepresentative[i][j] + " ";
//                }
//                output += "\n";
//            }
//            System.err.println (output);

            //send the updates to local nodes

            for (int i = 0; i < sameNodeUpdatesBufferSend.length; i++) {
                if (i == tparams.MyProc - representative) {
                    continue;
                }
                long[] buff = new long[(int) sameNodeUpdatesBufferSend[i][0] + 1];
                for (int t = 0; t < buff.length; t++) {
                    buff[t] = sameNodeUpdatesBufferSend[i][t];
                }
                PCJ.put((int) (representative + i), "sameNodeUpdatesBufferRecv", buff, (int) (tparams.MyProc - representative));
            }
            //  System.err.println ("table preparing");
            //prepare representative' table
            for (int i = 0; i < updatesForRepresentative.length; i++) {
                updatesForRepresentativeShortened[i] = new long[(int) updatesForRepresentative[i][0] + 1];
                for (int j = 0; j < updatesForRepresentativeShortened[i].length; j++) {
                    updatesForRepresentativeShortened[i][j] = updatesForRepresentative[i][j];
                }
            }


            //send the table to representative
            if (tparams.MyProc != representative) {
                PCJ.put((int) representative, "representativeUpdatesBuffer", updatesForRepresentativeShortened, (int) (tparams.MyProc - representative));
            } else {
                representativeUpdatesBuffer[0] = updatesForRepresentativeShortened;
            }

            // receive the updates
            PCJ.waitFor("sameNodeUpdatesBufferRecv", (int) thisNodeThreadsNumber - 1);


            if (tparams.MyProc == representative) {
                PCJ.waitFor("representativeUpdatesBuffer", (int) thisNodeThreadsNumber - 1);
//                String outputs = "Representative thread no " + tparams.MyProc + " has finished gathering data from subordinates\n";
//                for (int sender = 0; sender < representativeUpdatesBuffer.length; sender ++) {
//                    outputs += " sender = " + sender + " ";
//                    for (int target = 0; target < representativeUpdatesBuffer[sender].length; target++) {
//                        outputs += " target = " + target + " ";
//                        for (int u = 0; u <= representativeUpdatesBuffer[sender][target][0]; u++) {
//                            outputs += representativeUpdatesBuffer[sender][target][u] + " ";
//                        }
//                    }
//                    outputs += "\n";
//                }
//                System.err.println (outputs);
                //change the table format to something more manageable
                //firstly, allocate the memory

                for (int pe = 0; pe < representativeUpdatesBuffer.length; pe++) {
                    for (int target = 0; target < tparams.NumProcs; target++) {
                        sizes[target] += representativeUpdatesBuffer[pe][target][0];
                    }
                }
                //           String w = "Thread #" + PCJ.myNode() + " " + "sizes = ";
                //           for (int i = 0; i < sizes.length; i++) {
                //               w += sizes[i] + " ";
                //          }
                //           System.err.println (w);
//                //now -> change the format of the data to suitable for intra-representative communication
                for (int repr = 0; repr < numberOfRepresentatives; repr++) {
                    for (int target = 0; target < threadsPerNode; target++) {
                        int globalId = repr * threadsPerNode + target;
                        if (globalId == tparams.NumProcs) {
                            break;
                        }
                        if (globalId / threadsPerNode == myNode) {
                            continue;
                        }
                        updatesForOtherRepresentatives[repr][target] = new long[sizes[globalId]];
                        sizes[globalId] = 0;
                        for (int pe = 0, z = 0; pe < representativeUpdatesBuffer.length; pe++) {
                            for (int idx = 1; idx <= representativeUpdatesBuffer[pe][globalId][0]; idx++) {
                                long kk = updatesForOtherRepresentatives[repr][target][z++] = representativeUpdatesBuffer[pe][globalId][idx];
                                //                    System.err.println (PCJ.myNode() + " " + kk);
                            }
                        }
                    }
                }
                //              String ww = " Sizes for thread #" + PCJ.myNode() + " after transformation";
                //             for (int i = 0; i < sizes.length; i++) {
                //                ww += " " + sizes[i];
                //           }
                //              System.err.println (ww);
                //              String outputss = " Thread #" + PCJ.myNode() + " has finished transforming the data for intra-representative communication\n";
                //               for (int re = 0; re < tparams.NumProcs / threadsPerNode; re++) {
                //                   outputss += "representative = " + re * threadsPerNode + "/" ; if (re == tparams.MyProc) continue;
//                    for (int pe = 0; pe < updatesForOtherRepresentatives[re].length; pe++) {
//                        outputss += "pe = " + (pe * threadsPerNode) + " ";
//                        for (int i = 0; i < updatesForOtherRepresentatives[re][pe].length; i++) {
//                            outputss += updatesForOtherRepresentatives[re][pe][i] + " ";
//                        }
//                        outputss += "\n";
//                    }
                //             }
                //          System.err.println (outputss);
                //               PCJ.sync(0, 2);
                for (int i = 0; i < updatesForOtherRepresentatives.length; i++) {
                    if (i * threadsPerNode == tparams.MyProc) {
                        continue;
                    }
                    //                   System.err.println ("Thread #" + PCJ.myNode() + " is putting update for thread #" + i);
                    PCJ.put(i * threadsPerNode, "updatesFromOtherRepresentatives", updatesForOtherRepresentatives[i], (int) tparams.MyProc / threadsPerNode);
                }
            }
//PCJ.sync(localThreads); //check if it's really needed
            // carry out the suckers
            for (int i = 0; i < sameNodeUpdatesBufferRecv.length; i++) {
                if (tparams.MyProc - representative == i) {
                    continue;
                }
                for (int j = 1; j <= sameNodeUpdatesBufferRecv[i][0]; j++) {
                    long inmsg = sameNodeUpdatesBufferRecv[i][j];
                    LocalOffset = (inmsg & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                    HPCC_Table[(int) LocalOffset] ^= inmsg;
                    GlbNumUpdates++;
                }
            }

            if (tparams.MyProc == representative) {
                //             System.err.println ("representative Thread #" + PCJ.myNode() + " is waiting for updates from other representatives");
                PCJ.waitFor("updatesFromOtherRepresentatives", numberOfRepresentatives - 1);
                //              System.err.println ("representative Thread #" + PCJ.myNode() + " finished waiting for updates from other representatives");                
                for (int i = 0; i < numberOfRepresentatives; i++) {
                    if (i * threadsPerNode == tparams.MyProc) {
                        continue;
                    }
                    for (int j = 0; j < thisNodeThreadsNumber; j++) {
                        int globalID = (int) representative + j;
                        PCJ.put(globalID, "updatesFromRemoteThreads", updatesFromOtherRepresentatives[i][j], i);
//                        String ooo = "Thread #" + PCJ.myNode() + " is putting following data to thread's " + globalID + " updateFromRemoteThreads[" + i + "]\n";
//                        for (int k = 0;  k < updatesFromOtherRepresentatives[i][j].length; k++) {
//                            ooo += updatesFromOtherRepresentatives[i][j][k] + " ";
//                        }
//                        ooo += "\n";
//                        System.err.println (ooo);
                    }
                }
            } else {
                for (int limit = 0; limit < numberOfRepresentatives; limit++) {
                    if (limit == representative / tparams.NumProcs) {
                        continue;
                    }
                    PCJ.waitFor("updatesFromRemoteThreads");
                    if (updatesFromRemoteThreads[limit] != null) {
                        for (int upd = 0; upd < updatesFromRemoteThreads[limit].length; upd++) {
                            long inmsg = updatesFromRemoteThreads[limit][upd];
                            LocalOffset = (inmsg & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                            HPCC_Table[(int) LocalOffset] ^= inmsg;
                            GlbNumUpdates++;
                        }
                    }
                }
            }

            if (tparams.MyProc == representative) {
                for (int limit = 0; limit < numberOfRepresentatives; limit++) {
                    if (limit * threadsPerNode == tparams.MyProc) {
                        continue;
                    }
                    //            System.err.println ("representative thread #" + PCJ.myNode() + " is waiting as plain thread for updatesFromRemoteThreads\n");
                    PCJ.waitFor("updatesFromRemoteThreads");
                    //           System.err.println ("representative thread #" + PCJ.myNode() + " finished waiting as plain thread for updatesFromRemoteThreads\n");                    
                    //           System.err.println (updatesFromRemoteThreads[limit]);
                    if (updatesFromRemoteThreads[limit] != null) {
                        for (int upd = 0; upd < updatesFromRemoteThreads[limit].length; upd++) {
                            long inmsg = updatesFromRemoteThreads[limit][upd];
                            LocalOffset = (inmsg & (tparams.TableSize - 1)) - tparams.GlobalStartMyProc;
                            HPCC_Table[(int) LocalOffset] ^= inmsg;
                            GlbNumUpdates++;
                            //                  System.err.println ("Representative thread #" + PCJ.myNode() + " is updating its array[" + LocalOffset + "] with " + Ran);                            
                        }
                    }
                }
            }
        }
        PCJ.put("GlbNumUpdates", GlbNumUpdates);
        PCJ.sync();
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
            //   System.err.println ("vran = " + Ran);
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
        if (updatesNumber != -1) {
            tparams.ProcNumUpdates = updatesNumber;
        }
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
        PCJ.sync();
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
            threadsPerNode = Integer.parseInt(reader.readLine());
            updatesNumber = Integer.parseInt(reader.readLine());
            reader.close();
        } catch (Throwable t) {
            //... do nothing
        }
        //  System.err.println("thread#" + PCJ.myNode() + " available CPUs = " + Runtime.getRuntime().availableProcessors());
        HPCC_MPIRandomAccess(tableBits, null);
    }

    public static void main(String[] args) {
        PCJ.start(MPIRandomAccessMultilevel.class, MPIRandomAccessMultilevel.class, "nodes.file");
    }
}
