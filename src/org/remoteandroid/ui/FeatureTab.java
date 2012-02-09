package org.remoteandroid.ui;

import org.remoteandroid.ui.connect.AbstractConnectFragment;

import android.support.v4.app.ActionBar;

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
