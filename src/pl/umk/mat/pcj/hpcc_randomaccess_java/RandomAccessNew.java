/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.umk.mat.pcj.hpcc_randomaccess_java;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import org.pcj.PCJ;
import org.pcj.Shared;
import org.pcj.StartPoint;
import org.pcj.Storage;
import static pl.umk.mat.pcj.hpcc_randomaccess_java.MPIRandomAccessHypercube2.HPCC_Table;

/**
 *
 * @author Łukasz Górski <lgorski@mat.umk.pl>
 */
class RandomAccessNewStorage extends Storage {

    @Shared
    int[] table;
}

public class RandomAccessNew implements StartPoint {

    int logN;
    int globalN;
    int logLocalN;
    int localN;
    long updates;
    RandomForRA random = new RandomForRA();
    public static final int BUFFERED_UPDATES = 1024;

    public static void main(String[] args) {
        String nodesFileName = "nodes.txt";
        if (args.length > 0) {
            nodesFileName = args[0];
        }
        PCJ.deploy(RandomAccessNew.class, RandomAccessNewStorage.class, nodesFileName);
    }

    public void main() throws Throwable {
        initializeData();
        PCJ.barrier();
        double start = System.currentTimeMillis();
        performRandomAccess();
        double stop = System.currentTimeMillis();
    }

    private void initializeData() throws FileNotFoundException {
        Scanner in = new Scanner(new File("ra.config"));
        logN = in.nextInt();
        logLocalN = logN - PCJ.threadCount();
        localN = 1 << logLocalN;
        updates = 4 * localN;
        long[] table = new long[localN];
        for (int i = 0; i < localN; i++) {
            table[i] = i + PCJ.myId() * logLocalN;
        }
        PCJ.putLocal("table", table);
    }

    List<Long> send = new ArrayList<>();
    List<Long> keep = new ArrayList<>();

    private int randomNumberToLocalPosition (long rand) {
        return (int) (rand & (localN - 1));
    }
    private List<Long> localUpdatesAndGenerateRemoteUpdates(int update) {
        List<Long> numbers = new ArrayList<>();

        for (int k = 0; k < BUFFERED_UPDATES && update + k < updates; k++) {
            long rand = random.nextLong();
            int whichPe = (int) ((rand) >> logLocalN) & (PCJ.threadCount() - 1);

            if (whichPe == PCJ.myId()) {
                int localOffset = randomNumberToLocalPosition(rand);
                HPCC_Table[(int) localOffset] ^= rand;
            } else {
                numbers.add(rand);
            }
        }
        return numbers;
    }

    private void performRandomAccess() {
        for (int update = 0; update < updates; update++) {
            List<Long> updates = localUpdatesAndGenerateRemoteUpdates(update);
            alltoallHypercube(updates);
        }
    }

    private void alltoallHypercube(List<Long> updates) {
            //all-to-all hypercube personalized communication, per 
            //http://www.sandia.gov/~sjplimp/docs/cluster06.pdf, p. 5.
            for (int dimension = 0; dimension < Math.log(PCJ.threadCount()) / Math.log(2); dimension++) {
                send.clear();
                keep.clear();
                int partner = (1 << dimension) ^ PCJ.myId();

                long mask = ((1L << dimension) << logLocalN);

                if (partner > PCJ.myId()) {
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
//                String out = "";
//                out += send.size() + " ";
//                Iterator<Long> irr = send.iterator();
//                while (irr.hasNext()) {
//                    out += irr.next() + " ";
//                }
                // System.err.println (out);
                Iterator<Long> iter = send.iterator();
                int z = 1;
                while (iter.hasNext()) {
                    buff[z++] = iter.next();
                }
                send.clear();
                if (timeBound && bailed[tparams.MyProc] == true) {
                    buff[0] += 100_000;
                }
//                String sss = "Thr " + tparams.MyProc + " is putting to thread " + partner + " following data: { ";
//                for (int g = 0; g <= buff[0]; g++) {
//                    sss += buff[g] + " ";
//                }
//                System.err.println (sss);

                if (timeBound == false || bailed[partner] == false) {
                    PCJ.put(partner, "thr" + tparams.MyProc, buff);
                    //               System.err.println (tparams.MyProc + " waiting for " + partner);
                    if (timeBound && bailed[tparams.MyProc] == true) {
                        bailed[partner] = true;
                    }
                    PCJ.waitFor("thr" + partner);
                    //                System.err.println ("finished wait");
                    long[] recv = PCJ.get("thr" + partner);
                    if (timeBound) {
                        if (recv[0] >= 100_000) {
                            bailed[partner] = true;
                            bailed[tparams.MyProc] = true;
                            recv[0] -= 100_000;
                        }
                    }
                    for (int zz = 1; zz <= recv[0]; zz++) {
                        //  System.err.println (tparams.MyProc + " " + zz + " " + recv[0]);
                        keep.add(recv[zz]);
                    }
                }

                list.clear();
                Iterator<Long> iter2 = keep.iterator();
                while (iter2.hasNext()) {
                    list.add(iter2.next());
                }

            }
    }

    private static class RandomForRA {

        public static final long POLY = 0x0000000000000007;
        public static final long PERIOD = 1317624576693539401L;
        private long rand;

        public RandomForRA() {
            int n = PCJ.myId() * 4;
            int i, j;
            /* unsigned */ long[] m2 = new /* unsigned */ long[64];
            /* unsigned */ long temp, ran;

            while (n < 0) {
                n += PERIOD;
            }
            while (n > PERIOD) {
                n -= PERIOD;
            }
            if (n == 0) {
                rand = 0x1;
            }

            temp = 0x1;
            for (i = 0; i < 64; i++) {
                m2[i] = temp;
                temp = (temp << 1) ^ ((long) temp < 0 ? POLY : 0);
                temp = (temp << 1) ^ ((long) temp < 0 ? POLY : 0);
            }

            for (i = 62; i >= 0; i--) {
                if (((n >> i) & 1) == 1) {
                    break;
                }
            }

            ran = 0x2;
            while (i > 0) {
                temp = 0;
                for (j = 0; j < 64; j++) {
                    if (((ran >> j) & 1) == 1) {
                        temp ^= m2[j];
                    }
                }
                ran = temp;
                i -= 1;
                if (((n >> i) & 1) == 1) {
                    ran = (ran << 1) ^ ((long) ran < 0 ? POLY : 0);
                }
            }

            rand = ran;
        }

        public long nextLong() {
            return rand = (rand << 1) ^ ((long) rand < 0L ? POLY : 0L);
        }
    }
}
