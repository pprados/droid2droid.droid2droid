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

import static org.droid2droid.Constants.ACTION_CLEAR_DOWNLOAD;
import static org.droid2droid.Constants.ACTION_CLEAR_PROPOSED;
import static org.droid2droid.Constants.ACTION_COMPLETE_HIDE;
import static org.droid2droid.Constants.DEBUG;
import static org.droid2droid.Constants.LOCK_ASK_DOWNLOAD;
import static org.droid2droid.Constants.TAG_DISCOVERY;
import static org.droid2droid.Constants.TAG_SERVER_BIND;
import static org.droid2droid.Droid2DroidManager.ACTION_START_REMOTE_ANDROID;
import static org.droid2droid.Droid2DroidManager.ACTION_STOP_REMOTE_ANDROID;
import static org.droid2droid.Droid2DroidManager.PERMISSION_DISCOVER_RECEIVE;
import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.V;

import org.droid2droid.CommunicationWithLock;
import org.droid2droid.Constants;
import org.droid2droid.RAApplication;
import org.droid2droid.binder.ip.NetSocketRemoteAndroid;
import org.droid2droid.discovery.ip.IPDiscoverAndroids;
import org.droid2droid.ui.Notifications;

import android.app.Service;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public final class RemoteAndroidService extends Service 
{
    private Notifications mNotification;

    private static volatile RemoteAndroidService sMe;
    
    public static boolean isActive()
    {
    	return sMe!=null;
    }
    
	@Override
	public RAApplication getApplicationContext()
	{
		return (RAApplication)super.getApplicationContext();
	}

	/**
     * Called by the system when the service is first created.  Do not call this method directly.
     */
    @Override
    public void onCreate() 
    {
    	super.onCreate();
    	sMe=this;
    	if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Start RemoteAndroidService");
    	
    	mNotification=new Notifications(this);
    	
    }

    @Override
    public void onDestroy()
    {
    	super.onDestroy();
    	sMe=null;
    	if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Stop RemoteAndroidService");
    	RAApplication.sSingleThread.execute(new Runnable()
    	{
    		@Override
			public void run() 
    		{
            	stopDiscovery();
        		stopDaemon();
//            	stopSelf();
    		}
    	});
    }
	private void stopDiscovery()
	{
		try
		{
			RAApplication.sDiscover.cancelDiscover();
		}
		catch (RemoteException e)
		{
			if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+e.getMessage(),e);
		}
		
	}
	private void startDaemon()
	{
		// Start daemon
		try
		{
//	    	getPackageManager().setComponentEnabledSetting(new ComponentName(this,this.getClass()),
//    	        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
//    	        PackageManager.DONT_KILL_APP);    	
    	
			//new TestBluetooth().test();
			NetSocketRemoteAndroid.startDaemon(getApplicationContext(),mNotification);
	    	if (Constants.SHOW_SERVICE_NOTIFICATION)
	    	{
	    		mNotification.serviceShow(this);
	    	}
	//    	if (VERSION.SDK_INT<11)
	//    	{
	//    		setForeground(false); // Android <2.0
	//    	}
			RAApplication.sAppContext.sendBroadcast(new Intent(ACTION_START_REMOTE_ANDROID),
				PERMISSION_DISCOVER_RECEIVE);
	    	
		}
		catch (Exception e)
		{
			if (E) Log.e(TAG_SERVER_BIND,"Impossible to start service",e);
		}
	}
    
	private void stopDaemon()
	{
		NetSocketRemoteAndroid.stopDaemon(getApplicationContext());
		IPDiscoverAndroids.asyncUnregisterService();
		RAApplication.sAppContext.sendBroadcast(new Intent(ACTION_STOP_REMOTE_ANDROID),
			PERMISSION_DISCOVER_RECEIVE);
	}

	// Invoqué par les notifications
	@Override
    public int onStartCommand(Intent intent, int flags, int startId) 
	{
		if (intent!=null)
		{
			String action=intent.getAction();
			if (ACTION_COMPLETE_HIDE.equals(action))
			{
				mNotification.clearDownloads();
			}
			else if (ACTION_CLEAR_DOWNLOAD.equals(action) || ACTION_CLEAR_PROPOSED.equals(action))
			{
				final int id=intent.getIntExtra(Notifications.EXTRA_ID, -1);
				{
					CommunicationWithLock.putResult(LOCK_ASK_DOWNLOAD+id,false);
					if (VERSION.SDK_INT>VERSION_CODES.ECLAIR_0_1)
					{
						new Runnable()
						{
							@Override
							public void run() 
							{
								mNotification.mNotificationMgr.cancel(Notifications.LABEL_NOTIF_DOWNLOAD,id);
							}
						}.run();
					}
					else
						mNotification.mNotificationMgr.cancel(id);
				}
			}
			else
			{
//		    	Application.sSingleThread.execute(new Runnable()
//		    	{
//		    		@Override
//		    		public void run()
//		    		{
//		    	   		if (Application.getPreferences().getBoolean(PREFERENCES_ACTIVE, false))
//		    	   			startDaemon();
//		    		}
//		    	});
	   			startDaemon();
			}
		}
        return (DEBUG ? START_NOT_STICKY : START_STICKY);
    }    
	
	@Override
	public IBinder onBind(Intent intent)
	{
		return null;
	}
}
