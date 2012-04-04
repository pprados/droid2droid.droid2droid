package org.remoteandroid.ui.connect;

import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;

import java.util.ArrayList;
import java.util.List;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.discovery.Discover;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

public final class ConnectDiscoverFragment extends AbstractConnectFragment implements OnItemClickListener
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
		private List<RemoteAndroidInfoImpl> mListInfo;

		private Context mContext;

		private int mColorTextDark_nodisable;

		private int mColorTextDark;

		public ListRemoteAndroidInfoAdapter(Context context, List<RemoteAndroidInfoImpl> listInfo)
		{
			Resources resource = context.getResources();
			mContext = context;
			if (getConnectActivity().isLight())
			{
				mColorTextDark_nodisable = resource.getColor(android.R.color.primary_text_light_nodisable);
				mColorTextDark = resource.getColor(android.R.color.tertiary_text_light);
			}
			else
			{
				mColorTextDark_nodisable = resource.getColor(android.R.color.primary_text_dark_nodisable);
				mColorTextDark = resource.getColor(android.R.color.tertiary_text_dark);
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
			RemoteAndroidInfoImpl info = (RemoteAndroidInfoImpl) mListInfo.get(position);
			tag.mText1.setText(info.getName());
			if (tag.mText2 != null)
			{
				StringBuilder b = new StringBuilder("( ")
						.append(Application.getTechnologies(info, false));
						
				if (info.isBonded())
					b.append(", ").append(mContext.getString(R.string.connect_device_paired));
				b.append(')');
				tag.mText2.setText(b);
			}
			boolean enabled = parent.isEnabled() && info.isDiscover();
			tag.mText1.setTextColor(enabled ? mColorTextDark_nodisable : mColorTextDark);
			tag.mText2.setTextColor(enabled ? mColorTextDark_nodisable : mColorTextDark);
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
		mViewer = (View) inflater.inflate(
			R.layout.connect_discover, container, false);
		mUsage = (TextView) mViewer.findViewById(R.id.usage);
		mList = (ListView) mViewer.findViewById(R.id.connect_discover_list);
		mList.setOnItemClickListener(this);
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
			info.getUris(), true, null); // FIXME: anonymous
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
						RemoteAndroidManager.FLAG_ACCEPT_ANONYMOUS, RemoteAndroidManager.DISCOVER_BEST_EFFORT);
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
	protected void updateStatus(int activeNetwork)
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
		if (Application.sDiscover.isDiscovering())
			setProgressBarIndeterminateVisibility(true);
		if (mListInfo != null && !Application.sDiscover.isDiscovering())
		{
			Discover.getDiscover().startDiscover(
				RemoteAndroidManager.FLAG_ACCEPT_ANONYMOUS, RemoteAndroidManager.DISCOVER_BEST_EFFORT);
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
}
