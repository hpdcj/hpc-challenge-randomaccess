package pl.umk.mat.pcj.hpcc_stream_java;



import pl.umk.mat.pcj.PCJ;
import pl.umk.mat.pcj.Shared;
import pl.umk.mat.pcj.StartPoint;
import pl.umk.mat.pcj.Storage;

/**
 *
 * @author Piotr
 */
public class Pcj_hpc_1 extends Storage implements StartPoint {

    int N = 40000000;
    //int N = 999936;
    //int N = 10;
    int Nall = N;
    int NTIMES = 10;
    int OFFSET = 0;
    double avgtime[] = new double[4];
    double maxtime[] = new double[4];
    double mintime[] = new double[4];
    double bytes[] = new double[4];
    int numVectors = 3;
    int elemType = 8;
//The scalar multiplier, 
    double scalar = 3.0d;
    double t;
    double epsilon = 1e-8;
    double times[][] = new double[4][NTIMES];
    String label[] = new String[4];

    private void test_hpc() {

        for (int i = 0; i < 4; i++) {
            avgtime[i] = 0.0d;
            maxtime[i] = 0.0d;
            mintime[i] = 1e38;
        }
        bytes[0] = 2 * Nall * elemType;
        bytes[1] = 2 * Nall * elemType;
        bytes[2] = 3 * Nall * elemType;
        bytes[3] = 3 * Nall * elemType;

        label[0] = "Copy:               ";
        label[1] = "Scale:              ";
        label[2] = "Add:                ";
        label[3] = "Triad:              ";

        double BytesPerWord = elemType;

        if (PCJ.myNode() == 0) {
            System.out.println("-------------------------------------------------------------");
            System.out.println("This system uses " + BytesPerWord + " bytes per DOUBLE PRECISION word.");
            System.out.println("-------------------------------------------------------------");
            System.out.println("Array size " + Nall + " Offset " + OFFSET);
            System.out.println("Total memory required = " + (3.0 * BytesPerWord * ((double) N / 1048576.0)) + "MB");
            System.out.println("Each test is run " + NTIMES + " times, but only he *best* time for each is used.");
            System.out.println("-------------------------------------------------------------");
        }

        int k = PCJ.numNodes();
        if (PCJ.myNode() == 0) {
            System.out.println("Number of Threads requested = " + k);
        }

        // Total size of data
        // Actual size allocated to node is N

        N = Nall / k;
        if ((N * k) < Nall) {
            N = N + 1;
        }
        Nall = N * k;

        // Last node is dooing more, its work can be reduced ....

        double a[] = new double[N + OFFSET];
        double b[] = new double[N + OFFSET];
        double c[] = new double[N + OFFSET];

        // To tzreba zrownloeglic
        for (int j = 0; j < N; j++) {
            a[j] = 1.0d;
            b[j] = 2.0d;
            c[j] = 0.0d;
        }
        if (PCJ.myNode() == 0) {
            System.out.println("-------------------------------------------------------------");
        }

        long quantum = checktick();
        if (quantum >= 1) {
            System.out.println("Your clock granularity/precision appears to be "
                    + quantum + " microseconds.");
        } else {
            System.out.println("Your clock granularity appears to be less than one microsecond");
        }

        t = seconds();
        for (int j = 0; j < N; j++) {
            a[j] = 2.0E0 * a[j];
        }
        t = 1.0E6 * (seconds() - t);

        if (PCJ.myNode() == 0) {
            System.out.println("Each test below will take on the order of " + (int) t + " microseconds.");
            System.out.println("   (=  " + (int) (t / quantum) + " clock ticks)");
            System.out.println("Increase the size of the arrays if this shows that");
            System.out.println("you are not getting at least 20 clock ticks per test.");

            System.out.println("-------------------------------------------------------------");

            System.out.println("WARNING -- The above is only a rough guideline.");
            System.out.println("For best results, please be sure you know the");
            System.out.println("precision of your system timer.");
            System.out.println("-------------------------------------------------------------");
        }

        k = PCJ.numNodes();
        if (PCJ.myNode() == 0) {
            System.out.println("Number of Threads requested = " + k);
        }

        PCJ.sync();
        /*	--- MAIN LOOP --- repeat test cases NTIMES times --- */

        for (k = 0; k < NTIMES; k++) {
            times[0][k] = seconds();
            for (int j = 0; j < N; j++) {
                c[j] = a[j];
            }
            PCJ.sync();
            times[0][k] = seconds() - times[0][k];
            times[1][k] = seconds();
//#pragma omp parallel for
            for (int j = 0; j < N; j++) {
                b[j] = scalar * c[j];
            }
            PCJ.sync();
            times[1][k] = seconds() - times[1][k];
            times[2][k] = seconds();
//#pragma omp parallel for
            for (int j = 0; j < N; j++) {
                c[j] = a[j] + b[j];
            }
            PCJ.sync();
            times[2][k] = seconds() - times[2][k];
            times[3][k] = seconds();
//#pragma omp parallel for
            for (int j = 0; j < N; j++) {
                a[j] = b[j] + scalar * c[j];
            }
            PCJ.sync();
            times[3][k] = seconds() - times[3][k];
        }
        /*	--- SUMMARY --- */

        for (k = 1; k < NTIMES; k++) /* note -- skip first iteration */ {
            for (int j = 0; j < 4; j++) {
                avgtime[j] = avgtime[j] + times[j][k];
                mintime[j] = Math.min(mintime[j], times[j][k]);
                maxtime[j] = Math.max(maxtime[j], times[j][k]);
            }
        }

        if (PCJ.myNode() == 0) {
            System.out.println("-------------------------------------------------------------");
            System.out.println("Function      Rate (MB/s)   Avg time     Min time     Max time\n");
        }

        for (int j = 0; j < 4; j++) {
            avgtime[j] = avgtime[j] / (double) (NTIMES - 1);

            if (PCJ.myNode() == 0) {

                System.out.println(label[j] + " "
                        + 1.0E-06 * bytes[j] / mintime[j] + " "
                        + avgtime[j] + " "
                        + mintime[j] + " "
                        + maxtime[j]);

                //   
            }
        }

        if (PCJ.myNode() == 0) {
            System.out.println("-------------------------------------------------------------");
        }


        /* --- Check Results --- */
        checkSTREAMresults(a, b, c, N, Nall);

        if (PCJ.myNode() == 0) {
            System.out.println("-------------------------------------------------------------");
        }

    }
    
    @Shared
    double asum;
    @Shared
    double bsum;
    @Shared
    double csum;

    private void checkSTREAMresults(double[] a, double[] b, double[] c, int N, int Nall) {
        int j, k;
        double aj, bj, cj;

        //System.out.println("P" + PCJ.myNode() + " N, Nall " + N + " " + Nall);

        asum = 0.0;
        bsum = 0.0;
        csum = 0.0;

        /* reproduce initialization */
        aj = 1.0;
        bj = 2.0;
        cj = 0.0;
        /* a[] is modified during timing check */
        aj = 2.0E0 * aj;
        /* now execute timing loop */
        scalar = 3.0;
        for (k = 0; k < NTIMES; k++) {
            cj = aj;
            bj = scalar * cj;
            cj = aj + bj;
            aj = bj + scalar * cj;
        }
        aj = aj * (double) (Nall);
        bj = bj * (double) (Nall);
        cj = cj * (double) (Nall);

        asum = 0.0;
        bsum = 0.0;
        csum = 0.0;

        for (j = 0; j < N; j++) {
            asum += a[j];
            bsum += b[j];
            csum += c[j];
        }
        PCJ.sync();
        // System.out.println("P" + PCJ.myNode() + " asum " + asum);
        //System.out.println("P" + PCJ.myNode() + " bsum " + bsum);
        //System.out.println("P" + PCJ.myNode() + " csum " + csum);

        double asum0 = 0.0;
        double bsum0 = 0.0;
        double csum0 = 0.0;
        if (PCJ.myNode() == 0) {
            for (int p = 1; p < PCJ.numNodes(); p++) {
                asum = asum + (double) PCJ.get(p, "asum");
                bsum = bsum + (double) PCJ.get(p, "bsum");
                csum = csum + (double) PCJ.get(p, "csum");
            }
        }
        PCJ.sync();

        if (PCJ.myNode() == 0) {
            System.out.println("Results Comparison: ");
            System.out.println("        Expected  : " + aj + " " + bj + " " + cj);
            System.out.println("        Observed  : " + asum + " " + bsum + " " + csum);

            if (Math.abs(aj - asum) / asum > epsilon) {
                System.out.println("Failed Validation on array a[]");
                System.out.println("        Expected   : " + aj);
                System.out.println("        Observed  : " + asum);
            }
            if (Math.abs(bj - bsum) / bsum > epsilon) {
                System.out.println("Failed Validation on array b[]");
                System.out.println("        Expected   : " + bj);
                System.out.println("        Observed  : " + bsum);
            }
            if (Math.abs(cj - csum) / csum > epsilon) {
                System.out.println("Failed Validation on array c[]");
                System.out.println("        Expected   : " + cj);
                System.out.println("        Observed  : " + csum);
            }
            if ((Math.abs(aj - asum) / asum <= epsilon)
                    & (Math.abs(aj - asum) / asum <= epsilon)
                    & (Math.abs(aj - asum) / asum <= epsilon)) {
                System.out.println("Solution Validates");
            }
        }
    }

    private void add(double[] a, double[] b, double[] c, int n, double scalar) {
        for (int i = 0; i < n; i++) {
            a[i] = b[i] + c[i];
        }
    }

    private void triad(double[] a, double[] b, double[] c, int n, double scalar) {
        for (int i = 0; i < n; i++) {
            a[i] = b[i] + scalar * c[i];
        }
    }

    private long checktick() {
        int M = 20;
        int i;
        int minDelta, Delta;
        double t1, t2;
        double timesfound[] = new double[M];

        /*  Collect a sequence of M unique time values from the system. */
        for (i = 0; i < M; i++) {
            t1 = seconds();
            while (((t2 = seconds()) - t1) < 1E-6) {
                ;
            }
            timesfound[i] = t1 = t2;
        }

        /*
         * Determine the minimum difference between these M values.
         * This result will be our estimate (in microseconds) for the
         * clock granularity.
         */
        minDelta = 1000000;
        for (i = 1; i < M; i++) {
            Delta = (int) (1.0E6 * (timesfound[i] - timesfound[i - 1]));
            minDelta = Math.min(minDelta, Math.max(Delta, 0));
        }
        return minDelta;
    }

    private double seconds() {
        //    return (double) (System.currentTimeMillis()* 1.0E-6);
        return (double) (System.nanoTime() * 1.0E-9);

    }

    //
// Print out success/failure, the timings, and the GB/s value
//
    @Override
    public void main() {
        PCJ.sync();
        test_hpc();
        PCJ.sync();
    }

    public static void main(String[] args) throws Throwable {

        PCJ.start(Pcj_hpc_1.class, Pcj_hpc_1.class, "nodes.file");
    }
}
