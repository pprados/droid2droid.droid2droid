package org.remoteandroid.service;

import static org.remoteandroid.Constants.ACTION_CLEAR_DOWNLOAD;
import static org.remoteandroid.Constants.ACTION_CLEAR_PROPOSED;
import static org.remoteandroid.Constants.ACTION_COMPLETE_HIDE;
import static org.remoteandroid.Constants.DEBUG;
import static org.remoteandroid.Constants.LOCK_ASK_DOWNLOAD;
import static org.remoteandroid.Constants.PREFERENCES_ACTIVE;
import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.Constants.TAG_SERVER_BIND;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;

import org.remoteandroid.Application;
import org.remoteandroid.CommunicationWithLock;
import org.remoteandroid.Constants;
import org.remoteandroid.binder.ip.NetSocketRemoteAndroid;
import org.remoteandroid.discovery.ip.IPDiscoverAndroids;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.ui.Notifications;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public final class RemoteAndroidBinderService extends Service 
{

	@Override
	public IBinder onBind(Intent intent)
	{
		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"onBind mBoss="+Application.sDiscover);
		return Application.sDiscover;
	}
}
