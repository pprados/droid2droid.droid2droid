package org.remoteandroid.ui.connect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.*;
import org.remoteandroid.ListRemoteAndroidInfo;
import org.remoteandroid.ListRemoteAndroidInfo.DiscoverListener;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;

import android.app.Activity;
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

public class DiscoverFragment extends AbstractBodyFragment implements OnItemClickListener, DiscoverListener
{
	View mViewer;
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
		mList = (ListView)mViewer.findViewById(R.id.connect_discover_list);
		mList.setOnItemClickListener(this);
		mManager=RemoteAndroidManager.getManager(getActivity());
		mListInfo=mManager.newDiscoveredAndroid(this);
		mAdapter=new ListRemoteAndroidInfoAdapter(getActivity().getApplicationContext(),
				mListInfo);
		mAdapter.setListener(this);
		mList.setAdapter(mAdapter);
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
		mListInfo.start(RemoteAndroidManager.FLAG_ACCEPT_ANONYMOUS,RemoteAndroidManager.DISCOVER_BEST_EFFORT);
	}
	@Override
	public void onPause()
	{
		super.onPause();
		mListInfo.cancel();
	}
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		try
		{
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
		if (activity!=null)
			activity.setProgressBarIndeterminateVisibility(true);
	}

	@Override
	public void onDiscoverStop()
	{
		ConnectActivity activity=(ConnectActivity)getActivity();
		if (activity!=null)
			activity.setProgressBarIndeterminateVisibility(false);
	}

	@Override
	public void onDiscover(RemoteAndroidInfo remoteAndroidInfo, boolean update)
	{
	}
}
