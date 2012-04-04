package org.remoteandroid.ui.expose;

import static org.remoteandroid.RemoteAndroidInfo.FEATURE_ACCELEROMETER;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_LOCATION;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;

import org.remoteandroid.R;
import org.remoteandroid.ui.AbstractBodyFragment;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

public final class ExposeBumpFragment extends AbstractBodyFragment
{
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET|FEATURE_LOCATION|FEATURE_ACCELEROMETER);
		}
		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			Tab tab=actionBar.newTab()
					.setIcon(R.drawable.ic_tab_bump)
			        .setText(R.string.expose_bump);
			tabsAdapter.addTab(tab, ExposeBumpFragment.class, null);
		}
	}	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.expose_bump, container, false);
	}
}
