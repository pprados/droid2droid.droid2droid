package org.remoteandroid.ui;

import com.actionbarsherlock.app.ActionBar;

// TODO: meme classe que Expose ?
public abstract class FeatureTab
{
	public long mFeature;

	protected FeatureTab(long feature)
	{
		mFeature=feature;
	}
	public abstract void createTab(TabsAdapter tabsAdapter, ActionBar actionBar);

}
