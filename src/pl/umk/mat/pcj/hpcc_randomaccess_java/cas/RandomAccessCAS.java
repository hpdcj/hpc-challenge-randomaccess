package pl.umk.mat.pcj.hpcc_randomaccess_java.cas;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import org.pcj.FutureObject;
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
    private int okCells;

}

public class RandomAccessCAS implements StartPoint {

    int logN;
    int globalN;
    int logLocalN;
    int localN;
    int logNumProcs;
    long localUpdates;
    int threadCount;
    int myId;

    RandomForRA random;
    public static final int BUFFERED_UPDATES = 1024;

    private boolean checkCasResult(Update update) {
        if (update.casResult.isDone()) {
            long result = update.casResult.get();
            if (result == update.expected) {
                return true;
            } else {
                update.casResult = update.getRemoteResult = null;
                
            }
        }
        return false;
    }

    private void checkRemoteGetResult(Update update) {
        if (update.getRemoteResult.isDone()) {
            update.expected = update.getRemoteResult.get();
            long result = update.expected ^ update.update;
            update.casResult = PCJ.cas(whichPE(update.update), "table", update.expected, result, randomNumberToLocalPosition(update.update));
        }
    }

    private void getRemotely(Update update) {
        update.getRemoteResult = PCJ.getFutureObject(whichPE(update.update), "table", randomNumberToLocalPosition(update.update));
    }

    private static class Update {

        public long update;
        public long expected;
        public FutureObject<Long> casResult;
        public FutureObject<Long> getRemoteResult;

    }

    public static void main(String[] args) {
        String nodesFileName = "nodes.txt";
        if (args.length > 0) {
            nodesFileName = args[0];
        }
        PCJ.deploy(RandomAccessCAS.class, RandomAccessNewStorage.class, nodesFileName);
    }

    public void main() throws Throwable {
        String[] rounds = {"Warmup", "After warmup"};
        for (String round : rounds) {
            if (myId == 0) {
                PCJ.log(round + " round");
            }
            initializeData();
            PCJ.barrier();
            double start = System.currentTimeMillis();
            performRandomAccess();
            double stop = System.currentTimeMillis();
            PCJ.log("Starting verification");
            verifyResultsLocally();
            thread0VerifyAll();
            if (myId == 0) {
                double seconds = (stop - start) * 1e-3;
                double gups = localUpdates * threadCount * 1e-9 / seconds;
                System.out.println("Time: " + seconds + " s, performance: " + gups);
            }
        }
    }

    private void initializeData() throws FileNotFoundException {
        Scanner in = new Scanner(new File("ra.config"));
        logN = in.nextInt();
        threadCount = PCJ.threadCount();
        myId = PCJ.myId();
        logNumProcs = (int) (Math.log(threadCount) / Math.log(2));
        logLocalN = logN - logNumProcs;
        localN = 1 << logLocalN;
        localUpdates = 4 * localN;
        long[] table = new long[localN];
        for (int i = 0; i < localN; i++) {
            table[i] = i + myId * localN;
        }
        PCJ.putLocal("table", table);
        random = new RandomForRA(myId);

    }

    private int randomNumberToLocalPosition(long rand) {
        return (int) (rand & (localN - 1));
    }

    private void performRandomAccess() {

        List<Update> updateList = new ArrayList<>();
        int update;
        for (update = 0; update < localUpdates;) {
            generateRemoteUpdates(updateList, update, BUFFERED_UPDATES);
            int performedUpdates = performUpdates(updateList);
            update += performedUpdates;
        }
    }

    private void generateRemoteUpdates(List<Update> numbers, int update, int CHUNK_SIZE) {
        
        for (int k = numbers.size(); k < CHUNK_SIZE && update + k < localUpdates; k++) {
            long rand = random.nextLong();
            Update updateClass = new Update();
            updateClass.update = rand;
            numbers.add(updateClass);
        }
    }

    int whichPE(long pos) {
        return (int) (pos >> logLocalN) & (threadCount - 1);
    }

    private int performUpdates(List<Update> updateList) {
        int updateCounter = 0;
        Iterator<Update> iterator = updateList.iterator();
        while (iterator.hasNext()) {
            Update update = iterator.next();
            if (update.casResult != null) {
                boolean casSuccessful = checkCasResult(update);
                if (casSuccessful) {
                    iterator.remove();
                    updateCounter++;
                }
            } else if (update.getRemoteResult != null) {
                checkRemoteGetResult(update);
            } else {
                getRemotely(update);
            }
        }
        return updateCounter;
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

            for (int update = 0; update < localUpdates; update++) {
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
