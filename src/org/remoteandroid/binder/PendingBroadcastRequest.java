package org.remoteandroid.binder;

import java.util.LinkedList;
import java.util.List;

import org.remoteandroid.internal.RemoteAndroidInfoImpl;

public final class PendingBroadcastRequest
{
	public interface OnBroadcastReceive
	{
		boolean onBroadcast(long cookie,RemoteAndroidInfoImpl info);
	}
	private static List<OnBroadcastReceive> sCallBacks=new LinkedList<OnBroadcastReceive>();
	public static synchronized void registerListener(OnBroadcastReceive receive)
	{
		sCallBacks.add(receive);
	}
	public static synchronized void removeListener(OnBroadcastReceive receive)
	{
		sCallBacks.remove(receive);
	}
	public static synchronized boolean notify(long cookie,RemoteAndroidInfoImpl info)
	{
		boolean rc=false;
		for (OnBroadcastReceive cb:sCallBacks)
		{
			if (cb.onBroadcast(cookie,info))
				rc=true;
		}
		return rc;
	}
}
