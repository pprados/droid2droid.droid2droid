package org.remoteandroid.ui.connect.dtmf;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class DoubleArrayPool 
{
	static final int BLOCK_SIZE=1024;
	private static Queue<double[]> sPool = new LinkedList<double[]>();
	public static synchronized double[] get(boolean clear)
	{
		double[] rc=sPool.poll();
		if (rc==null)
			return new double[BLOCK_SIZE];
		if (clear)
			Arrays.fill(rc, 0);
		return rc;
	}
	public static synchronized void release(double[] array)
	{
		sPool.add(array);
	}
	
	public static double[] fromShortArray(short[] buffer)
	{
		double[] rc=get(false);
		for (int i = 0;i<BLOCK_SIZE; ++i) 
			rc[i] = (double) buffer[i];
		return rc;
	}
	
}