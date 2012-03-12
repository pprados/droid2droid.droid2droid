package org.remoteandroid.discovery;

import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.internal.Constants.ETHERNET;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.remoteandroid.Application;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.discovery.ip.IPDiscoverAndroids;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;

import android.content.Intent;
import android.os.RemoteException;
import android.util.Log;

public class Discover
{
	public interface Listener
	{
		void onDiscoverStart();
		void onDiscoverStop();
		void onDiscover(RemoteAndroidInfoImpl info);
	}
	
	private static final Discover sDiscover=new Discover();
	public static Discover getDiscover()
	{
		return sDiscover;
	}
	private ArrayList<DiscoverAndroids> mDrivers=new ArrayList<DiscoverAndroids>();
	private ArrayList<Listener> mCallBacks=new ArrayList<Listener>();
	private long mDiscoverMaxTimeout=0L;
	private AtomicInteger mDiscoverCount=new AtomicInteger(0);

	
	Discover()
	{
		// Register all drivers
		if (ETHERNET)
		{
			mDrivers.add(new IPDiscoverAndroids(this));
		}
		
	}
	public void registerListener(Listener callback)
	{
		mCallBacks.add(callback);
	}
	public void unregisterListener(Listener callback)
	{
		mCallBacks.remove(callback);
	}
	public synchronized void startDiscover(int flags,long timeToDiscover)
	{
		// FIXME Race condition ?
		if (timeToDiscover==RemoteAndroidManager.DISCOVER_INFINITELY || timeToDiscover==RemoteAndroidManager.DISCOVER_BEST_EFFORT)
		{
			mDiscoverMaxTimeout=Long.MAX_VALUE;
		}
		else
		{
			final long end=System.currentTimeMillis()+timeToDiscover;
			// Extend discovery
			if (end>mDiscoverMaxTimeout)
				mDiscoverMaxTimeout=end;
		}

		if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"Discover process started");
		for (int i=mCallBacks.size()-1;i>=0;--i)
		{
			mCallBacks.get(i).onDiscoverStart();
		}

		for (DiscoverAndroids driver:mDrivers)
    	{
    		if (driver.startDiscovery(timeToDiscover,flags))
    		{
    			mDiscoverCount.incrementAndGet();
    		}
    	}
		if (mDiscoverCount.get()==0)
		{
			if (timeToDiscover==RemoteAndroidManager.DISCOVER_BEST_EFFORT)
			{
				mDiscoverMaxTimeout=0;
				finishDiscover();
			}
		}
	}

	public void finishDiscover()
	{
		if (mDiscoverCount.decrementAndGet()<=0)
		{
			mDiscoverCount.set(0);
			mDiscoverMaxTimeout=0;
			if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"Discover process finished");
			for (int i=mCallBacks.size()-1;i>=0;--i)
			{
				mCallBacks.get(i).onDiscoverStop();
			}
		}
	}
	public void cancelDiscover()
	{
		if (isDiscovering())
		{
			mDiscoverMaxTimeout=0;
	    	for (DiscoverAndroids driver:mDrivers)
	    	{
	    		driver.cancelDiscovery();
	    	}
			for (int i=mCallBacks.size()-1;i>=0;--i)
			{
				mCallBacks.get(i).onDiscoverStop();
			}
		}
	}
	public boolean isDiscovering()
	{
		boolean rc=System.currentTimeMillis()<mDiscoverMaxTimeout;
		return rc;
	}
	public void discover(RemoteAndroidInfoImpl info)
	{
		for (int i=mCallBacks.size()-1;i>=0;--i)
		{
			mCallBacks.get(i).onDiscover(info);
		}
	}
}
