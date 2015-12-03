
package pl.umk.mat.pcj.hpcc_randomaccess_java;

public class HPCC_RandomAccess_tabparams_s {
  public long LocalTableSize; /* local size of the table may be rounded up >= MinLocalTableSize */
  public long ProcNumUpdates; /* usually 4 times the local size except for time-bound runs */

  public long logTableSize;   /* it is an unsigned 64-bit value to type-promote expressions */
  public long TableSize;      /* always power of 2 */
  public long MinLocalTableSize; /* TableSize/NumProcs */
  public long GlobalStartMyProc; /* first global index of the global table stored locally */
  public long Top; /* global indices below 'Top' are asigned in MinLocalTableSize+1 blocks;
                 above 'Top' -- in MinLocalTableSize blocks */

  //MPI blah blah blah
  //MPI_Datatype dtype64;
  //MPI_Status *finish_statuses; /* storage for 'NumProcs' worth of statuses */
  //MPI_Request *finish_req;     /* storage for 'NumProcs' worth of requests */

  public int logNumProcs, NumProcs, MyProc;

  public int Remainder; /* TableSize % NumProcs */    
  
  public boolean PowerOfTwo;

    public HPCC_RandomAccess_tabparams_s() {
    }
  
  
}
