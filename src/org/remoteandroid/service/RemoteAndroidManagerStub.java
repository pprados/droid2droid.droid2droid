package org.remoteandroid.service;

import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.internal.Constants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.remoteandroid.Application;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.Cookies;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.discovery.DiscoverAndroids;
import org.remoteandroid.discovery.bluetooth.BluetoothDiscoverAndroids;
import org.remoteandroid.discovery.ip.IPDiscoverAndroids;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.IRemoteAndroidManager;
import org.remoteandroid.internal.RemoteAndroidManagerImpl;
import org.remoteandroid.pairing.Trusted;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

public class RemoteAndroidManagerStub extends IRemoteAndroidManager.Stub
{
	private ArrayList<DiscoverAndroids> mDrivers=new ArrayList<DiscoverAndroids>();
	private long mDiscoverMaxTimeout=0L;
	private Context mContext;
	private AtomicInteger mDiscoverCount=new AtomicInteger(0);
	
	private Cookies mCookies=new Cookies();
	
	/**
	 * 
	 * @param uri
	 * @return 0 if error, else random value
	 * @throws RemoteException
	 */
	public long getCookie(String uri)
	{
		long cookie=mCookies.getCookie(uri);
		if (cookie==0)
		{
			cookie=Application.sManager.askCookie(Uri.parse(uri));
			if (cookie!=0)
				mCookies.addCookie(uri, cookie);
		}
		return cookie;
	}
	
	public void removeCookie(String uri)
	{
		mCookies.removeCookie(uri);
	}
	public RemoteAndroidManagerStub(Context context)
	{
		mContext=context.getApplicationContext();
		if (ETHERNET)
		{
			mDrivers.add(new IPDiscoverAndroids(mContext,this));
		}
		if (BLUETOOTH)
		{
			BluetoothAdapter.getDefaultAdapter();
			mDrivers.add(new BluetoothDiscoverAndroids(mContext,this));
		}
	}
	
	@Override
	public void startDiscover(int flags,long timeToDiscover) throws RemoteException
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
		mContext.sendBroadcast(new Intent(RemoteAndroidManager.ACTION_START_DISCOVER_ANDROID),
				RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);

		for (DiscoverAndroids driver:mDrivers)
    	{
    		if (driver.startDiscovery(timeToDiscover,flags,this))
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
			mDiscoverMaxTimeout=0;
			if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"Discover process finished");
			mContext.sendBroadcast(new Intent(RemoteAndroidManager.ACTION_STOP_DISCOVER_ANDROID),
					RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
		}
	}
	@Override
	public void cancelDiscover() throws RemoteException
	{
		if (isDiscovering())
		{
			mDiscoverMaxTimeout=0;
	    	for (DiscoverAndroids driver:mDrivers)
	    	{
	    		driver.cancelDiscovery(this);
	    	}
		}
	}
	@Override
	public boolean isDiscovering()
	{
		boolean rc=System.currentTimeMillis()<mDiscoverMaxTimeout;
		return rc;
	}

	@Override
	public RemoteAndroidInfoImpl getInfo()
	{
		return getInfo(null);
	}
	public RemoteAndroidInfoImpl getInfo(ConnectionType type)
	{
		return Trusted.getInfo(mContext,type);
	}

	@Override
	public List<RemoteAndroidInfoImpl> getBoundedDevices() throws RemoteException
	{
		return Trusted.getBonded();
	}

	@Override
	public void setLog(int type, boolean state) throws RemoteException
	{
		RemoteAndroidManagerImpl.setLogInternal(type, state);
	}

}