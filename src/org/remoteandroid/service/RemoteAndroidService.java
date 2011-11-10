package org.remoteandroid.service;

import static org.remoteandroid.Constants.ACTION_CLEAR_DOWNLOAD;
import static org.remoteandroid.Constants.ACTION_CLEAR_PROPOSED;
import static org.remoteandroid.Constants.ACTION_COMPLETE_HIDE;
import static org.remoteandroid.Constants.DEBUG;
import static org.remoteandroid.Constants.LOCK_ASK_DOWNLOAD;
import static org.remoteandroid.Constants.PREFERENCES_ACTIVE;
import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.Constants.TAG_SERVER_BIND;
import static org.remoteandroid.internal.Constants.BLUETOOTH;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;

import org.remoteandroid.Application;
import org.remoteandroid.CommunicationWithLock;
import org.remoteandroid.Constants;
import org.remoteandroid.binder.bluetooth.BluetoothRemoteAndroid;
import org.remoteandroid.binder.ip.NetSocketRemoteAndroid;
import org.remoteandroid.discovery.ip.IPDiscoverAndroids;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.ui.Notifications;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

public class RemoteAndroidService extends Service 
{
    private Notifications mNotification;

    private static RemoteAndroidService sMe;
    
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
    	if (BLUETOOTH)
    		BluetoothAdapter.getDefaultAdapter();
    	
    	Application.sThreadPool.execute(new Runnable()
    	{
    		@Override
    		public void run()
    		{
    	   		if (Application.getPreferences().getBoolean(PREFERENCES_ACTIVE, false))
    	   			startDaemon();
    		}
    	});
    }

    @Override
    public void onDestroy()
    {
    	super.onDestroy();
    	sMe=null;
    	if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Stop RemoteAndroidService");
    	stopDiscovery();
		stopDaemon();
    	stopSelf();
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
			//new TestBluetooth().test();
			NetSocketRemoteAndroid.startDaemon(getApplicationContext(),mNotification);
			if (BLUETOOTH)
			{
				if (Compatibility.VERSION_SDK_INT>Compatibility.VERSION_DONUT)
				{
					BluetoothRemoteAndroid.startDaemon(getApplicationContext(),mNotification);
				}
			}
	
	    	if (Constants.MODE_WITH_SERVICE_NOTIFICATION)
	    	{
	    		mNotification.serviceShow(this);
	    	}
	//    	if (Build.VERSION.SDK_INT<11)
	//    	{
	//    		setForeground(false); // Android <2.0
	//    	}
		}
		catch (Exception e)
		{
			if (E) Log.e(TAG_SERVER_BIND,"Impossible to start service",e);
		}
	}
    
	private void stopDaemon()
	{
		NetSocketRemoteAndroid.stopDaemon(getApplicationContext());
		IPDiscoverAndroids.unregisterService();
		if (Compatibility.VERSION_SDK_INT>Compatibility.VERSION_DONUT)
		{
			BluetoothRemoteAndroid.stopDaemon();
		}
	}

	// Invoqué par les notifications
//[[7	
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
		}
        return (DEBUG ? START_NOT_STICKY : START_STICKY);
    }    
//]]    

	@Override
	public IBinder onBind(Intent intent)
	{
		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"onBind mBoss="+Application.sDiscover);
		return Application.sDiscover;
	}
}