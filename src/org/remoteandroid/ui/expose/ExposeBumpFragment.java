package org.remoteandroid.ui.expose;

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

public class ExposeBumpFragment extends AbstractBodyFragment
{
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET|FEATURE_LOCATION);
		}
		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			tabsAdapter.addTab(actionBar.newTab()
		        .setText(R.string.expose_bump), ExposeBumpFragment.class, null);
		}
	}	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.expose_bump, container, false);
	}
}
