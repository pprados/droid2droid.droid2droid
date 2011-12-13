package org.remoteandroid.ui.connect;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public abstract class AbstractBodyFragment extends Fragment
{
	Technology mTechnology;
	
	protected AbstractBodyFragment()
	{
	}
	protected void setTechnology(Technology technology)
	{
		mTechnology=technology;
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
	@Override
	public abstract View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);
	
}
