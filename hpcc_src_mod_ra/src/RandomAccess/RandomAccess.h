/* -*- mode: C; tab-width: 2; indent-tabs-mode: nil; -*- */

/* Random number generator */
#ifdef LONG_IS_64BITS
#define POLY 0x0000000000000007UL
#define PERIOD 1317624576693539401L
#else
#define POLY 0x0000000000000007ULL
#define PERIOD 1317624576693539401LL
#endif

/* Macros for timing */
#define CPUSEC() (HPL_timer_cputime())
#define RTSEC() (MPI_Wtime())

extern u64Int HPCC_starts (s64Int);
extern u64Int HPCC_starts_LCG (s64Int);

#define WANT_MPI2_TEST 0


#define HPCC_TRUE 1
#define HPCC_FALSE 0
#define HPCC_DONE 0

#define FINISHED_TAG 1
#define UPDATE_TAG   2
#define USE_NONBLOCKING_SEND 1

#define MAX_TOTAL_PENDING_UPDATES 1024
#define LOCAL_BUFFER_SIZE MAX_TOTAL_PENDING_UPDATES

/*#define USE_MULTIPLE_RECV 1*/

#ifdef USE_MULTIPLE_RECV
#define MAX_RECV					16
#else
#define MAX_RECV					1
#endif

#define LCG_MUL64 6364136223846793005ULL
#define LCG_ADD64 1

extern u64Int *HPCC_Table;

extern u64Int LocalSendBuffer[LOCAL_BUFFER_SIZE];
extern u64Int LocalRecvBuffer[MAX_RECV*LOCAL_BUFFER_SIZE];

typedef struct HPCC_RandomAccess_tabparams_s {
  s64Int LocalTableSize; /* local size of the table may be rounded up >= MinLocalTableSize */
  s64Int ProcNumUpdates; /* usually 4 times the local size except for time-bound runs */

  u64Int logTableSize;   /* it is an unsigned 64-bit value to type-promote expressions */
  u64Int TableSize;      /* always power of 2 */
  u64Int MinLocalTableSize; /* TableSize/NumProcs */
  u64Int GlobalStartMyProc; /* first global index of the global table stored locally */
  u64Int Top; /* global indices below 'Top' are asigned in MinLocalTableSize+1 blocks;
                 above 'Top' -- in MinLocalTableSize blocks */

  MPI_Datatype dtype64;
  MPI_Status *finish_statuses; /* storage for 'NumProcs' worth of statuses */
  MPI_Request *finish_req;     /* storage for 'NumProcs' worth of requests */

  int logNumProcs, NumProcs, MyProc;

  int Remainder; /* TableSize % NumProcs */
} HPCC_RandomAccess_tabparams_t;

extern void AnyNodesMPIRandomAccessUpdate(HPCC_RandomAccess_tabparams_t tparams);
extern void Power2NodesMPIRandomAccessUpdate(HPCC_RandomAccess_tabparams_t tparams);
extern void HPCC_AnyNodesMPIRandomAccessUpdate_LCG(HPCC_RandomAccess_tabparams_t tparams);
extern void HPCC_Power2NodesMPIRandomAccessUpdate_LCG(HPCC_RandomAccess_tabparams_t tparams);

extern int HPCC_RandomAccess(HPCC_Params *params, int doIO, double *GUPs, int *failure);
extern int HPCC_RandomAccess_LCG(HPCC_Params *params, int doIO, double *GUPs, int *failure);

extern void HPCC_Power2NodesMPIRandomAccessCheck(HPCC_RandomAccess_tabparams_t tparams, s64Int *NumErrors);
extern void HPCC_AnyNodesMPIRandomAccessCheck(HPCC_RandomAccess_tabparams_t tparams, s64Int *NumErrors);
extern void HPCC_Power2NodesMPIRandomAccessCheck_LCG(HPCC_RandomAccess_tabparams_t tparams, s64Int *NumErrors);
extern void HPCC_AnyNodesMPIRandomAccessCheck_LCG(HPCC_RandomAccess_tabparams_t tparams, s64Int *NumErrors);

#if defined( RA_SANDIA_NOPT )
#define HPCC_RA_ALGORITHM 1
#elif defined( RA_SANDIA_OPT2 )
#define HPCC_RA_ALGORITHM 2
#else
#define HPCC_RA_STDALG 1
#define HPCC_RA_ALGORITHM 0
#endif
