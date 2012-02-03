package org.remoteandroid.ui;

import org.remoteandroid.ui.TabsAdapter;

import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;

// TODO: meme classe que Expose ?
public abstract class FeatureTab
{
	public long mFeature;

	protected FeatureTab(long feature)
	{
		mFeature=feature;
	}
	public abstract void createTab(FragmentActivity activity,TabsAdapter tabsAdapter, ActionBar actionBar);

}
