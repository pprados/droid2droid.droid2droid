package org.remoteandroid.ui;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_PREFERENCE;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.internal.Constants.*;

import java.io.IOException;
import java.net.URL;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.internal.ListRemoteAndroidInfoImpl;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.ui.MainFragment;
import org.remoteandroid.ui.MainActivity;
import org.remoteandroid.ui.TabsAdapter;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
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

	private BroadcastReceiver mNetworkStateReceiver = new BroadcastReceiver()
	{

		@Override
		public void onReceive(Context context, Intent intent)
		{
			onReceiveNetworkEvent(
				context, intent);
		}
	};

	private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			onReceiveBluetoothEvent(
				context, intent);
		}
	};

	private BroadcastReceiver mAirPlane = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			onReceiveAirplaneEvent(
				context, intent);
		}
	};

	void onReceivePhoneEvent(Context context, Intent intent)
	{
		if (intent.getAction().equals(
			TelephonyManager.ACTION_PHONE_STATE_CHANGED))
		{
			// TODO
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		// Register receiver
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR)
		{
			registerReceiver(mNetworkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
			registerReceiver(mAirPlane, new IntentFilter("android.intent.action.SERVICE_STATE"));
			IntentFilter filter = new IntentFilter();
			filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
			filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
			registerReceiver(mBluetoothReceiver, filter);
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		// Unregister the discovery receiver
		unregisterReceiver(mNetworkStateReceiver);
		unregisterReceiver(mBluetoothReceiver);
		unregisterReceiver(mAirPlane);
	}

	protected abstract AbstractBodyFragment getActiveFragment();

	protected void onReceiveNetworkEvent(Context context, Intent intent)
	{
		if (getActiveFragment()==null) return;
		ConnectivityManager conn = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		NetworkInfo info=null;
		if (conn != null)
			info=conn.getActiveNetworkInfo();
		if (conn == null || info == null)
		{
			mActiveNetwork &= ~NetworkTools.ACTIVE_LOCAL_NETWORK|NetworkTools.ACTIVE_GLOBAL_NETWORK|NetworkTools.ACTIVE_INTERNET_NETWORK;
		}
		else
		{
			int type = conn.getActiveNetworkInfo().getType();
			switch (type)
			{
				case ConnectivityManager.TYPE_MOBILE:
				case ConnectivityManager.TYPE_MOBILE_DUN:
				case ConnectivityManager.TYPE_MOBILE_HIPRI:
				case ConnectivityManager.TYPE_MOBILE_MMS:
				case ConnectivityManager.TYPE_MOBILE_SUPL:
				case ConnectivityManager.TYPE_WIMAX:
					mActiveNetwork &= ~NetworkTools.ACTIVE_LOCAL_NETWORK;
					break;
				case ConnectivityManager.TYPE_BLUETOOTH:
				case ConnectivityManager.TYPE_ETHERNET:
				case ConnectivityManager.TYPE_WIFI:
					mActiveNetwork |= NetworkTools.ACTIVE_LOCAL_NETWORK;
					break;
				default:
					if (W)
						Log.w(
							TAG_DISCOVERY, PREFIX_LOG + "Unknown network type " + type);
					break;
			}
			mActiveNetwork &= ~NetworkTools.ACTIVE_INTERNET_NETWORK;
			if (info.isConnected())
			{
				// Check if the connection is linked to Internet or only intranet
				new AsyncTask<Void,Void,Boolean>()
				{
					@Override
					protected Boolean doInBackground(Void... params)
					{
						try
						{
							if (D) Log.d(TAG_EXPOSE,"Ping google...");
							new URL("http://www.google.com").openConnection().getInputStream().close(); // Ping google
							if (D) Log.d(TAG_EXPOSE,"Ping google done");
							return Boolean.TRUE;
						}
						catch (IOException e)
						{
							if (D) Log.d(TAG_EXPOSE,"Ping google fail");
							return Boolean.FALSE;
						}
					}
					protected void onPostExecute(Boolean result) 
					{
						if (result)
							mActiveNetwork |= NetworkTools.ACTIVE_INTERNET_NETWORK;
						else
							mActiveNetwork &= ~NetworkTools.ACTIVE_INTERNET_NETWORK;
						onUpdateActiveNetwork();
					}
				}.execute();
			}
		}
		onUpdateActiveNetwork();
	}

	protected void onReceiveBluetoothEvent(Context context, Intent intent)
	{
//		if (getActiveFragment()==null) return;
//		getActiveFragment().onReceiveBluetoothEvent(context, intent);

		if (intent.getAction().equals(
			BluetoothAdapter.ACTION_STATE_CHANGED))
		{
			int state = intent.getIntExtra(
				BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_DISCONNECTED);
			if (state == BluetoothAdapter.STATE_ON)
				mActiveNetwork |= NetworkTools.ACTIVE_BLUETOOTH;
			else
				mActiveNetwork &= ~NetworkTools.ACTIVE_BLUETOOTH;
			onUpdateActiveNetwork();
		}
	}

	protected void onReceiveAirplaneEvent(Context context, Intent intent)
	{
		if (getActiveFragment()==null) return;
		getActiveFragment().onReceiveAirplaneEvent(context, intent);
	}

	protected void onUpdateActiveNetwork()
	{
		getActiveFragment().onUpdateActiveNetwork(mActiveNetwork);
	}

	public int getActiveNetwork()
	{
		return mActiveNetwork;
	}

}