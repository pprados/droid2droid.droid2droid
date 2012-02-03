package org.remoteandroid.ui;

import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.W;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.ui.AbstractNetworkEventActivity;
import org.remoteandroid.ui.MainFragment;
import org.remoteandroid.ui.MainActivity;
import org.remoteandroid.ui.TabsAdapter;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.ActionBar.Tab;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.support.v4.view.ViewPager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuInflater;


public abstract class AbstractFeatureTabActivity extends AbstractNetworkEventActivity
{
	
	
    ViewPager  mViewPager;
    TabsAdapter mTabsAdapter;
	FragmentManager mFragmentManager;
	Fragment mFragment;
    
	protected abstract FeatureTab[] getFeatureTabs();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);

    	mFragmentManager = getSupportFragmentManager();
    	mFragment = (MainFragment) mFragmentManager.findFragmentById(R.id.fragment);

    	mViewPager = new ViewPager(this);
        mViewPager.setId(R.id.body);
        setContentView(mViewPager);
    	// setup action bar for tabs
    	setTitle(R.string.expose);
    	
        final ActionBar actionBar = getSupportActionBar();
    	actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setDisplayShowTitleEnabled(true);
        
        mTabsAdapter = new TabsAdapter(this, getSupportActionBar(), mViewPager);
        FeatureTab[] featureTabs=getFeatureTabs();
    	for (int i=0;i<featureTabs.length;++i)
    	{
    		if ((featureTabs[i].mFeature & Application.sFeature) == featureTabs[i].mFeature)
    		{
    			featureTabs[i].createTab(this,mTabsAdapter,actionBar);
    		}
    	}
        if (savedInstanceState != null) 
        {
        	getSupportActionBar().setSelectedNavigationItem(savedInstanceState.getInt("index"));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) 
    {
        super.onSaveInstanceState(outState);
        outState.putInt("index", getSupportActionBar().getSelectedNavigationIndex());
    }
	
    @Override
    protected void onResume()
    {
    	super.onResume();
    	Application.hideSoftKeyboard(this);
    }
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_fragment_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				// app icon in action bar clicked; go home
				Intent intent = new Intent(this, MainActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	@Override
	protected AbstractNetworkEventFragment getActiveFragment()
	{
		// TODO Auto-generated method stub
		return null;
	}	
}
