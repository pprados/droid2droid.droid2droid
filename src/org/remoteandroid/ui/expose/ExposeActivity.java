package org.remoteandroid.ui.expose;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.ui.AbstractFeatureTabActivity;
import org.remoteandroid.ui.AbstractNetworkEventActivity;
import org.remoteandroid.ui.AbstractNetworkEventFragment;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.MainActivity;
import org.remoteandroid.ui.MainFragment;
import org.remoteandroid.ui.TabsAdapter;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.support.v4.view.ViewPager;
import android.view.MenuInflater;


public class ExposeActivity extends AbstractFeatureTabActivity
{
	public static final FeatureTab[] sTabs=
	{
		new ExposeQRCodeFragment.Provider(),
		new ExposeSMSFragment.Provider(),
		new ExposeSoundFragment.Provider(),
		new ExposeWifiDirectFragment.Provider(),
		new ExposeNFCFragment.Provider(),
		new ExposeBumpFragment.Provider(),
		new ExposeTicketFragment.Provider()
	};

	@Override
	protected FeatureTab[] getFeatureTabs()
	{
		return sTabs;
	}

}
