package org.remoteandroid.ui.expose;

import static org.remoteandroid.RemoteAndroidInfo.FEATURE_ACCELEROMETER;
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

public class ExposeArroundFragment extends AbstractBodyFragment
{
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_ACCELEROMETER|FEATURE_NET);
		}

		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			Tab tab=actionBar.newTab()
			        .setText(R.string.expose_arround);
			tabsAdapter.addTab(tab, ExposeArroundFragment.class, null);
		}
	}	
	public ExposeArroundFragment()
	{
		
	}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.expose_arround, container, false);
	}
}
