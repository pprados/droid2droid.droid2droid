package org.remoteandroid.ui;


import com.actionbarsherlock.app.SherlockFragment;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.nfc.Tag;
import android.os.Bundle;
import android.support.v4.app.Fragment;

public abstract class AbstractBodyFragment extends SherlockFragment
{
	public interface OnNfcEvent
	{
		public void onNfcTag(Intent intent);
	};
	protected ContentResolver mContentResolver;
	
	protected AbstractBodyFragment()
	{
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mContentResolver=getActivity().getContentResolver();
	}
	protected ContentResolver getContentResolver()
	{
		return mContentResolver;
	}
	@Override
	public void onResume()
	{
		super.onResume();
		updateStatus(((AbstractNetworkEventActivity)getSherlockActivity()).getActiveNetwork());
	}
//	public void onReceiveNetworkEvent(Context context,Intent intent,int activeNetwork)
//	{
//		updateStatus(activeNetwork);
//	}
//	public void onReceiveBluetoothEvent(Context context, Intent intent)
//	{
//		updateStatus(((AbstractNetworkEventActivity)getActivity()).getActiveNetwork());
//	}
	public void onReceiveAirplaneEvent(Context context,Intent intent)
	{
		//updateStatus(((AbstractNetworkEventActivity)getActivity()).getActiveNetwork());
	}
	public void onUpdateActiveNetwork(int activeNetwork)
	{
		updateStatus(activeNetwork);
	}
	public void onPageSelected()
	{
		if (getActivity()!=null)
			updateStatus(((AbstractNetworkEventActivity)getActivity()).getActiveNetwork());
	}
	public void onPageUnselected()
	{
	}
	protected void updateStatus(int activeNetwork)
	{
		
	}
}
