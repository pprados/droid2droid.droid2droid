package org.remoteandroid.pairing;

import static org.remoteandroid.Constants.PAIR_ANTI_SPOOF;
import static org.remoteandroid.internal.Constants.*;

import org.remoteandroid.Application;
import org.remoteandroid.binder.AbstractSrvRemoteAndroid.ConnectionContext;
import org.remoteandroid.internal.AbstractProtoBufRemoteAndroid;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.Messages.Type;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

public abstract class Pairing
{
	private static boolean sNoDOS=false;
	
	public static boolean isGlobalLock()
	{
		if (PAIR_ANTI_SPOOF)
		{
			if (D) Log.d(TAG_PAIRING,PREFIX_LOG+"Global lock set");
			if (sNoDOS) 
			{
				if (E) Log.e(TAG_PAIRING,PREFIX_LOG+"Multiple pairing process at the same time");
				return false;
			}
			sNoDOS=true;
		}
		return false;
	}
	public static void globalUnlock()
	{
		if (PAIR_ANTI_SPOOF)
		{
			if (sNoDOS)
			{
				if (D) Log.d(TAG_PAIRING,PREFIX_LOG+"Global lock unset");
				sNoDOS=false;
			}
		}
	}
	
	protected final Context mAppContext;
	protected Pairing(Context appContext)
	{
		mAppContext=appContext;
	}
	public void unpairing(AbstractProtoBufRemoteAndroid remoteAndroid,long timeout)
	{
		try
		{
			Application.clearCookies();
		    final long threadid = Thread.currentThread().getId();
			Msg msg = Msg.newBuilder()
				.setType(Type.PAIRING_CHALENGE)
				.setThreadid(threadid)
				.setPairingstep(-1)
				.build();
			remoteAndroid.sendRequestAndReadResponse(msg,timeout);
		}
		catch (RemoteException e)
		{
			// Ignore. Best effort
			if (I) Log.i(TAG_PAIRING,PREFIX_LOG+"Remote unpairing impossible");
		}
	}
	
	abstract public boolean client(AbstractProtoBufRemoteAndroid remoteAndroid,String uri,long timeout);
	
	public Msg server(ConnectionContext context, Msg msg,long cookie)
	{
		int step=msg.getPairingstep();
		if (step==-1) // Unpairing
		{
			Trusted.unregisterDevice(mAppContext, context.mClientInfo);
			return Msg.newBuilder()
				.setType(Type.PAIRING_CHALENGE)
				.setThreadid(msg.getThreadid())
				.setPairingstep(msg.getPairingstep())
				.build();
		}
		return null;
	}


}
