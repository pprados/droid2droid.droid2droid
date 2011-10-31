package org.remoteandroid;

import android.util.SparseArray;

public class CommunicationWithLock
{
	
	public static SparseArray<Object> sResults=new SparseArray<Object>();
	
	public static void putResult(int key,Object value)
	{
		synchronized (sResults)
		{
			sResults.put(key, value);
			sResults.notifyAll();
		}
	}

	/** 
	 * Return result or null if timeout.
	 * 
	 * @param key
	 * @param timeout
	 * @return
	 */
	public static Object getResult(int key,long timeout)
	{
		long t=System.currentTimeMillis();
		long to=timeout;
		
		synchronized (sResults)
		{
			Object val;
			for (;;)
			{
				val=sResults.get(key);
				if (val!=null)
				{
					sResults.remove(key);
					return val;
				}
				if (to<=0)
				{
					return null;
				}
				try
				{
					sResults.wait(to);
				}
				catch (InterruptedException e)
				{
					to-=System.currentTimeMillis()-t;
				}
			} 
		}
	}
}
