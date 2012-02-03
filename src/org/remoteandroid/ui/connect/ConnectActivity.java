package org.remoteandroid.ui.connect;

import org.remoteandroid.ui.AbstractFeatureTabActivity;
import org.remoteandroid.ui.FeatureTab;


public abstract class ConnectActivity extends AbstractFeatureTabActivity
{
	public static final FeatureTab[] sTabs=
		{
			new ConnectDiscoverFragment.Provider(),
		};	
	
	protected FeatureTab[] getFeatureTabs()
	{
		return sTabs;
	}
}
