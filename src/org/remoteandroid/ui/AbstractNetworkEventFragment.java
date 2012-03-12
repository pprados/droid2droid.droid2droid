package org.remoteandroid.ui;

import com.actionbarsherlock.app.SherlockFragment;

import android.content.Context;
import android.content.Intent;
import android.support.v4.app.Fragment;

public abstract class AbstractNetworkEventFragment extends SherlockFragment
{
	protected AbstractNetworkEventFragment()
	{
	}

	protected void onReceiveNetworkEvent(Context context,Intent intent)
	{
		
	}
	protected void onReceiveBluetoothEvent(Context context, Intent intent)
	{
		
	}
	protected void onReceiveAirplaneEvent(Context context,Intent intent)
	{
		
	}
	protected void onUpdateActiveNetwork()
	{
		
	}
	protected int getActiveNetwork()
	{
		return getNetworkActivity().getActiveNetwork();
	}
	public AbstractNetworkEventActivity getNetworkActivity()
	{
		return (AbstractNetworkEventActivity)super.getActivity();
	}
	
}
