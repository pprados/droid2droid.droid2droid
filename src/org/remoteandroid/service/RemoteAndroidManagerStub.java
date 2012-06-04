package org.remoteandroid.service;

import static org.remoteandroid.internal.Constants.COOKIE_EXCEPTION;
import static org.remoteandroid.internal.Constants.COOKIE_NO;
import static org.remoteandroid.internal.Constants.COOKIE_SECURITY;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_CLIENT_BIND;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;
import java.util.List;

import org.remoteandroid.Application;
import org.remoteandroid.NfcUtils;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.discovery.Discover;
import org.remoteandroid.internal.IRemoteAndroidManager;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.RemoteAndroidManagerImpl;
import org.remoteandroid.pairing.Trusted;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.os.RemoteException;
import android.util.Log;

public final class RemoteAndroidManagerStub extends IRemoteAndroidManager.Stub
implements Discover.Listener
{
	private Context mContext;
	private long mDiscoverMaxTimeout=0L;
	
	
	public RemoteAndroidManagerStub(Context context)
	{
		mContext=context.getApplicationContext();
		Discover.getDiscover().registerListener(this);
		Application.startService();
	}
	
	/**
	 * 
	 * @param uri
	 * @return 0 if error, else random value
	 * @throws RemoteException
	 */
	@Override
	public long getCookie(int flags,String uri)
	{
		return getCookie(flags,uri,Type.CONNECT_FOR_COOKIE);
	}
	public long getCookie(int flags,String uri,
			Type type // FIXME: Pourquoi est-ce encore nécessaire ?
			)
	{
		long cookie=Application.getCookie(uri);
		if (cookie==COOKIE_NO)
		{
			try
			{
				cookie=Application.getManager().askCookie(Uri.parse(uri),type,flags);
				if (cookie!=COOKIE_NO && cookie!=COOKIE_EXCEPTION && cookie!=COOKIE_SECURITY)
					Application.addCookie(uri, cookie);
			}
			catch (SecurityException e)
			{
				if (I) Log.i(TAG_CLIENT_BIND,PREFIX_LOG+"Connection for cookie impossible ("+e.getMessage()+")");
//				if (PAIR_AUTO_IF_NO_COOKIE)
//				{
//					ArrayList<String> uris=new ArrayList<String>();
//					uris.add(uri);
//					if (new Trusted(mContext, Application.sHandler)
//						.pairWith(uris.toArray(new String[0]))==null)
//						return -1;
//					try
//					{
//						cookie=Application.getManager().askCookie(Uri.parse(uri),Type.CONNECT_FOR_COOKIE,flags);
//						if (cookie!=COOKIE_NO && cookie!=COOKIE_EXCEPTION)
//							Application.addCookie(uri, cookie);
//						return cookie;
//					}
//					catch (IOException ee)
//					{
//						if (W) Log.w(TAG_CLIENT_BIND,PREFIX_LOG+"Connection for cookie after pairing impossible ("+ee.getMessage()+")");
//						return COOKIE_EXCEPTION;
//					}
//				}
//				else
					return COOKIE_SECURITY;
			}
			catch (IOException e)
			{
				if (W) Log.w(TAG_CLIENT_BIND,PREFIX_LOG+"Connection for cookie impossible ("+((e!=null) ? e.getMessage() : "null") +")");
				return -1;
			}			
		}
		return cookie;
	}
	public void addCookie(RemoteAndroidInfoImpl info,long cookie)
	{
		for (int i=info.uris.size()-1;i>=0;--i)
		{
			Application.addCookie(info.uris.get(i), cookie);
		}
	}
	public void removeCookie(String uri)
	{
		Application.removeCookie(uri);
	}
	@Override
	public synchronized void startDiscover(int flags,long timeToDiscover) throws RemoteException
	{
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
		Discover.getDiscover().startDiscover(flags, timeToDiscover);
	}

	@Override
	public void cancelDiscover() throws RemoteException
	{
		mDiscoverMaxTimeout=0;
		Discover.getDiscover().cancelDiscover();
	}
	@Override
	public boolean isDiscovering()
	{
		return System.currentTimeMillis()<mDiscoverMaxTimeout;
	}
	@Override
	public RemoteAndroidInfoImpl getInfo()
	{
		return Trusted.getInfo(mContext);
	}

	@Override
	public List<RemoteAndroidInfoImpl> getBondedDevices() throws RemoteException
	{
		return Trusted.getBonded();
	}

	@Override
	public boolean isBonded(RemoteAndroidInfoImpl info)
	{
		return Trusted.isBonded(info);
	}
	
	@Override
	public NdefMessage createNdefMessage()
	{
		return NfcUtils.createNdefMessage(Application.sAppContext, getInfo());
	}

	@Override
	public void setLog(int type, boolean state) throws RemoteException
	{
		RemoteAndroidManagerImpl.setLogInternal(type, state);
	}

	// -------- Propagate events
	@Override
	public void onDiscoverStart()
	{
		if (mDiscoverMaxTimeout!=0)
		{
			mContext.sendBroadcast(new Intent(RemoteAndroidManager.ACTION_START_DISCOVER_ANDROID),
				RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
		}
	}
	
	@Override
	public void onDiscoverStop()
	{
		if (mDiscoverMaxTimeout!=0)
		{
			mContext.sendBroadcast(new Intent(RemoteAndroidManager.ACTION_STOP_DISCOVER_ANDROID),
				RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
		}
	}
	
	@Override
	public void onDiscover(RemoteAndroidInfoImpl info)
	{
		if (mDiscoverMaxTimeout!=0)
		{
			Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
			intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
			intent.putExtra(RemoteAndroidManager.EXTRA_UPDATE, info);
			Application.sAppContext.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
		}
	}


}