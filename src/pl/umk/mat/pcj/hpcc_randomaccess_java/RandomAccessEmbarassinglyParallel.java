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

public class RandomAccessEmbarassinglyParallel extends Storage implements StartPoint {

    public boolean performVerification = true;
    public boolean timeBound = false;
    /* Allocate main table (in global memory) */
    public static /* unsigned */ long[] HPCC_Table;


    /* The embarassingly parallel version of the algorithm.
     * 
     * The following function is a modification of MPIRandomAccess LG's pitiful algorithm, so
     * that it encompasses the embarassingly parallel nature of its (not-embarassingly) parallel
     * counterpart.
     * 
     */
    
    //efficiency metrics
    @Shared
    double localGUPs;
    
   @Shared
   long errors;
   
   long GlbNumUpdates;
   
    public void AnyNodesMPIRandomAccessUpdate(HPCC_RandomAccess_tabparams_s tparams) {
        long i;
        long[] ran = new long[128];
        int j;
        long start = System.currentTimeMillis();
        
        for (j = 0; j < 128; j++) {
            ran[j] = Utility.HPCC_starts((4 * tparams.TableSize / 128) * j);
        }
        
        for (i = 0; i < (4 * tparams.TableSize) / 128; i++) {
            //#pragma omp parallel for
            for (j = 0; j < 128; j++) {
                ran[j] = (ran[j] << 1) ^ ((/* signed */ long) ran[j] < 0 ? DefineConstants.POLY : 0);

                HPCC_Table[(int)(ran[j] & (tparams.TableSize - 1))] ^= ran[j];
                GlbNumUpdates++;
            }
            if (timeBound) {

                if (System.currentTimeMillis() - start > 60_000 ) {
                    break;
                }
            }            
        }
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

        long localProcNumUpdates; // for reduction

        long MPIRandomAccess_ExeUpdates;
        double MPIRandomAccess_TimeBound;
        
        double minGUPs, avgGUPs, maxGUPs;

        minGUPs = avgGUPs = maxGUPs = localGUPs = 0;


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
        tparams.LocalTableSize = (long)TotalMem;

        /* calculate TableSize --- the size of update array (must be a power of 2) */
        for (TotalMem *= 0.5, tparams.logTableSize = 0, tparams.TableSize = 1; TotalMem >= 1.0; TotalMem *= 0.5, tparams.logTableSize++, tparams.TableSize <<= 1) {
            ; // EMPTY
        }



        sAbort = 0;
        HPCC_Table = new /* unsigned */ long[(int) tparams.LocalTableSize];

        long MPIRandomAccess_N = (long) tparams.TableSize;

        //Tangible multiline preserve/* Default number of global updates to table: 4x number of table entries */
        NumUpdates_Default = 4 * tparams.TableSize;
        tparams.ProcNumUpdates = 4 * tparams.LocalTableSize;
        NumUpdates = NumUpdates_Default;


        tparams.ProcNumUpdates = /*Math.min(GlbNumUpdates, (*/ ((4 * tparams.LocalTableSize));
        /* works for both PowerofTwo and AnyNodes */
        NumUpdates = NumUpdates_Default;

        if (PCJ.myNode() == 0) {
            outFile.printf("Main table size   = 2^%d = %d words\n", tparams.logTableSize,tparams.TableSize);
            outFile.printf("Number of updates = %d\n", NumUpdates);
        }



        /* Initialize main table */
        for (i = 0; i < tparams.LocalTableSize; i++) {
            HPCC_Table[(int) i] = i;
        }

        PCJ.sync();


        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        boolean CPUTimeSupported = bean.isCurrentThreadCpuTimeSupported();
        CPUTime = 0;
        if (CPUTimeSupported) {
            CPUTime = -bean.getCurrentThreadCpuTime();
        }
        RealTime = -System.nanoTime();
        
       
        AnyNodesMPIRandomAccessUpdate(tparams);
        
        
        
        
        
        /* End timed section */
        if (CPUTimeSupported) {
            CPUTime += bean.getCurrentThreadCpuTime();
        }
        RealTime += System.nanoTime();

        /* convert time to seconds */
        CPUTime *= 1e-9;
        RealTime *= 1e-9;
        
        
        // calculate timings
        
        localGUPs = (RealTime > 0.0 ? 1.0 / RealTime : -1.0);
        localGUPs *= ( 1e-9 * (GlbNumUpdates));               
                
        /* Print timing results */
        if (tparams.MyProc == 0) {
            outFile.printf("CPU time used  = %.6f seconds\n", CPUTime);
            outFile.printf("Real time used = %.6f seconds\n", RealTime);

            outFile.printf("%.9f Billion(10^9) Updates    per second [GUP/s]\n", localGUPs );
            MPIRandomAccess_ExeUpdates = NumUpdates;
            MPIRandomAccess_TimeBound = timeBound;
        }




        /* Verification phase */
        /* Verification of results (in serial or "safe" mode; optional) */
        long temp = 0x1;
        for (i=0; i<GlbNumUpdates; i++) {
            temp = (temp << 1) ^ (((/* signed */ long) temp < 0) ? DefineConstants.POLY : 0);
            HPCC_Table[(int)(temp & (tparams.TableSize-1))] ^= temp;
        }

        temp = 0;
        for (i=0; i<tparams.TableSize; i++) {
          if (HPCC_Table[(int)i] != i) {
                temp++;
          }
        }

        errors = temp;
        PCJ.sync();
        
        int nodesWithErrors = errors > 0 ? 1 : 0;
        //count the GUPs metrics
        if (PCJ.myNode() == 0) {
            maxGUPs = minGUPs = avgGUPs = localGUPs;
            
            for (int k = 1; k < PCJ.numNodes(); k++) {
                double curr = PCJ.get(k, "localGUPs");
                maxGUPs = Math.max(maxGUPs, curr);
                minGUPs = Math.min(minGUPs, curr);
                avgGUPs += curr;
                nodesWithErrors  += ((long)PCJ.get (k, "errors") > 0) ? 1 : 0;
            }
            
            avgGUPs /= PCJ.numNodes();
            outFile.printf("Node(s) with error %d\n", nodesWithErrors );
            outFile.printf("Minimum GUP/s %.6f\n", minGUPs );
            outFile.printf("Average GUP/s %.6f\n", avgGUPs );
            outFile.printf("Maximum GUP/s %.6f\n", maxGUPs );

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
        HPCC_MPIRandomAccess(tableBits, null);
    }

    public static void main(String[] args) {
        PCJ.start(RandomAccessEmbarassinglyParallel.class, RandomAccessEmbarassinglyParallel.class, "nodes.file");
    }
}
