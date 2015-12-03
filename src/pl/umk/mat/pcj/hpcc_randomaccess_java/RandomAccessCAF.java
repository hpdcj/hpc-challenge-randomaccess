package pl.umk.mat.pcj.hpcc_randomaccess_java;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import pl.umk.mat.pcj.FutureResponse;
import pl.umk.mat.pcj.PCJ;
import pl.umk.mat.pcj.Shared;
import pl.umk.mat.pcj.StartPoint;
import pl.umk.mat.pcj.Storage;

/**
 * Wzororwane na
 * http://svn.rice.edu/r/caf/caf-compiler/benchmarks/randomaccess-hpcc/randomaccess.caf
 * i okolicach
 * 
* http://www.hpcchallenge.org/presentations/sc2011/hpcc11_report_caf2_0.pdf
 */
public class RandomAccessCAF extends Storage implements StartPoint {

    @Shared
    long[] table;
    int world_rank, world_size, world_logsize;
    ArrayList<Long>[] retain;
    //long[][] retain; // [1..buffer_size][0..1] -> my odwracamy na [0..1][1..buffer_size]
    @Shared
    ArrayList<Long>[] fwd;
    //teraz fwd ma strukturę [2 * world_logsize], zaś obszar z buforem -> ArrayList
    //s long[][][] fwd; //[0..buffer_size][0..1][0..world_logsize - 1] -> my odwracamy
    // na [0..1][0..world_logsize - 1][0..buffer_size]
    int buffer_size, nelements_per_bunch;
    int last, current;
    @Shared
    int received0, received1, received2, received3, received4, received5, received6, received7, received8,
            received9, received10, received11, received12, received13, received14, received15, received16,
            received17, received18, received19, received20, received21, received22, received23, received24,
            received25, received26, received27, received28, received29, received30, received31;
    @Shared
    int delivered0, delivered1, delivered2, delivered3, delivered4, delivered5, delivered6, delivered7,
            delivered8, delivered9, delivered10, delivered11, delivered12, delivered13, delivered14,
            delivered15, delivered16, delivered17, delivered18, delivered19, delivered20, delivered21,
            delivered22, delivered23, delivered24, delivered25, delivered26, delivered27, delivered28,
            delivered29, delivered30, delivered31;

    private int number_of_bits(int i) {
        int num = 0;
        int itmp = i;
        while (itmp > 0) {
            num++;
            itmp >>= 1;
        }
        return num;
    }

    private void team_init() {
        world_rank = PCJ.myNode();
        world_size = PCJ.numNodes();
        world_logsize = number_of_bits(world_size - 1);
    }
    long local_table_size;
    long local_table_logsize;

    private void table_init(int local_n) {
        long start_index, i;

        local_table_size = 1L << local_n;
        local_table_logsize = local_n;

        table = new long[(int) local_table_size]; // :( Java does not allow to index arrays
        // with long
        start_index = world_rank * local_table_size;
        for (i = 0; i < local_table_size; i++) {
            table[(int) i] = start_index + i;
        }
    }

    private void route_init(int n_elements_per_bunch_) {
        nelements_per_bunch = n_elements_per_bunch_;
        buffer_size = Math.max(2000000, 2 * nelements_per_bunch);
        retain = new ArrayList[2];
        for (int i = 0; i < 2; i++) {
            retain[i] = new ArrayList(buffer_size);
            for (int j = 0; j < buffer_size; j++) {
                retain[i].add(0L);
            }
        }
        fwd = new ArrayList[2 * world_logsize];
        for (int i = 0; i < fwd.length; i++) {
            fwd[i] = new ArrayList(buffer_size);
            for (int j = 0; j < buffer_size; j++) {
                fwd[i].add(0L);
            }
        }

        for (int i = 0; i < world_logsize; i++) {
            PCJ.put("received" + i, 0);
            PCJ.put("delivered" + i, 0);
        }
    }

    private void print_problem_stats(long nupdates_local) {
        if (world_rank == 0) {
            System.out.println("randomaccess benchmark");
            System.out.println("global table    =" + local_table_size * 8 * world_size
                    + " bytes, " + local_table_size * world_size + " elements");
            System.out.println("local table     =" + local_table_size * 8
                    + " bytes, " + local_table_size + " elements");
            System.out.println("global updates  =" + nupdates_local * world_size);
            System.out.println("local updates   =" + nupdates_local);
        }
    }

    private void print_gups(long start_time, long end_time, long rate, long updates, String style) {
        final double billion = 1e9;
        long ticks;
        double GUPS;
        ticks = end_time - start_time;
        if (world_rank == 0) {
            GUPS = (updates * 1.0 * world_size * rate) / (ticks * billion);
            System.out.println("clock rate      =" + rate + " ticks per second");
            System.out.println("ticks           =" + ticks);
            System.out.println("elapsed time    =" + ticks / (1.0 * rate) + " seconds");
            System.out.printf(": GUPS   =%f\n", GUPS);
        }
    }
    long POLY, PERIOD, ran;

    long random_seq_get_next(long val) {
        return (val << 1) ^ ((-(val >> 63)) & POLY);
    }

    @SuppressWarnings("empty-statement")
    void random_seq_set_position(long nth) {
        long n, temp;
        long[] m2 = new long[64];
        int i, j;

        POLY = 7;
        PERIOD = 1317624576693539401L;

        for (n = nth; n < 0; n += PERIOD) {
        }
        for (; n > PERIOD; n -= PERIOD) {
        }

        if (n == 0) {
            ran = 1;
        } else {
            temp = 1;
            for (i = 0; i < 64; i++) {
                m2[i] = temp;
                temp = random_seq_get_next(temp);
                temp = random_seq_get_next(temp);
            }

            for (i = 62; i >= 0; i--) {
                if ((n & (1L << i)) != 0) {
                    return;
                }
            }

            ran = 2;
            while (i > 0) {
                temp = 0;
                for (j = 0; j < 64; j++) {
                    if ((ran & (1L << j)) != 0) {
                        temp = temp ^ m2[j];
                    }
                }
                ran = temp;
                i--;
                if ((n & (1L << i)) != 0) {
                    ran = random_seq_get_next(ran);
                }
            }
        }
    }

    private void random_seq_get_bunch(ArrayList<Long> rand, int bunch) {
        for (int i = 0; i < bunch; i++) {
            rand.set(i, ran);
            ran = random_seq_get_next(ran);
        }
    }

        private double seconds() {
        //    return (double) (System.currentTimeMillis()* 1.0E-6);
        return (double) (System.nanoTime() * 1.0E-9);

    }
private int checktick()
    {
    int         i, minDelta, Delta;
    double      t1, t2;
    final int M = 20;
    double[] timesfound = new double[M];

/*  Collect a sequence of M unique time values from the system. */

    for (i = 0; i < M; i++) {
        t1 = seconds();
        while( ((t2=seconds()) - t1) < 1.0E-6 )
            ;
        timesfound[i] = t1 = t2;
        }

/*
 * Determine the minimum difference between these M values.
 * This result will be our estimate (in microseconds) for the
 * clock granularity.
 */

    minDelta = 1000000;
    for (i = 1; i < M; i++) {
        Delta = (int)( 1.0E6 * (timesfound[i]-timesfound[i-1]));
        minDelta = Math.min(minDelta, Math.max(Delta,0));
        }

   return(minDelta);
    }
    
    private void perform_updates(long nupdates_local, int bunch_size, String style) {
        long start_time, end_time, rate = (long)1e9;
        long start_pos, round;

        dropped_updates = 0;

        start_pos = world_rank * nupdates_local;
        random_seq_set_position(start_pos);

        PCJ.sync();

        start_time = System.nanoTime();

        for (round = 1; round <= nupdates_local - bunch_size + 1; round += bunch_size) {
            random_seq_get_bunch(retain[0], bunch_size);
            route();
        }

        PCJ.sync();

        end_time = System.nanoTime();
        print_gups(start_time, end_time, rate, nupdates_local, style);

    }
    long dropped_updates;

    private void split(ArrayList<Long> in,
            ArrayList<Long> keep,
            ArrayList<Long> fwd,
            int buffersize, int distance) {
        int partner_rank, i, target_rank;

        partner_rank = world_rank + distance;

        for (i = 0; i < in.size(); i++) {
            target_rank = (int) (in.get(i) >> local_table_logsize);
            target_rank &= (world_size - 1);

            if (target_rank < world_rank) {
                target_rank += world_size;
            }
            if (target_rank - world_rank < distance) {
                if (keep.size() < buffersize) {
                    keep.add(in.get(i));
                } else {
                    dropped_updates++;
                }
            } else {
                if (fwd.size() < buffersize) {
                    fwd.add(in.get(i));
                } else {
                    dropped_updates++;
                }
            }
        }
    }

    private int advance(int n) {
        return 1 - n;
    }

    private void route() {
        final int out = 0, in = 1;
        int distance, partner = 0, i, from = 42; //to quiet the compiler
        int outgoing_size, itemp, n, iii, iik, next_slot;

        last = 0;
        current = 1;

        for (i = world_logsize - 1; i >= 0; i--) {
            distance = 1 << i;
            partner = (world_rank + distance + world_size) % world_size;

            split(retain[last],
                    retain[current],
                    fwd[out * world_logsize + i],
                    buffer_size, distance);

            if (i < world_logsize - 1) {
                PCJ.waitFor("delivered" + (i + 1));
                PCJ.monitor("delivered" + (i + 1));
                split(fwd[in * world_logsize + i + 1],
                        retain[current],
                        fwd[out * world_logsize + i],
                        buffer_size, distance);
                PCJ.put(from, "received" + (i + 1), 0);
            }

           PCJ.waitFor("received" + i);
            PCJ.monitor("received" + i);
            from = (world_rank - distance + world_size) % world_size;
            outgoing_size = fwd[0 * world_logsize + out].get(i).intValue();

            PCJ.put("fwd", fwd[out * world_logsize + i], in * world_logsize + i);
            PCJ.put(partner, "delivered" + i, 0);

            last = advance(last);
            current = advance(current);
        }

        apply_updates(retain[last]);

        PCJ.waitFor("delivered0");
        PCJ.monitor("delivered0");
        apply_updates(fwd[in * world_logsize + 0]);
        from = (world_rank - 1 + world_size) % world_size;
        PCJ.put(from, "received0", 0);

    }

    private void apply_updates(ArrayList<Long> buffer) {

        for (int i = 0; i < buffer.size(); i++) {
            int index = (int) (buffer.get(i) & (local_table_size - 1));
            table[index] ^= buffer.get(i);
        }
    }

    @Override
    public void main() throws Throwable {
        PCJ.sync();
        try {
            BufferedReader reader = new BufferedReader(new FileReader("table.file"));
            local_table_bits = Integer.parseInt(reader.readLine());
            performVerification = Boolean.parseBoolean(reader.readLine());
            timeBound = Boolean.parseBoolean(reader.readLine());
            reader.close();
        } catch (Throwable t) {
            //... do nothing
        }
        
        int bunch_size = 1024;
        long nupdates_local = 0, error_count = 0, error_bound = 0;

        team_init();

        table_init(local_table_bits);
        route_init(bunch_size);

        nupdates_local = 1L << (local_table_bits + 2);

        print_problem_stats(nupdates_local);

        perform_updates(nupdates_local, bunch_size, "routing");

//        perform_updates(nupdates_local, bunch_size, "routing verification");


        count_update_errors(dropped_updates);

        if (world_rank == 0) {
            System.out.println("errors founds       =" + error_count);
            System.out.println("error upper bound   =" + error_bound);
        }
    }
    @Shared
    long error_count, error_bound;

    private void count_update_errors(long dropped_updates) {
        long[] errors = new long[]{0, 0};
        long start_index = world_rank * local_table_size;
        for (int i = 0; i < local_table_size; i++) {
            if (table[i] != start_index + i) {
                error_count++;
                error_bound++;
            }
            error_bound += dropped_updates;
        }

        PCJ.sync();

        if (PCJ.myNode() == 0) {
            for (int i = 1; i < PCJ.numNodes(); i++) {
                long ec = PCJ.get(i, "error_count");
                long eb = PCJ.get(i, "error_bound");

                error_count += ec;
                error_bound += eb;
            }
        }
    }
    int local_table_bits;
    boolean performVerification = false, timeBound = false;


    public static void main(String[] args) {
        PCJ.deploy(RandomAccessCAF.class, RandomAccessCAF.class, "nodes.file");
    }
}
