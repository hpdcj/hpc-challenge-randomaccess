/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.umk.mat.pcj.hpcc_randomaccess_java;

import java.io.File;
import java.io.FileNotFoundException;
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
    int[] table;
}
public class RandomAccessNew implements StartPoint {
    
    int N;
    int n;
    long updates;
    
    public static void main (String[] args) {
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
        Scanner in = new Scanner (new File ("ra.config"));
        N = in.nextInt();
        n = N / PCJ.threadCount();
        updates = 1L << n;
        long[] table = new long[n];
        for (int i = 0; i < n; i++) {
            table[i] = i + PCJ.myId() * n;
        }
        PCJ.putLocal("table", table);
    }

    private void performRandomAccess() {
        for (int update = 0; update < updates; update++) {
            
        }
    }
}
