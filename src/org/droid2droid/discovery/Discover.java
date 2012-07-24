/******************************************************************************
 *
 * droid2droid - Distributed Android Framework
 * ==========================================
 *
 * Copyright (C) 2012 by Atos (http://www.http://atos.net)
 * http://www.droid2droid.org
 *
 ******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
******************************************************************************/
package org.droid2droid.discovery;

import static org.droid2droid.Constants.TAG_DISCOVERY;
import static org.droid2droid.Droid2DroidManager.DISCOVER_BEST_EFFORT;
import static org.droid2droid.Droid2DroidManager.DISCOVER_INFINITELY;
import static org.droid2droid.internal.Constants.D;
import static org.droid2droid.internal.Constants.ETHERNET;
import static org.droid2droid.internal.Constants.I;
import static org.droid2droid.internal.Constants.PREFIX_LOG;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.droid2droid.RAApplication;
import org.droid2droid.discovery.ip.IPDiscoverAndroids;
import org.droid2droid.internal.RemoteAndroidInfoImpl;

import android.util.Log;

public final class Discover
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
	private final ArrayList<DiscoverAndroids> mDrivers=new ArrayList<DiscoverAndroids>();
	private final ArrayList<Listener> mCallBacks=new ArrayList<Listener>();
	private long mDiscoverMaxTimeout=0L;
	private final AtomicInteger mDiscoverCount=new AtomicInteger(0);

	
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
		if (timeToDiscover==DISCOVER_INFINITELY || timeToDiscover==DISCOVER_BEST_EFFORT)
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
			final Listener cb=mCallBacks.get(i);
			RAApplication.sHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					cb.onDiscoverStart();
				}
			});
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
			if (timeToDiscover==DISCOVER_BEST_EFFORT)
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
				final Listener cb=mCallBacks.get(i);
				RAApplication.sHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						cb.onDiscoverStop();
					}
				});
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
				final Listener cb=mCallBacks.get(i);
				RAApplication.sHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						cb.onDiscoverStop();
					}
				});
			}
		}
	}
	public boolean isDiscovering()
	{
		boolean rc=System.currentTimeMillis()<mDiscoverMaxTimeout;
		return rc;
	}
	public void discover(final RemoteAndroidInfoImpl info)
	{
		if (info==null)
			if (D) Log.d(TAG_DISCOVERY,"info=null !");
		for (int i=mCallBacks.size()-1;i>=0;--i)
		{
			final Listener cb=mCallBacks.get(i);
			RAApplication.sHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					cb.onDiscover(info);
				}
			});
		}
	}
}
