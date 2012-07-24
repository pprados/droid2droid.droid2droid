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
package org.droid2droid.service;

import static org.droid2droid.Droid2DroidManager.ACTION_DISCOVER_ANDROID;
import static org.droid2droid.Droid2DroidManager.ACTION_START_DISCOVER_ANDROID;
import static org.droid2droid.Droid2DroidManager.ACTION_STOP_DISCOVER_ANDROID;
import static org.droid2droid.Droid2DroidManager.DISCOVER_BEST_EFFORT;
import static org.droid2droid.Droid2DroidManager.DISCOVER_INFINITELY;
import static org.droid2droid.Droid2DroidManager.EXTRA_DISCOVER;
import static org.droid2droid.Droid2DroidManager.EXTRA_UPDATE;
import static org.droid2droid.Droid2DroidManager.PERMISSION_DISCOVER_RECEIVE;
import static org.droid2droid.internal.Constants.COOKIE_EXCEPTION;
import static org.droid2droid.internal.Constants.COOKIE_NO;
import static org.droid2droid.internal.Constants.COOKIE_SECURITY;
import static org.droid2droid.internal.Constants.D;
import static org.droid2droid.internal.Constants.I;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.TAG_CLIENT_BIND;
import static org.droid2droid.internal.Constants.W;

import java.io.IOException;
import java.util.List;

import org.droid2droid.NfcUtils;
import org.droid2droid.RAApplication;
import org.droid2droid.discovery.Discover;
import org.droid2droid.internal.Droid2DroidManagerImpl;
import org.droid2droid.internal.IRemoteAndroidManager;
import org.droid2droid.internal.Messages.Type;
import org.droid2droid.internal.RemoteAndroidInfoImpl;
import org.droid2droid.pairing.Trusted;
import org.droid2droid.ui.connect.ConnectActivity;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.os.RemoteException;
import android.util.Log;

public final class RemoteAndroidManagerStub extends IRemoteAndroidManager.Stub
implements Discover.Listener
{
	private final Context mContext;
	private long mDiscoverMaxTimeout=0L;
	
	
	public RemoteAndroidManagerStub(Context context)
	{
		mContext=context.getApplicationContext();
		Discover.getDiscover().registerListener(this);
		RAApplication.startService();
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
		long cookie=RAApplication.getCookie(uri);
		if (cookie==COOKIE_NO)
		{
			try
			{
				cookie=RAApplication.getManager().askCookie(Uri.parse(uri),type,flags);
				if (cookie!=COOKIE_NO && cookie!=COOKIE_EXCEPTION && cookie!=COOKIE_SECURITY)
					RAApplication.addCookie(uri, cookie);
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
				if (W && !D) Log.w(TAG_CLIENT_BIND,PREFIX_LOG+"Connection for cookie impossible ("+((e!=null) ? e.getMessage() : "null") +"). Remote device is shared ?");
				if (D) Log.d(TAG_CLIENT_BIND,PREFIX_LOG+"Connection for cookie impossible. Remote device is shared ?",e);
				return -1;
			}			
		}
		return cookie;
	}
	public void addCookie(RemoteAndroidInfoImpl info,long cookie)
	{
		for (int i=info.uris.size()-1;i>=0;--i)
		{
			RAApplication.addCookie(info.uris.get(i), cookie);
		}
	}
	@Override
	public void removeCookie(String uri)
	{
		RAApplication.removeCookie(uri);
	}
	@Override
	public synchronized void startDiscover(int flags,long timeToDiscover) throws RemoteException
	{
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
	
	@TargetApi(9)
	@Override
	public NdefMessage createNdefMessage()
	{
		return NfcUtils.createNdefMessage(RAApplication.sAppContext, getInfo());
	}

	@Override
	public void setLog(int type, boolean state) throws RemoteException
	{
		Droid2DroidManagerImpl.setLogInternal(type, state);
	}

	// -------- Propagate events
	@Override
	public void onDiscoverStart()
	{
		if (mDiscoverMaxTimeout!=0)
		{
			mContext.sendBroadcast(new Intent(ACTION_START_DISCOVER_ANDROID),
				PERMISSION_DISCOVER_RECEIVE);
		}
	}
	
	@Override
	public void onDiscoverStop()
	{
//		if (mDiscoverMaxTimeout!=0)
//		{
			mContext.sendBroadcast(new Intent(ACTION_STOP_DISCOVER_ANDROID),
				PERMISSION_DISCOVER_RECEIVE);
//		}
	}
	
	@Override
	public void onDiscover(RemoteAndroidInfoImpl info)
	{
		if (ConnectActivity.sIsConnect) return;
		if (Discover.getDiscover().isDiscovering())
		{
			Intent intent=new Intent(ACTION_DISCOVER_ANDROID);
			intent.putExtra(EXTRA_DISCOVER, info);
			intent.putExtra(EXTRA_UPDATE, info);
			RAApplication.sAppContext.sendBroadcast(intent,PERMISSION_DISCOVER_RECEIVE);
		}
		else
		{
			Intent intent=new Intent(RAApplication.sAppContext,ConnectActivity.class)
				.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				.putExtra(ConnectActivity.EXTRA_INFO, info);
			
			RAApplication.sAppContext.startActivity(intent);
		}
	}


}