module load java
max=128
THREADS_PER_NODE=24
for size in 23 25 27 29
do
	num=1
	bytes=`echo 2^$size | bc`
	bytes=$(( bytes * 8 ))
	megabytes=$(( (bytes + 1048575) / 1048576 ))
	megabytes=$(( 2 * megabytes ))

	while [ $num -le $max ]
	do
		echo $megabytes
		nodes=$((num/THREADS_PER_NODE))
		nodes=$((nodes + 1))

		echo Total=$megabytes > hpccmemf.txt 

		salloc -N $nodes -n $num mpiexec hpcc	
		echo $size > ra.config
		echo 60 >> ra.config
		salloc -N $nodes -n $num ra.java.subcommand.sh $nodes
		
		num=$((num * 2))
	done
done
