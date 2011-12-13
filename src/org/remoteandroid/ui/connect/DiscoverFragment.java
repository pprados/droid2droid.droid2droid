package org.remoteandroid.ui.connect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.*;
import org.remoteandroid.ListRemoteAndroidInfo;
import org.remoteandroid.ListRemoteAndroidInfo.DiscoverListener;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.Application;
import org.remoteandroid.NetworkTools;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.SupportActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

public class DiscoverFragment extends AbstractBodyFragment implements OnItemClickListener, DiscoverListener
{
	View mViewer;
	TextView mText;
	ListView mList;
	RemoteAndroidManager mManager;
	ListRemoteAndroidInfo mListInfo;
	ListRemoteAndroidInfoAdapter mAdapter;
	
	
	@Override
	public void onAttach(SupportActivity activity)
	{
		super.onAttach(activity);
	}	

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		ConnectActivity activity=(ConnectActivity)getActivity();
		activity.setProgressBarIndeterminateVisibility(Boolean.TRUE); // Important: Use Boolean value !
		mViewer = (View) inflater.inflate(R.layout.connect_discover, container, false);
		mText = (TextView)mViewer.findViewById(R.id.connect_help);
		mList = (ListView)mViewer.findViewById(R.id.connect_discover_list);
		mList.setOnItemClickListener(this);
		mManager=RemoteAndroidManager.getManager(getActivity());
		mListInfo=mManager.newDiscoveredAndroid(this);
		mAdapter=new ListRemoteAndroidInfoAdapter(getActivity().getApplicationContext(),
				mListInfo);
		for (RemoteAndroidInfo inf:Trusted.getBonded())
		{
			RemoteAndroidInfoImpl info=(RemoteAndroidInfoImpl)inf;
			info.clearDiscover();
			mListInfo.add(info);
		}
		
		mAdapter.setListener(this);
		mList.setAdapter(mAdapter);
		onUpdateActiveNetwork();
		return mViewer;
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		ConnectActivity activity=(ConnectActivity)getActivity();
		RemoteAndroidInfo info=mAdapter.getItem(position);
		activity.tryConnect(null, Arrays.asList(info.getUris()), true);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		ConnectActivity activity=(ConnectActivity)getActivity();
		if (activity==null)
			return;
		int active=activity.getActiveNetwork();
		//TODO: ACTIVE_PHONE_DATA lors du NAT traversal
		if ((active & NetworkTools.ACTIVE_NOAIRPLANE|NetworkTools.ACTIVE_BLUETOOTH|NetworkTools.ACTIVE_LOCAL_NETWORK)!=0)
			mListInfo.start(RemoteAndroidManager.FLAG_ACCEPT_ANONYMOUS,RemoteAndroidManager.DISCOVER_BEST_EFFORT);
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		mListInfo.cancel();
		ConnectActivity activity=(ConnectActivity)getActivity();
		if (activity!=null)
			activity.setProgressBarIndeterminateVisibility(Boolean.FALSE);

	}
	
	protected void onUpdateActiveNetwork()
	{
		ConnectActivity activity=(ConnectActivity)getActivity();
		if (activity==null)
			return;
		int active=activity.getActiveNetwork();
		if ((active & (NetworkTools.ACTIVE_BLUETOOTH|NetworkTools.ACTIVE_LOCAL_NETWORK))!=0)
		{
			if (!Application.sDiscover.isDiscovering())
			{
				mListInfo.start(NetworkTools.ACTIVE_NOAIRPLANE|RemoteAndroidManager.FLAG_ACCEPT_ANONYMOUS,RemoteAndroidManager.DISCOVER_BEST_EFFORT);
			}
			setEnabled(true);
		}
		else
		{
			setEnabled(false);
			mListInfo.cancel();
		}
	}
	
	private void setEnabled(boolean enabled)
	{
		mViewer.setEnabled(enabled);
		mText.setEnabled(enabled);
		mList.setEnabled(enabled);
		mAdapter.notifyDataSetChanged();
	}
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		try
		{
			if (mListInfo!=null)
				mListInfo.close();
		}
		catch (IOException e)
		{
			if (W) Log.w(TAG_CONNECT,"Error when close discover list info ("+e.getMessage()+")");
		}
	}
	
	@Override
	public void onDiscoverStart()
	{
		ConnectActivity activity=(ConnectActivity)getActivity();
		if (activity==null) return;
		activity.setProgressBarIndeterminateVisibility(Boolean.TRUE);
	}

	@Override
	public void onDiscoverStop()
	{
		ConnectActivity activity=(ConnectActivity)getActivity();
		if (activity==null) return;
		activity.setProgressBarIndeterminateVisibility(Boolean.FALSE);
	}
	
	@Override
	public void onDiscover(RemoteAndroidInfo remoteAndroidInfo, boolean update)
	{
	}
}
