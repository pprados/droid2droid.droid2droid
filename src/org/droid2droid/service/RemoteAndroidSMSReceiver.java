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

import static org.droid2droid.Constants.ETHERNET_TRY_TIMEOUT;
import static org.droid2droid.Constants.PREFERENCES_ACTIVE;
import static org.droid2droid.Constants.SMS_MESSAGE_SIZE;
import static org.droid2droid.Constants.SMS_PORT;
import static org.droid2droid.Constants.TAG_SMS;
import static org.droid2droid.Droid2DroidManager.ACTION_DISCOVER_ANDROID;
import static org.droid2droid.Droid2DroidManager.EXTRA_DISCOVER;
import static org.droid2droid.Droid2DroidManager.PERMISSION_DISCOVER_RECEIVE;
import static org.droid2droid.internal.Constants.D;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.V;
import static org.droid2droid.internal.Constants.W;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.Hashtable;

import org.droid2droid.RAApplication;
import org.droid2droid.discovery.Discover;
import org.droid2droid.internal.AbstractProtoBufRemoteAndroid;
import org.droid2droid.internal.Driver;
import org.droid2droid.internal.Droid2DroidManagerImpl;
import org.droid2droid.internal.Messages;
import org.droid2droid.internal.Messages.Type;
import org.droid2droid.internal.ProtobufConvs;
import org.droid2droid.internal.RemoteAndroidInfoImpl;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.RemoteException;
import android.telephony.SmsMessage;
import android.util.Log;
import android.util.SparseArray;

import com.google.protobuf.InvalidProtocolBufferException;

// TODO: utiliser le hack pour désactiver le receiver lorsque le service n'est pas actif
// https://developer.android.com/training/monitoring-device-state/manifest-receivers.html
// Warning: stateless instance
public final class RemoteAndroidSMSReceiver extends BroadcastReceiver
{
	private static class SmsPaquet
	{
		int max = -1;

		SparseArray<byte[]> bufs = new SparseArray<byte[]>();
	}
	
	private static final String ACTION_RECEIVE_SMS = "android.intent.action.DATA_SMS_RECEIVED";
	private static final String EXTRA_PDU="pdus";

	private static Hashtable<String, SmsPaquet> sAllFragments = new Hashtable<String, SmsPaquet>();

	@Override
	public void onReceive(Context context, Intent intent)
	{
		if (!intent.getAction().equals(ACTION_RECEIVE_SMS))
			return;
		final String uri = intent.getDataString();

		if (Uri.parse(uri).getPort() != SMS_PORT)
			return;

		final Object[] pdus = (Object[]) intent.getExtras().get(EXTRA_PDU);
		byte[] data = null;

		for (int i = 0; i < pdus.length; i++)
		{
			SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdus[i]);
			final String sender = msg.getOriginatingAddress();
			data = msg.getUserData();
			readData(context,data, sender);
		}
	}

	private void readData(Context context,byte[] fragment, String sender)
	{

		SmsPaquet currentFragments = sAllFragments.get(sender);
		if (currentFragments == null)
		{
			currentFragments = new SmsPaquet();
			sAllFragments.put(sender, currentFragments);
		}

		int fragNumber = fragment[0] & 0x7F;
		boolean isLast=((fragment[0] & 0x80) == 0x80);
		if (isLast)
			currentFragments.max = fragNumber;
		if (V) Log.v(TAG_SMS,"Receive fragment  #"+fragNumber+" ["+(fragment.length-1)+"] "+(((fragment[0] & 0x80) == 0x80) ? "(last)":""));
		currentFragments.bufs.put(fragNumber, fragment);

		if (currentFragments.bufs.size()-1 == currentFragments.max)
		{
			int bufferSize = (currentFragments.bufs.size() - 1) * (SMS_MESSAGE_SIZE - 1)
					+ currentFragments.bufs.get(currentFragments.max).length - 1;
			if (V) Log.v(TAG_SMS, "BufferSize = " + bufferSize);
			byte[] result = new byte[bufferSize];
			for (int i = 0; i < currentFragments.bufs.size(); ++i)
			{
				byte[] r = currentFragments.bufs.get(i);
				if (r == null)
				{
					currentFragments.bufs.clear();
					sAllFragments.remove(sender);
					if (W) Log.w(TAG_SMS, PREFIX_LOG + "Receive invalide SMS");
					return; // Ignore bad message
				}
				System.arraycopy(r, 1, result, i * (SMS_MESSAGE_SIZE - 1), r.length - 1);
			}
			currentFragments.bufs.clear();
			sAllFragments.remove(sender);
			Messages.BroadcastMsg bmsg;
			try
			{
				bmsg = Messages.BroadcastMsg.newBuilder().mergeFrom(result).build();
				onMessage(context,bmsg);
			}
			catch (InvalidProtocolBufferException e)
			{
				if (W) Log.w(TAG_SMS,"Invalide protobuf message");
			}
		}
	}
	
	// Receive SMS message
	private void onMessage(Context context,final Messages.BroadcastMsg msg)
	{
		if (msg.getType()==Messages.BroadcastMsg.Type.EXPOSE)
		{
			// Propagate the discover
			RemoteAndroidInfoImpl info=ProtobufConvs.toRemoteAndroidInfo(context,msg.getIdentity());
			// FIXME: Necessaire de faire les deux ? Si pas de discover en court, il faut envoyer quand même
			Discover.getDiscover().discover(info);
			Intent intent=new Intent(ACTION_DISCOVER_ANDROID);
			intent.putExtra(EXTRA_DISCOVER, info);
			RAApplication.sAppContext.sendBroadcast(intent,PERMISSION_DISCOVER_RECEIVE);
		}
		else if (msg.getType()==Messages.BroadcastMsg.Type.CONNECT)
		{
			// Send call-back broadcast
        	final SharedPreferences preferences=RAApplication.getPreferences();
			if (preferences.getBoolean(PREFERENCES_ACTIVE, false))
			{
	
				final RemoteAndroidInfoImpl info=ProtobufConvs.toRemoteAndroidInfo(context,msg.getIdentity());
				RAApplication.sThreadPool.execute(new Runnable()
				{
					@Override
					public void run()
					{
						try
						{
							tryAllUrisForBroadcast(msg.getCookie(),info.getUris());
						}
						catch (Exception e)
						{
							if (W && !D) Log.w(TAG_SMS,PREFIX_LOG+"Send broadcast message refused ("+e.getMessage()+")");
							if (D) Log.d(TAG_SMS,PREFIX_LOG+"Send broadcast message refused ("+e.getMessage()+")",e);
						}
					}
				});
			}
		}
		else
			if (W) Log.w(TAG_SMS,"Invalide message type");
	}
	
	private static void tryAllUrisForBroadcast(long cookie,String[] uris) throws UnknownHostException, RemoteException, SecurityException, IOException 
	{
		AbstractProtoBufRemoteAndroid binder=null;
		try
		{
			for (int i=0;i<uris.length;++i)
			{
				final Uri uri=Uri.parse(uris[i]);
				Driver driver=Droid2DroidManagerImpl.sDrivers.get(uri.getScheme());
				if (driver==null)
					throw new MalformedURLException("Unknown "+uri);
				binder=(AbstractProtoBufRemoteAndroid)driver.factoryBinder(RAApplication.sAppContext,RAApplication.getManager(),uri);
				if (binder.connect(Type.CONNECT_FOR_BROADCAST, 0,cookie,ETHERNET_TRY_TIMEOUT))
					break;
				binder.close();
			}
		}
		finally
		{
			if (binder!=null)
				binder.close();
		}
	}
	
	
	
}
