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
    private ArrayList<Long> receivedUpdates0;

    @Shared
    private ArrayList<Long> receivedUpdates1;

    @Shared
    private int okCells;

    @Shared
    private int executed;

}

public class RandomAccessNew implements StartPoint {

    public static final int BUFFERED_UPDATES = 1024;
    public static final long POISON_PILL = -1;

    public static void main(String[] args) {
        String nodesFileName = "nodes.txt";
        if (args.length > 0) {
            nodesFileName = args[0];
        }
        PCJ.start(RandomAccessNew.class, RandomAccessNewStorage.class, nodesFileName);
    }

    int logN;
    int globalN;
    int logLocalN;
    int localN;
    int logNumProcs;
    long localUpdates;
    int threadCount;
    int myId;

    RandomForRA random;

    long timeBoundSeconds = Long.MAX_VALUE;
    boolean shutDown = false;
    int preparedLocally = 0;

    public void main() throws Throwable {
        String[] rounds = {"Warmup", "After warmup"};
        for (String round : rounds) {

            PCJ.log(round + " round");

            initializeData();
            PCJ.barrier();
            double start = System.currentTimeMillis();
            performRandomAccess();
            double stop = System.currentTimeMillis();

            PCJ.putLocal("executed", this.preparedLocally);
            PCJ.waitFor("executed");
            PCJ.log("Starting verification");
            verifyResultsLocally();
            thread0VerifyAll();
            if (myId == 0) {
                double seconds = (stop - start) * 1e-3;
                int updatesPerformedGlobally = 0;
                for (int PE = 0; PE < PCJ.threadCount(); PE++) {
                    updatesPerformedGlobally += (int) PCJ.get(PE, "executed");
                }
                double gups = updatesPerformedGlobally * 1e-9 / seconds;
                System.out.println("Time: " + seconds + " s, global size = " + this.globalN + " updates = " + updatesPerformedGlobally + ", performance: " + gups);
            }
            PCJ.barrier();

        }
    }

    private void initializeData() throws FileNotFoundException {

        try (Scanner in = new Scanner(new File("ra.config"))) {
            logN = in.nextInt();
            if (in.hasNextLong()) {
                this.timeBoundSeconds = in.nextLong();
            }
        }
        threadCount = PCJ.threadCount();
        myId = PCJ.myId();
        logNumProcs = (int) (Math.log(threadCount) / Math.log(2));
        logLocalN = logN - logNumProcs;
        localN = 1 << logLocalN;
        globalN = localN * threadCount;
        localUpdates = 4 * localN;
        long[] table = new long[localN];
        for (int i = 0; i < localN; i++) {
            table[i] = i + myId * localN;
        }

        PCJ.monitor("receivedUpdates0");
        PCJ.monitor("receivedUpdates1");

        PCJ.putLocal("table", table);
        PCJ.putLocal("executed", 0);
        PCJ.putLocal("okCells", 0);
        PCJ.putLocal("receivedUpdates0", new ArrayList<Long>());
        PCJ.putLocal("receivedUpdates1", new ArrayList<Long>());

        PCJ.waitFor("receivedUpdates0");
        PCJ.waitFor("receivedUpdates1");

        PCJ.monitor("receivedUpdates0");
        PCJ.monitor("receivedUpdates1");
        PCJ.monitor("executed");
        PCJ.monitor("okCells");
        PCJ.monitor("table");
        random = new RandomForRA(myId);
        this.preparedLocally = 0;
        this.shutDown = false;

    }

    private int randomNumberToLocalPosition(long rand) {
        return (int) (rand & (localN - 1));
    }

    private List<Long> generateRemoteUpdates(int update, int CHUNK_SIZE) {
        List<Long> numbers = new ArrayList<>();

        if (isTimeBound() && shutDown) {
            return numbers;
        }

        for (int k = 0; k < CHUNK_SIZE && update + k < localUpdates; k++) {
            long rand = random.nextLong();
            numbers.add(rand);
        }

        preparedLocally += numbers.size();
        return numbers;
    }

    boolean isTimeBound() {
        return timeBoundSeconds != Long.MAX_VALUE;
    }

    private void performRandomAccess() {
        long timeBoundStart = System.currentTimeMillis();
        for (int update = 0; update < localUpdates; update += BUFFERED_UPDATES) {
            List<Long> updateList = generateRemoteUpdates(update, BUFFERED_UPDATES);

            if (isTimeBound()) {
                if (PCJ.myId() == 0) {
                    shutDown = System.currentTimeMillis() - timeBoundStart > timeBoundSeconds * 1e3;
                }
            }

            updateList = alltoallHypercube(updateList);
            performUpdates(updateList);

            if (isTimeBound()) {
                if (shutDown) {
                    PCJ.log("Shutting down");
                    break;
                }
            }
        }
    }

    private List<Long> alltoallHypercube(List<Long> updates) {
        //all-to-all hypercube personalized communication, per 
        //http://www.sandia.gov/~sjplimp/docs/cluster06.pdf, p. 5.
        List<Long> send = new ArrayList<>();
        List<Long> keep = new ArrayList<>();

        for (int dimension = 0; dimension < logNumProcs; dimension++) {
            
            int partner = (1 << dimension) ^ myId;
            PCJ.barrier(partner); //try removing this or changing to barrier(partner)
            
            long mask = 1L << (logLocalN + dimension);
            List<Long> received = receiveUpdates(dimension);
            updates.addAll(received);
            prepareUpdateLists(partner, updates, keep, send, mask);
            sendListToPartner(send, partner, dimension);
            updates = keep;
            keep = new ArrayList<>();
        }
        List<Long> received = receiveUpdates(logNumProcs);
        updates.addAll(received);
        return updates;
    }

    private void sendListToPartner(List<Long> send, int partner, int dimension) throws ClassCastException {
        PCJ.put(partner, "receivedUpdates" + (dimension % 2), send);
        send.clear();
    }

    private List<Long> receiveUpdates(int dimension) {
        if (dimension > 0) {
            int previousDimension = dimension - 1;
            PCJ.waitFor("receivedUpdates" + (previousDimension % 2));
            return PCJ.getLocal("receivedUpdates" + (previousDimension % 2));
        }
        return new ArrayList<>();
    }

    private void prepareUpdateLists(int partner, List<Long> updates1, List<Long> keep, List<Long> send, long mask) {
        if (partner > myId) {
            for (long update : updates1) {

                if (update == POISON_PILL) {
                    shutDown = true;
                    continue;
                }
                if ((update & mask) != 0) {
                    send.add(update);
                } else {
                    keep.add(update);
                }
            }
        } else {
            for (long update : updates1) {

                if (update == POISON_PILL) {
                    shutDown = true;
                    continue;
                }

                if ((update & mask) != 0) {
                    keep.add(update);
                } else {
                    send.add(update);
                }
            }
        }

        if (shutDown) {
            keep.add(POISON_PILL);
            send.add(POISON_PILL);
        }
    }

    int whichPE(long pos) {
        return (int) (pos >> logLocalN) & (threadCount - 1);
    }

    private void performUpdates(List<Long> updateList) {
        for (long update : updateList) {
            if (update == POISON_PILL) {
                shutDown = true;
            } else {
                updateSingleCell(update);
            }
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

        for (int PE = 0; PE < threadCount; PE++) {
            RandomForRA random = new RandomForRA(PE);

            int executedRemotely = PCJ.get(PE, "executed");
            for (int update = 0; update < executedRemotely; update++) {
                long val = random.nextLong();
                if (whichPE(val) == myId) {

                    updateSingleCell(val);
                }
            }
        }

        long[] table = PCJ.getLocal("table");
        int ok = 0;
        for (int i = 0; i < table.length; i++) {
            if (table[i] == i + myId * localN) {
                ok++;
            } else {
                PCJ.log("Cell #" + i + " is " + table[i] + ", should be: " + (i + myId * localN));
            }
        }
        PCJ.putLocal("okCells", ok);
        PCJ.waitFor("okCells");
        PCJ.barrier();
    }

    private void thread0VerifyAll() {
        if (myId == 0) {
            for (int i = 0; i < threadCount; i++) {
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
