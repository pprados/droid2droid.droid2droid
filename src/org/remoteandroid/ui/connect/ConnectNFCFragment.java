package org.remoteandroid.ui.connect;

import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NFC;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;

import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;

import android.annotation.TargetApi;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

@TargetApi(9)
public final class ConnectNFCFragment extends AbstractConnectFragment
{
	private View mViewer;
	private TextView mUsage;
	
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET|FEATURE_NFC);
		}
		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			Tab tab=actionBar.newTab()
					.setIcon(R.drawable.ic_tab_nfc)
			        .setText(R.string.connect_nfc);
			tabsAdapter.addTab(tab, ConnectNFCFragment.class, null);
		}
	}	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer = inflater.inflate(R.layout.connect_nfc, container, false);
		mUsage = (TextView)mViewer.findViewById(R.id.usage);
		return mViewer;
	}
	
	@Override
	protected void updateStatus(int activeNetwork)
	{
		if (mUsage==null)
			return;
		boolean airplane=Settings.System.getInt(getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
		if (airplane)
		{
			mUsage.setText(R.string.connect_nfc_help_airplane);
		}
		else
		if ((activeNetwork & (NetworkTools.ACTIVE_NFC))!=0)
		{
			mUsage.setText(R.string.connect_nfc_help);
		}
		else
		{
			mUsage.setText(R.string.connect_nfc_help_nfc);
		}
	}
	private RemoteAndroidInfo mPendingInfo;
	
	@Override
	public void onDiscover(RemoteAndroidInfoImpl info)
	{
		if (info.isDiscoverByNFC)
		{
			if (getConnectActivity()==null)
			{
				mPendingInfo=info;
			}
			else
				showConnect(info.getUris(),getConnectActivity().mFlags,null);
		}
	}
	@Override
	public void onResume()
	{
		super.onResume();
		if (mPendingInfo!=null)
		{
			showConnect(mPendingInfo.getUris(),getConnectActivity().mFlags,null);
			mPendingInfo=null;
		}
	}
	
}
