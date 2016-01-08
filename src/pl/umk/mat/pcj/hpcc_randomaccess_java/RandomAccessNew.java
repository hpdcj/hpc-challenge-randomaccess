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

/**
 *
 * @author Łukasz Górski <lgorski@mat.umk.pl>
 */
class RandomAccessNewStorage extends Storage {

    @Shared
    private long[] table;

    @Shared
    private ArrayList<Long> receivedUpdates;

    @Shared
    private int okCells;
}

public class RandomAccessNew implements StartPoint {

    int logN;
    int globalN;
    int logLocalN;
    int localN;
    int logNumProcs;
    long localUpdates;
    RandomForRA random;
    public static final int BUFFERED_UPDATES = 1024;

    public static void main(String[] args) {
        String nodesFileName = "nodes.txt";
        if (args.length > 0) {
            nodesFileName = args[0];
        }
        PCJ.deploy(RandomAccessNew.class, RandomAccessNewStorage.class, nodesFileName);
    }

    public void main() throws Throwable {
        String[] rounds = {"Warmup", "After warmup"};
        for (int i = 0; i < 2; i++) {
            if (PCJ.myId() == 0) {
                PCJ.log(rounds[i] + " round");
            }
            initializeData();
            PCJ.barrier();
            double start = System.currentTimeMillis();
            performRandomAccess();
            double stop = System.currentTimeMillis();
            PCJ.log("Starting verification");
            verifyResultsLocally();
            thread0VerifyAll();
            if (PCJ.myId() == 0) {
                double seconds = (stop - start) * 1e-3;
                double gups = localUpdates*PCJ.threadCount() * 1e-6 / seconds;
                System.out.println("Time: " + seconds + " s, performance: " + gups);
            }
        }
    }

    private void initializeData() throws FileNotFoundException {
        Scanner in = new Scanner(new File("ra.config"));
        logN = in.nextInt();
        logNumProcs = (int) (Math.log(PCJ.threadCount()) / Math.log(2));
        logLocalN = logN - logNumProcs;
        localN = 1 << logLocalN;
        localUpdates = 4 * localN;
        long[] table = new long[localN];
        for (int i = 0; i < localN; i++) {
            table[i] = i + PCJ.myId() * localN;
        }
        PCJ.putLocal("table", table);
        random = new RandomForRA(PCJ.myId());
    }

    List<Long> send = new ArrayList<>();
    List<Long> keep = new ArrayList<>();

    private int randomNumberToLocalPosition(long rand) {
        return (int) (rand & (localN - 1));
    }

    private List<Long> localUpdatesAndGenerateRemoteUpdates(int update, int CHUNK_SIZE) {
        List<Long> numbers = new ArrayList<>();

        for (int k = 0; k < CHUNK_SIZE && update + k < localUpdates; k++) {
            long rand = random.nextLong();
            numbers.add(rand);
        }
        return numbers;
    }

    private void performRandomAccess() {
        for (int update = 0; update < localUpdates; update += BUFFERED_UPDATES) {
            List<Long> updateList = localUpdatesAndGenerateRemoteUpdates(update, BUFFERED_UPDATES);
            updateList = alltoallHypercube(updateList);
            performUpdates(updateList);
        }
    }

    private List<Long> alltoallHypercube(List<Long> updates) {
        //all-to-all hypercube personalized communication, per 
        //http://www.sandia.gov/~sjplimp/docs/cluster06.pdf, p. 5.
        List<Long> list = updates;
        for (int dimension = 0; dimension < logNumProcs; dimension++) {
            int partner = (1 << dimension) ^ PCJ.myId();
            PCJ.barrier(partner);
            long mask = 1L << (logLocalN + dimension);
            prepareUpdateLists(partner, list, mask);
            sendListToPartner(partner);
            PCJ.barrier(partner);
            PCJ.waitFor("receivedUpdates");
            receiveUpdates();

            list.clear();
            list.addAll(keep);
            keep.clear();

        }
        return list;
    }

    private void sendListToPartner(int partner) throws ClassCastException {
        PCJ.put(partner, "receivedUpdates", send);
        send.clear();
    }

    private void receiveUpdates() {
        List<Long> receivedUpdates = PCJ.getLocal("receivedUpdates");

        keep.addAll(receivedUpdates);

    }

    private void prepareUpdateLists(int partner, List<Long> updates1, long mask) {
        if (partner > PCJ.myId()) {
            for (long update : updates1) {
                if ((update & mask) != 0) {
                    send.add(update);
                } else {
                    keep.add(update);
                }
            }
        } else {
            for (long update : updates1) {
                if ((update & mask) != 0) {
                    keep.add(update);
                } else {
                    send.add(update);
                }
            }
        }
    }

    int whichPE(long pos) {
        return (int) (pos >> logLocalN) & (PCJ.threadCount() - 1);
    }

    private void performUpdates(List<Long> updateList) {
        for (long update : updateList) {
            updateSingleCell(update);
        }
    }

    private void updateSingleCell(long update) throws ClassCastException {
        int localOffset = randomNumberToLocalPosition(update);
        long val = PCJ.getLocal("table", localOffset);
        val ^= update;
        PCJ.putLocal("table", val, localOffset);
    }

    private void verifyResultsLocally() {
        PCJ.barrier();

        for (int PE = 0; PE < PCJ.threadCount(); PE++) {
            RandomForRA random = new RandomForRA(PE);

            for (int update = 0; update < localUpdates; update++) {
                long val = random.nextLong();
                if (whichPE(val) == PCJ.myId()) {

                    updateSingleCell(val);
                }
            }
        }

        long[] table = PCJ.getLocal("table");
        int ok = 0;
        for (int i = 0; i < table.length; i++) {
            if (table[i] == i + PCJ.myId() * localN) {
                ok++;
            } else {
                PCJ.log("Cell #" + i + " is " + table[i] + ", should be: " + (i + PCJ.myId() * localN));
            }
        }
        PCJ.putLocal("okCells", ok);
        PCJ.barrier();
    }

    private void thread0VerifyAll() {
        if (PCJ.myId() == 0) {
            for (int i = 0; i < PCJ.threadCount(); i++) {
                int remoteOk = PCJ.get(i, "okCells");
                if (remoteOk != localN) {
                    System.out.println("Verification failed for thread #" + i);
                }
            }
        }
    }

    private static class RandomForRA {

        public static final long POLY = 0x0000000000000007;
        public static final long PERIOD = 1317624576693539401L;
        private long rand;

        public RandomForRA(int initialVal) {
            long n = initialVal * 4;
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
