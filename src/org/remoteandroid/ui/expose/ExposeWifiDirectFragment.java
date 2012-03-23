package org.remoteandroid.ui.expose;

import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_WIFI_DIRECT;

import org.remoteandroid.R;
import org.remoteandroid.ui.AbstractBodyFragment;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.actionbarsherlock.app.ActionBar;

public class ExposeWifiDirectFragment extends AbstractBodyFragment
{
	protected static final String KEY_WIFI_DIRECT="WifiDirect";
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET|FEATURE_WIFI_DIRECT);
		}
		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			tabsAdapter.addTab(actionBar.newTab()
				.setIcon(R.drawable.ic_tab_wifi_direct)
		        .setText(R.string.expose_wifi_direct), ExposeWifiDirectFragment.class, null);
		}
	}	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.expose_wifi_direct, container, false);
	}
}
