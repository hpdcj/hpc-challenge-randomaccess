package pl.umk.mat.pcj.hpcc_randomaccess_java;

public class Utility {
/* unsigned */ static long HPCC_starts(long n)
{
  int i, j;
  /* unsigned */ long[] m2 = new /* unsigned */ long[64];
  /* unsigned */ long temp, ran;

  while (n < 0) n += DefineConstants.PERIOD;
  while (n > DefineConstants.PERIOD) n -= DefineConstants.PERIOD;
  if (n == 0) return 0x1;

  temp = 0x1;
  for (i=0; i<64; i++) {
    m2[i] = temp;
    temp = (temp << 1) ^ ((long) temp < 0 ? DefineConstants.POLY : 0);
    temp = (temp << 1) ^ ((long) temp < 0 ? DefineConstants.POLY : 0);
  }

  for (i=62; i>=0; i--)
    if (((n >> i) & 1) == 1)
      break;

  ran = 0x2;
  while (i > 0) {
    temp = 0;
    for (j=0; j<64; j++)
      if (((ran >> j) & 1) == 1)
        temp ^= m2[j];
    ran = temp;
    i -= 1;
    if (((n >> i) & 1) == 1)
      ran = (ran << 1) ^ ((long) ran < 0 ? DefineConstants.POLY : 0);
  }

  return ran;
}    
}
