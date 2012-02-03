package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.remoteandroid.Application;
import org.remoteandroid.ListRemoteAndroidInfo;
import org.remoteandroid.ListRemoteAndroidInfo.DiscoverListener;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;
import org.remoteandroid.ui.expose.ExposeBumpFragment;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ConnectDiscoverFragment extends AbstractConnectFragment implements OnItemClickListener, DiscoverListener
{
	protected static final String KEY_DISCOVER="Discover";
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET);
		}
		@Override
		public void createTab(FragmentActivity activity,TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			tabsAdapter.addTab(actionBar.newTab()
		        .setText(R.string.expose_bump), ExposeBumpFragment.class, null);
		}
	}	
	public interface Filter
	{
		public boolean filter(RemoteAndroidInfo info);
	}
	public class ListRemoteAndroidInfoAdapter extends BaseAdapter implements DiscoverListener
	{
		private ListRemoteAndroidInfo mListInfo;
		private List<RemoteAndroidInfo> mListFilter;
		private Context mContext;
		private Filter mFilter;
		private int mResId;
		private DiscoverListener mListener;
		private int mColorTextDark_nodisable;
		private int mColorTextDark;

		public ListRemoteAndroidInfoAdapter(Context context,ListRemoteAndroidInfo listInfo)
		{
			this(context,listInfo,R.layout.discover_device);
		}
		public ListRemoteAndroidInfoAdapter(Context context,ListRemoteAndroidInfo listInfo,int resid)
		{
			this(context,listInfo,resid,null);
		}
		public ListRemoteAndroidInfoAdapter(Context context,ListRemoteAndroidInfo listInfo,Filter filter)
		{
			this(context,listInfo,R.layout.discover_device,filter);
		}
		public ListRemoteAndroidInfoAdapter(Context context,ListRemoteAndroidInfo listInfo,int resid,Filter filter)
		{
			Resources resource=context.getResources();
			mContext=context;
			mColorTextDark_nodisable=resource.getColor(android.R.color.primary_text_dark_nodisable); 
			mColorTextDark=resource.getColor(android.R.color.tertiary_text_dark);
			mListInfo=listInfo;
			mFilter=filter;
			mResId=resid;
			
			if (filter==null)
			{
				mListFilter=mListInfo;
			}
			else
			{
				mListFilter=new ArrayList<RemoteAndroidInfo>();
				for (int i=0;i<mListInfo.size();++i)
				{
					RemoteAndroidInfo info=mListInfo.get(i);
					if (mFilter.filter(info))
					{
						mListFilter.add(info);
					}
				}
			}
			listInfo.setListener(this);
		}

		public void setListener(DiscoverListener listener)
		{
			mListener=listener;
		}
		@Override
		public int getCount()
		{
			return mListFilter.size();
		}

		@Override
		public RemoteAndroidInfo getItem(int position)
		{
			return mListFilter.get(position);
		}

		@Override
		public long getItemId(int position)
		{
			return mListFilter.get(position).hashCode();
		}

		class Tag
		{
			TextView mText1;
			TextView mText2;
		}
		@Override
		public View getView(int position, View view, ViewGroup parent)
		{
			Tag tag;
			if (view==null)
			{
				final LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = inflater.inflate(mResId, parent, false);
				tag=new Tag();
				tag.mText1=((TextView)view.findViewById(android.R.id.text1));
				tag.mText2=((TextView)view.findViewById(android.R.id.text2));
				view.setTag(tag);
			}
			else
				tag=(Tag)view.getTag();
			RemoteAndroidInfoImpl info=(RemoteAndroidInfoImpl)mListFilter.get(position);
			tag.mText1.setText(info.getName());
			if (tag.mText2!=null)
			{
				StringBuilder b=new StringBuilder(Application.getTechnologies(info, false));
				if (info.isDiscover()) b.append(' ').append( mContext.getString(R.string.connect_device_paired));
				tag.mText2.setText(b);
			}
			boolean enabled=parent.isEnabled();
			tag.mText1.setTextColor(enabled 
					? mColorTextDark_nodisable 
					: mColorTextDark);
			tag.mText2.setTextColor(enabled 
					? mColorTextDark_nodisable 
					: mColorTextDark);
			return view;
		}
		
		@Override
		public void onDiscoverStart()
		{
			if (mListener!=null)
				mListener.onDiscoverStart();
		}
		@Override
		public void onDiscoverStop()
		{
			if (mListener!=null)
				mListener.onDiscoverStop();
		}
		@Override
		public void onDiscover(RemoteAndroidInfo remoteAndroidInfo, boolean update)
		{
			notifyDataSetChanged();
			if (mFilter!=null)
			{
				for (int i=0;i<mListFilter.size();++i)
				{
					RemoteAndroidInfo info=mListFilter.get(i);
					if (info.getUuid().equals(remoteAndroidInfo.getUuid()))
					{
						if (!mFilter.filter(info))
						{
							// The change exclude the previous record
							mListFilter.remove(info);
							return;
						}
					}
				}
				// Unknown record
				if (mFilter.filter(remoteAndroidInfo))
				{
					// Add new record
					mListFilter.add(remoteAndroidInfo);
				}
				else
				{
					Log.d(TAG_DISCOVERY,PREFIX_LOG+" reject "+remoteAndroidInfo);
				}
			}
			if (mListener!=null)
				mListener.onDiscover(remoteAndroidInfo, update);
		}
	}
	
	private View mViewer;
	private TextView mText;
	private ListView mList;
	private RemoteAndroidManager mManager;
	private ListRemoteAndroidInfo mListInfo;
	private ListRemoteAndroidInfoAdapter mAdapter;
	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		setProgressBarIndeterminateVisibility(true);
		mViewer = (View) inflater.inflate(R.layout.connect_discover, container, false);
		mText = (TextView)mViewer.findViewById(R.id.connect_help);
		mList = (ListView)mViewer.findViewById(R.id.connect_discover_list);
		mList.setOnItemClickListener(this);
		mManager=Application.getManager();
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
		RemoteAndroidInfo info=mAdapter.getItem(position);
		tryConnect(null, Arrays.asList(info.getUris()), true);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		int active=getActiveNetwork();
		//TODO: ACTIVE_PHONE_DATA lors du NAT traversal
		if ((active & NetworkTools.ACTIVE_NOAIRPLANE|NetworkTools.ACTIVE_BLUETOOTH|NetworkTools.ACTIVE_LOCAL_NETWORK)!=0)
			mListInfo.start(RemoteAndroidManager.FLAG_ACCEPT_ANONYMOUS,RemoteAndroidManager.DISCOVER_BEST_EFFORT);
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		mListInfo.cancel();
		setProgressBarIndeterminateVisibility(false);
	}
	
	protected void onUpdateActiveNetwork()
	{
		int active=getActiveNetwork();
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
		if (mListInfo!=null)
		{
			mListInfo.close();
			if (D) mListInfo=null;
		}
		if (mManager!=null)
		{
			mManager.close();
			if (D) mManager=null;
		}
	}
	
	@Override
	public void onDiscoverStart()
	{
		setProgressBarIndeterminateVisibility(true);
	}

	@Override
	public void onDiscoverStop()
	{
		setProgressBarIndeterminateVisibility(false);
	}
	
	@Override
	public void onDiscover(RemoteAndroidInfo remoteAndroidInfo, boolean update)
	{
	}
}
