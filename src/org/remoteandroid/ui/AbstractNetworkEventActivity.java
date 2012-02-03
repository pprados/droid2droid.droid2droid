package org.remoteandroid.ui;

import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.W;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.ui.MainFragment;
import org.remoteandroid.ui.MainActivity;
import org.remoteandroid.ui.TabsAdapter;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.ActionBar.Tab;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.support.v4.view.ViewPager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuInflater;


public abstract class AbstractNetworkEventActivity extends FragmentActivity
{
	private int mActiveNetwork;
	private BroadcastReceiver mNetworkStateReceiver=new BroadcastReceiver() 
    {
		
        @Override
        public void onReceive(Context context, Intent intent) 
        {
        	onReceiveNetworkEvent(context,intent);
        }
    };
    
    private BroadcastReceiver mBluetoothReceiver=new BroadcastReceiver()
    {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			onReceiveBluetoothEvent(context,intent);
		}
    };
	private BroadcastReceiver mAirPlane = new BroadcastReceiver() 
	{
	      @Override
	      public void onReceive(Context context, Intent intent) 
	      {
	            onReceiveAirplaneEvent(context,intent);
	      }
	};
	
	void onReceivePhoneEvent(Context context,Intent intent)
	{
		if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
		{
			// TODO
		}
	}
	protected abstract AbstractNetworkEventFragment getActiveFragment();
	
	void onReceiveNetworkEvent(Context context,Intent intent)
	{
		getActiveFragment().onReceiveNetworkEvent(context,intent);
		
		ConnectivityManager conn=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
		if (conn==null || conn.getActiveNetworkInfo()==null)
		{
			mActiveNetwork&=~NetworkTools.ACTIVE_LOCAL_NETWORK;
		}
		else
		{
			int type=conn.getActiveNetworkInfo().getType();
			switch (type)
			{
				case ConnectivityManager.TYPE_MOBILE:
				case ConnectivityManager.TYPE_MOBILE_DUN:
				case ConnectivityManager.TYPE_MOBILE_HIPRI:
				case ConnectivityManager.TYPE_MOBILE_MMS:
				case ConnectivityManager.TYPE_MOBILE_SUPL:
				case ConnectivityManager.TYPE_WIMAX:
					mActiveNetwork&=~NetworkTools.ACTIVE_LOCAL_NETWORK;
					break;
				case ConnectivityManager.TYPE_BLUETOOTH:
				case ConnectivityManager.TYPE_ETHERNET:
				case ConnectivityManager.TYPE_WIFI:
					mActiveNetwork|=NetworkTools.ACTIVE_LOCAL_NETWORK;
					break;
	            default:
	            	if (W) Log.w(TAG_DISCOVERY,PREFIX_LOG+"Unknown network type "+type);
	            	break;
	        }
		}
		onUpdateActiveNetwork();
	}
	void onReceiveBluetoothEvent(Context context, Intent intent)
	{
		getActiveFragment().onReceiveBluetoothEvent(context,intent);
		
		if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))
		{
			int state=intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_DISCONNECTED);
			if (state==BluetoothAdapter.STATE_ON)
				mActiveNetwork|=NetworkTools.ACTIVE_BLUETOOTH;
			else
				mActiveNetwork&=~NetworkTools.ACTIVE_BLUETOOTH;
			onUpdateActiveNetwork();
		}
	}
	void onReceiveAirplaneEvent(Context context,Intent intent)
	{
		getActiveFragment().onReceiveAirplaneEvent(context,intent);
	}
	
	void onUpdateActiveNetwork()
	{
		getActiveFragment().onUpdateActiveNetwork();
	}
	protected int getActiveNetwork()
	{
		return mActiveNetwork;
	}
	
}
