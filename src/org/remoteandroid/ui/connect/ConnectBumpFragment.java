package org.remoteandroid.ui.connect;

import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;

import org.remoteandroid.R;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;

import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;

public class ConnectBumpFragment extends AbstractConnectFragment
{
	private View mViewer;
	private TextView mUsage;
	
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET);
		}
		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			tabsAdapter.addTab(actionBar.newTab()
		        .setText(R.string.connect_bump), ConnectBumpFragment.class, null);
		}
	}	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer = (View) inflater.inflate(R.layout.connect_discover, container, false);
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
			mUsage.setText(R.string.connect_ticket_help_airplane);
		}
		else
		if ((activeNetwork & (NetworkTools.ACTIVE_BLUETOOTH|NetworkTools.ACTIVE_LOCAL_NETWORK))!=0)
		{
			mUsage.setText(R.string.connect_ticket_help);
		}
		else
		{
			mUsage.setText(R.string.connect_ticket_help_internet);
		}
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}
	
}
