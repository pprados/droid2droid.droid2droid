/******************************************************************************
 *
 * droid2droid - Distributed Android Framework
 * ==========================================
 *
 * Copyright (C) 2012 by Atos (http://www.http://atos.net)
 * http://www.droid2droid.org
 *
 ******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
******************************************************************************/
package org.droid2droid.ui;

import org.droid2droid.R;
import org.droid2droid.RAApplication;
import org.droid2droid.internal.RemoteAndroidInfoImpl;
import org.droid2droid.ui.connect.ConnectNFCFragment;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;

public abstract class AbstractFeatureTabActivity extends AbstractNetworkEventActivity
{
	
	//protected RemoteAndroidNfcHelper mNfcIntegration; // FIXME
	
	protected ViewPager  		mViewPager;
	protected TabsAdapter 		mTabsAdapter;
	protected FragmentManager 	mFragmentManager;
	protected ActionBar 		mActionBar;
    
	protected abstract FeatureTab[] getFeatureTabs();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
		requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_CONTEXT_MENU);
        
        super.onCreate(savedInstanceState);

        //mNfcIntegration=new RemoteAndroidNfcHelperImpl(this);
        
    	mFragmentManager = getSupportFragmentManager();

    	mViewPager = new ViewPager(this);
        mViewPager.setId(R.id.body);
        setContentView(mViewPager);
    	
        mActionBar = getSupportActionBar();
        if (mActionBar!=null)
        {
	        mActionBar.setDisplayHomeAsUpEnabled(true);
	        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
//	        mActionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
	        mActionBar.setDisplayShowTitleEnabled(true);
        }        
        mTabsAdapter = new TabsAdapter(this, mActionBar, mViewPager);
        FeatureTab[] featureTabs=getFeatureTabs();
    	for (int i=0;i<featureTabs.length;++i)
    	{
    		if ((featureTabs[i].mFeature & RAApplication.sFeature) == featureTabs[i].mFeature)
    		{
    			featureTabs[i].createTab(mTabsAdapter,mActionBar);
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
    	RAApplication.hideSoftKeyboard(this);
//    	NfcUtils.onResume(this, mNfcIntegration);
    }
	@Override
	protected void onPause()
	{
		super.onPause();	// Register a listener when another device ask my tag
//		mNfcIntegration.onPause(this);
	}
    
	// Invoked when NFC tag detected
	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
//		mNfcIntegration.onNewIntent(this, intent);
	}

	public final TabsAdapter getTabsAdapter()
	{
		return mTabsAdapter;
	}
// ----------------------------------------
	public void onDiscover(RemoteAndroidInfoImpl info)
	{
		if (info.isDiscoverByNFC)
		{
			for (int i=0;i<mTabsAdapter.getCount();++i)
			{
				AbstractBodyFragment fragment=(AbstractBodyFragment)mTabsAdapter.getItem(i);
				if (fragment instanceof ConnectNFCFragment)
				{
					mTabsAdapter.onPageSelected(i);
					fragment.onDiscover(info);
				}
			}
		}
	}

    
//----------------------------------
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getSupportMenuInflater();
		// FIXME: Menu different en cas de connect ?
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
			case R.id.config:
				startActivity(new Intent(this, EditPreferenceActivity.class));
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	@Override
	protected AbstractBodyFragment getActiveFragment()
	{
		return mTabsAdapter.getActiveFragment();
	}	
}
