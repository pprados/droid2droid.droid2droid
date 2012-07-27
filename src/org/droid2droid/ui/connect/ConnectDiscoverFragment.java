/******************************************************************************
 *
 * droid2droid - Distributed Android Framework
 * ==========================================
 *
 * Copyright (C) 2012 by Atos (http://www.http://atos.net)
 * http://www.droid2droid.org
 *
 ******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
******************************************************************************/
package org.droid2droid.ui.connect;

import static org.droid2droid.Constants.TAG_DISCOVERY;
import static org.droid2droid.Droid2DroidManager.DISCOVER_BEST_EFFORT;
import static org.droid2droid.Droid2DroidManager.FLAG_ACCEPT_ANONYMOUS;
import static org.droid2droid.Droid2DroidManager.FLAG_PROPOSE_PAIRING;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_NET;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.droid2droid.internal.Constants.D;

import java.util.ArrayList;
import java.util.List;

import org.droid2droid.R;
import org.droid2droid.RAApplication;
import org.droid2droid.RemoteAndroidInfo;
import org.droid2droid.discovery.Discover;
import org.droid2droid.internal.NetworkTools;
import org.droid2droid.internal.RemoteAndroidInfoImpl;
import org.droid2droid.pairing.Trusted;
import org.droid2droid.ui.FeatureTab;
import org.droid2droid.ui.TabsAdapter;
import org.droid2droid.ui.connect.nfc.WriteNfcActivity;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

public final class ConnectDiscoverFragment extends AbstractConnectFragment 
implements OnItemClickListener, OnItemLongClickListener
{
	private View mViewer;

	private TextView mUsage;

	private ListView mList;

	private List<RemoteAndroidInfoImpl> mListInfo;

	private ListRemoteAndroidInfoAdapter mAdapter;

	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN | FEATURE_NET);
		}

		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			Tab tab=actionBar.newTab()
					.setIcon(R.drawable.ic_tab_discover)
					.setText(R.string.connect_discover);
			tabsAdapter.addTab(
				tab, ConnectDiscoverFragment.class, null);
		}
	}

	public interface Filter
	{
		public boolean filter(RemoteAndroidInfo info);
	}

	public class ListRemoteAndroidInfoAdapter extends BaseAdapter implements Discover.Listener
	{
		private final List<RemoteAndroidInfoImpl> mListInfo;

		private final Context mContext;

		private int mColorText_nodisable;

		private int mColorText;

		public ListRemoteAndroidInfoAdapter(Context context, List<RemoteAndroidInfoImpl> listInfo)
		{
			Resources resource = context.getResources();
			mContext = context;
			//TODO: It's better to use Theme and obtainStyledAttributes. See source code of TextView
			if (getConnectActivity().isLight())
			{
				mColorText_nodisable = resource.getColor(android.R.color.primary_text_light_nodisable);
				mColorText = resource.getColor(android.R.color.tertiary_text_light);
			}
			else
			{
				mColorText_nodisable = resource.getColor(android.R.color.primary_text_dark_nodisable);
				mColorText = resource.getColor(android.R.color.tertiary_text_dark);
			}
			mListInfo = listInfo;
		}

		@Override
		public int getCount()
		{
			return mListInfo.size();
		}

		@Override
		public RemoteAndroidInfo getItem(int position)
		{
			return mListInfo.get(position);
		}

		@Override
		public long getItemId(int position)
		{
			return mListInfo.get(
				position).hashCode();
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
			if (view == null)
			{
				final LayoutInflater inflater = (LayoutInflater) mContext
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = inflater.inflate(
					R.layout.discover_device, parent, false);
				tag = new Tag();
				tag.mText1 = ((TextView) view.findViewById(android.R.id.text1));
				tag.mText2 = ((TextView) view.findViewById(android.R.id.text2));
				view.setTag(tag);
			}
			else
				tag = (Tag) view.getTag();
			RemoteAndroidInfoImpl info = mListInfo.get(position);
			tag.mText1.setText(info.getName());
			if (tag.mText2 != null)
			{
				StringBuilder b = new StringBuilder("( ")
						.append(RAApplication.getTechnologies(info, false));
						
				if (info.isBound())
					b.append(", ").append(mContext.getString(R.string.connect_device_paired));
				b.append(')');
				tag.mText2.setText(b);
			}
			boolean enabled = parent.isEnabled() && info.isDiscover();
			tag.mText1.setTextColor(enabled ? mColorText_nodisable : mColorText);
			tag.mText2.setTextColor(enabled ? mColorText_nodisable : mColorText);
			return view;
		}

		@Override
		public void onDiscoverStart()
		{
			setProgressBarIndeterminateVisibility(true);
			progress(true);
		}

		@Override
		public void onDiscoverStop()
		{
			setProgressBarIndeterminateVisibility(false);
			progress(false);
		}

		@Override
		public void onDiscover(final RemoteAndroidInfoImpl remoteAndroidInfo)
		{
			int flags=getConnectActivity().mFlags;
			if (!remoteAndroidInfo.isBonded && (flags & (FLAG_ACCEPT_ANONYMOUS|FLAG_PROPOSE_PAIRING))==0)
			{
				if (D) Log.d(TAG_DISCOVERY,"Refuse "+remoteAndroidInfo);
				return;
			}
			for (int i = mListInfo.size() - 1; i >= 0; --i)
			{
				RemoteAndroidInfoImpl and = mListInfo.get(i);
				if (and.uuid.equals(remoteAndroidInfo.uuid))
				{
					and.merge(remoteAndroidInfo);
					notifyDataSetChanged();
					return;
				}
			}
			mListInfo.add(remoteAndroidInfo);
			notifyDataSetChanged();
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		setProgressBarIndeterminateVisibility(true);
		mViewer = inflater.inflate(
			R.layout.connect_discover, container, false);
		mUsage = (TextView) mViewer.findViewById(R.id.usage);
		mList = (ListView) mViewer.findViewById(R.id.connect_discover_list);
		mList.setOnItemClickListener(this);
		mList.setOnItemLongClickListener(this);
		mListInfo = new ArrayList<RemoteAndroidInfoImpl>();
		mAdapter = new ListRemoteAndroidInfoAdapter(getActivity().getApplicationContext(), mListInfo);
		for (RemoteAndroidInfo inf : Trusted.getBonded())
		{
			RemoteAndroidInfoImpl info = (RemoteAndroidInfoImpl) inf;
			info.clearDiscover();
			mListInfo.add(info);
		}

		mList.setAdapter(mAdapter);
		return mViewer;
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		RemoteAndroidInfo info = mAdapter.getItem(position);
		showConnect(
			info.getUris(), getConnectActivity().mFlags, null);
	}
	@Override 
    public void onCreateContextMenu(ContextMenu menu, View view,ContextMenuInfo menuInfo) 
	{ 
    } 
	
	@Override
	public void onResume()
	{
		super.onResume();
		ConnectActivity activity = (ConnectActivity) getActivity();
		final int active = activity.getActiveNetwork();
		// TODO: ACTIVE_PHONE_DATA lors du NAT traversal
		new AsyncTask<Void, Void, Void>()
		{
			@Override
			protected Void doInBackground(Void... params)
			{
				if ((active & NetworkTools.ACTIVE_NOAIRPLANE | NetworkTools.ACTIVE_BLUETOOTH | NetworkTools.ACTIVE_LOCAL_NETWORK) != 0)
				{
					Discover.getDiscover().startDiscover(
						FLAG_ACCEPT_ANONYMOUS, DISCOVER_BEST_EFFORT);
				}
				return null;
			}
		}.execute();
		Discover.getDiscover().registerListener(
			mAdapter);
	}

	@Override
	public void onPause()
	{
		super.onPause();
		Discover.getDiscover().cancelDiscover();
		setProgressBarIndeterminateVisibility(false);
		Discover.getDiscover().unregisterListener(
			mAdapter);
	}

	@Override
	protected void onUpdateActiveNetwork(int activeNetwork)
	{
		if (mListInfo == null)
			return;
		boolean airplane = Settings.System.getInt(
			getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
		if (airplane)
		{
			mUsage.setText(R.string.connect_discover_help_airplane);
			mList.setVisibility(View.GONE);
			progress(false);
		}
		else if ((activeNetwork & (NetworkTools.ACTIVE_BLUETOOTH | NetworkTools.ACTIVE_LOCAL_NETWORK)) != 0)
		{
			mUsage.setText(R.string.connect_discover_help);
			mList.setVisibility(View.VISIBLE);
			mList.setEnabled(true);
		}
		else
		{
			mUsage.setText(R.string.connect_discover_help_wifi_or_bt);
			Discover.getDiscover().cancelDiscover();
			mList.setVisibility(View.GONE);
			mList.setEnabled(false);
			progress(false);
		}
	}

	@Override
	public void onPageSelected()
	{
		super.onPageSelected();
		startScan();
	}
	@Override
	public void onPageReSelected()
	{
		super.onPageReSelected();
		startScan();
	}


	private void startScan()
	{
		if (RAApplication.sDiscover.isDiscovering())
			setProgressBarIndeterminateVisibility(true);
		if (mListInfo != null && !RAApplication.sDiscover.isDiscovering())
		{
			Discover.getDiscover().startDiscover(
				FLAG_ACCEPT_ANONYMOUS, DISCOVER_BEST_EFFORT);
		}
	}
	@Override
	public void onPageUnselected()
	{
		super.onPageUnselected();
		setProgressBarIndeterminateVisibility(false);
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	private void progress(boolean onoff)
	{
		if (onoff)
		{
			getActivity().setProgressBarIndeterminate(
				true);
		}
		else
		{
			getActivity().setProgressBarIndeterminate(
				false);
		}
	}
	// A long clic to write a tag with the selected Remote Android Info.
	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
	{
		if ((getConnectActivity().mBroadcast) && (getConnectActivity().getActiveNetwork() & NetworkTools.ACTIVE_NFC)!=0)
		{
			startActivity(new Intent(getConnectActivity(),WriteNfcActivity.class)
				.putExtra(WriteNfcActivity.EXTRA_INFO, mAdapter.getItem(position)));
		}
		return true;
	}

}
