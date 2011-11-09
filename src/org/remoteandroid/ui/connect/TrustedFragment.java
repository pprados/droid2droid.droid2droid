package org.remoteandroid.ui.connect;

import org.remoteandroid.ListRemoteAndroidInfo;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.ui.ListRemoteAndroidInfoAdapter;

import android.os.Bundle;
import android.support.v4.app.SupportActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

public class TrustedFragment extends AbstractBodyFragment implements OnItemClickListener
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
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mManager=RemoteAndroidManager.getManager(getActivity());
		mListInfo=mManager.getBoundedDevices();
		mAdapter=new ListRemoteAndroidInfoAdapter(getActivity().getApplicationContext(),
				mListInfo,
				new ListRemoteAndroidInfoAdapter.Filter()
				{
					
					@Override
					public boolean filter(RemoteAndroidInfo info)
					{
						RemoteAndroidInfoImpl i=(RemoteAndroidInfoImpl)info;
						return i.isDiscover() || i.isConnectableWithBluetooth();
					}
				});
	}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer =inflater.inflate(R.layout.connect_trusted, container, false);
		mList = (ListView)mViewer.findViewById(R.id.connect_bounded_list);
		mList.setOnItemClickListener(this);
		mList.setAdapter(mAdapter);
		return mViewer;
	}
	
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		// TODO Auto-generated method stub
		
	}

	
}
