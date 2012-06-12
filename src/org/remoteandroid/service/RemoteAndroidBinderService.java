package org.remoteandroid.service;

import static org.remoteandroid.Constants.TAG_SERVER_BIND;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;

import org.remoteandroid.RAApplication;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public final class RemoteAndroidBinderService extends Service 
{

	@Override
	public IBinder onBind(Intent intent)
	{
		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"onBind mBoss="+RAApplication.sDiscover);
		return RAApplication.sDiscover;
	}
}
