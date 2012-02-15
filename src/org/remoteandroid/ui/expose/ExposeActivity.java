package org.remoteandroid.ui.expose;

import org.remoteandroid.R;
import org.remoteandroid.ui.AbstractFeatureTabActivity;
import org.remoteandroid.ui.FeatureTab;

import android.os.Bundle;


public class ExposeActivity extends AbstractFeatureTabActivity
{
	private static final FeatureTab[] sTabs=
	{
		new ExposeQRCodeFragment.Provider(),
//		new ExposeSoundFragment.Provider(),
//		new ExposeWifiDirectFragment.Provider(),
//		new ExposeNFCFragment.Provider(),
//		new ExposeBumpFragment.Provider(),
		new ExposeTicketFragment.Provider()
	};

	@Override
	protected FeatureTab[] getFeatureTabs()
	{
		return sTabs;
	}
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
    	setTitle(R.string.expose);

	}
}
