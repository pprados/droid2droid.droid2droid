package org.remoteandroid.ui.connect;

import org.remoteandroid.ListRemoteAndroidInfo;
import org.remoteandroid.ListRemoteAndroidInfo.DiscoverListener;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.ui.ListRemoteAndroidInfoAdapter;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.SupportActivity;
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
		mViewer = (View) inflater.inflate(R.layout.connect_discover, container, false);
		mList = (ListView)mViewer.findViewById(R.id.connect_discover_list);
		mList.setOnItemClickListener(this);
		mManager=RemoteAndroidManager.getManager(getActivity());
		mListInfo=mManager.newDiscoveredAndroid(null);
		mAdapter=new ListRemoteAndroidInfoAdapter(getActivity().getApplicationContext(),
				mListInfo);
		mAdapter.setListener(this);
		mList.setAdapter(mAdapter);
		return mViewer;
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onResume()
	{
		super.onResume();
		mListInfo.start(RemoteAndroidManager.DISCOVER_BEST_EFFORT);
	}
	@Override
	public void onPause()
	{
		super.onPause();
		mListInfo.cancel();
	}
	@Override
	public void onDiscoverStart()
	{
		/*FragmentActivity*/Activity activity=/*(FragmentActivity)FIXME*/getActivity();
		if (activity!=null)
			activity.setProgressBarIndeterminateVisibility(true);
	}

	@Override
	public void onDiscoverStop()
	{
		/*FragmentActivity*/Activity activity=/*(FragmentActivity)FIXME*/getActivity();
		if (activity!=null)
			activity.setProgressBarIndeterminateVisibility(false);
	}

	@Override
	public void onDiscover(RemoteAndroidInfo remoteAndroidInfo, boolean update)
	{
	}
}
