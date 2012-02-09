package org.remoteandroid.ui.connect.old;

import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.ETHERNET;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_PREFERENCE;
import static org.remoteandroid.internal.Constants.V;

import java.util.ArrayList;
import java.util.List;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.ui.DevicePreference;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ListFragment;
import android.support.v4.app.SupportActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class TechnologiesFragment extends ListFragment
{
	private BroadcastReceiver mAirPlaine = new BroadcastReceiver() 
	{
	      @Override
	      public void onReceive(Context context, Intent intent) 
	      {
		        update();
	      }
	};
	private BroadcastReceiver mNetworkStateReceiver=new BroadcastReceiver() 
    {
		
        @Override
        public void onReceive(Context context, Intent intent) 
        {
        	update();
        }
    };
    private BroadcastReceiver mBluetoothReceiver=new BroadcastReceiver()
    {
		@Override
		public void onReceive(Context context, Intent intent)
		{
            if (W) Log.w(TAG_PREFERENCE, PREFIX_LOG+"BT Type Changed "+intent);
			if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))
			{
	        	update();
			}
			
		}
    };
    private long mActiveFeature;
    private long mActiveNetwork;
    
	interface Listener
	{
		public void onTechnologieSelected(Technology technology);
	}

	static class Cache
	{
		TextView mContent;
		TextView mContextDescription;
	}
	class TechArrayAdapter extends BaseAdapter
	{
		LayoutInflater mInflater; 
		TechArrayAdapter(Context context)
		{
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}
		public View getView(int position, View convertView, android.view.ViewGroup parent) 
		{
			Cache cache;
			if (convertView==null)
			{
//				convertView=mInflater.inflate(R.layout.connect_technology, parent,false);
//				cache=new Cache();
//				cache.mContent=(TextView)convertView.findViewById(R.id.connect);
//				cache.mContextDescription=(TextView)convertView.findViewById(R.id.connect_description);
//				convertView.setTag(cache);
			}
			else
				cache=(Cache)convertView.getTag();
			final Technology tech=mTechnologies[position+1];
//			cache.mContent.setText(tech.mContent);
//			cache.mContextDescription.setText(tech.mDescription);
//			boolean airplane=Settings.System.getInt(getActivity().getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
//			boolean active=
//					((mActiveFeature & tech.mFeature) == tech.mFeature)
//					&& ((mActiveNetwork & tech.mActiveNetwork)!=0);
//			cache.mContent.setEnabled(active);
//			cache.mContextDescription.setEnabled(active);
			return convertView;
		}
		@Override
		public int getCount()
		{
			if (mTechnologies==null)
				return 0;
			return mTechnologies.length-1;
		}
		@Override
		public Object getItem(int position)
		{
			return mTechnologies[position+1];
		}
		@Override
		public long getItemId(int position)
		{
			return position+1;
		}
		
	};
	
	private Technology[] mTechnologies; 
	private Listener	mListener;
	private TechArrayAdapter mAdapter;

	void setTechnologies(Technology[] technologies)
	{
		mTechnologies=technologies;
		if (mAdapter!=null)
			mAdapter.notifyDataSetChanged();
		
	}
	
	void update()
	{
		mActiveFeature=Application.getActiveFeature();
		mActiveNetwork=NetworkTools.getActiveNetwork(Application.sAppContext);
		mAdapter.notifyDataSetChanged();
	}
	@Override
	public void onAttach(SupportActivity activity)
	{
		try
		{
			super.onAttach(activity);
			mListener = (Listener) activity;
			if (mAdapter==null)
			{
				mAdapter=new TechArrayAdapter(activity.getApplicationContext());
				
			}
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString()
					+ " must implement OnTechnologieListener");
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
		// Register receiver
        getActivity().registerReceiver(mNetworkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		getActivity().registerReceiver(mAirPlaine,new IntentFilter("android.intent.action.SERVICE_STATE"));

		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.ECLAIR)
		{
			IntentFilter filter=new IntentFilter();
			filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
			getActivity().registerReceiver(mBluetoothReceiver, filter);
		}
	}
	@Override
	public void onPause()
	{
		super.onPause();
   		getActivity().unregisterReceiver(mNetworkStateReceiver);
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.ECLAIR)
			getActivity().unregisterReceiver(mBluetoothReceiver); 
   		getActivity().unregisterReceiver(mAirPlaine); 
	}
	@Override
	public void onDetach()
	{
		super.onDetach();
		mListener = null;
	}

	public void enabledPersistentSelection()
	{
		getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id)
	{
		
		if (mListener != null)
		{
			final Technology tech=mTechnologies[position+1];
			boolean active=
					((mActiveFeature & tech.mFeature) == tech.mFeature)
					&& ((mActiveNetwork & tech.mActiveNetwork)!=0);
			
			if (active) mListener.onTechnologieSelected(mTechnologies[position+1]);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setListAdapter(mAdapter);
	}
	
}
