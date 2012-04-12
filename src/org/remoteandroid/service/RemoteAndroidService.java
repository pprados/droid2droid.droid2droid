package org.remoteandroid.service;

import static org.remoteandroid.Constants.ACTION_CLEAR_DOWNLOAD;
import static org.remoteandroid.Constants.ACTION_CLEAR_PROPOSED;
import static org.remoteandroid.Constants.ACTION_COMPLETE_HIDE;
import static org.remoteandroid.Constants.DEBUG;
import static org.remoteandroid.Constants.LOCK_ASK_DOWNLOAD;
import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.Constants.TAG_SERVER_BIND;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;

import org.remoteandroid.Application;
import org.remoteandroid.CommunicationWithLock;
import org.remoteandroid.Constants;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.binder.ip.NetSocketRemoteAndroid;
import org.remoteandroid.discovery.ip.IPDiscoverAndroids;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.ui.Notifications;

import android.app.Service;
import android.content.Intent;
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
    	Application.sSingleThread.execute(new Runnable()
    	{
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
			Application.sDiscover.cancelDiscover();
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
	//    	if (Build.VERSION.SDK_INT<11)
	//    	{
	//    		setForeground(false); // Android <2.0
	//    	}
			Application.sAppContext.sendBroadcast(new Intent(RemoteAndroidManager.ACTION_START_REMOTE_ANDROID),
				RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
	    	
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
		Application.sAppContext.sendBroadcast(new Intent(RemoteAndroidManager.ACTION_STOP_REMOTE_ANDROID),
			RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
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
					if (Compatibility.VERSION_SDK_INT>Compatibility.VERSION_ECLAIR_0_1)
					{
						new Runnable()
						{
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
