#! /bin/bash

mpiexec hostname > nodes.txt
mpiexec -n $1 java -cp "hpcc_randomaccess_java.jar:PCJ-4.1.0.jar" -Xms4g -Xmx4g pl.umk.mat.pcj.hpcc_randomaccess_java.RandomAccessNew >> out.txt

