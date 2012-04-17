package org.remoteandroid.service;

import static org.remoteandroid.Constants.ETHERNET_TRY_TIMEOUT;
import static org.remoteandroid.Constants.PREFERENCES_ACTIVE;
import static org.remoteandroid.Constants.SMS_MESSAGE_SIZE;
import static org.remoteandroid.Constants.SMS_PORT;
import static org.remoteandroid.Constants.TAG_SMS;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.Hashtable;

import org.remoteandroid.Application;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.discovery.Discover;
import org.remoteandroid.internal.AbstractProtoBufRemoteAndroid;
import org.remoteandroid.internal.Driver;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.RemoteAndroidManagerImpl;

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
	static class SmsPaquet
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
			Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
			intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
			Application.sAppContext.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
		}
		else if (msg.getType()==Messages.BroadcastMsg.Type.CONNECT)
		{
			// Send call-back broadcast
        	final SharedPreferences preferences=Application.getPreferences();
			if (preferences.getBoolean(PREFERENCES_ACTIVE, false))
			{
	
				final RemoteAndroidInfoImpl info=ProtobufConvs.toRemoteAndroidInfo(context,msg.getIdentity());
				Application.sThreadPool.execute(new Runnable()
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
				Driver driver=RemoteAndroidManagerImpl.sDrivers.get(uri.getScheme());
				if (driver==null)
					throw new MalformedURLException("Unknown "+uri);
				binder=(AbstractProtoBufRemoteAndroid)driver.factoryBinder(Application.sAppContext,Application.getManager(),uri);
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
