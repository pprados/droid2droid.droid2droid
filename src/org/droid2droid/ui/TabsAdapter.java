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

import java.util.ArrayList;

import org.droid2droid.RAApplication;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

/**
 * This is a helper class that implements the management of tabs and all details
 * of connecting a ViewPager with associated TabHost. It relies on a trick.
 * Normally a tab host has a simple API for supplying a View or Intent that each
 * tab will show. This is not sufficient for switching between pages. So instead
 * we make the content part of the tab host 0dp high (it is not shown) and the
 * TabsAdapter supplies its own dummy view to show as the tab content. It
 * listens to changes in tabs, and takes care of switch to the correct paged in
 * the ViewPager whenever the selected tab changes.
 */
public final class TabsAdapter extends FragmentPagerAdapter implements ViewPager.OnPageChangeListener, ActionBar.TabListener
{
	private final FragmentActivity mContext;

	private final ActionBar mActionBar;

	private final ViewPager mViewPager;
	
	private static final class TabInfo
	{
		private final Class<?> mClss;

		private final Bundle mArgs;
		AbstractBodyFragment mFragment;

		TabInfo(Class<?> clazz, Bundle args)
		{
			mClss = clazz;
			mArgs = args;
		}
	}
	private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

	public TabsAdapter(FragmentActivity activity, ActionBar actionBar, ViewPager pager)
	{
		super(activity.getSupportFragmentManager());
		mContext = activity;
		mActionBar = actionBar;
		mViewPager = pager;
		mViewPager.setAdapter(this);
		mViewPager.setOnPageChangeListener(this);
	}

	public void addTab(ActionBar.Tab tab, Class<?> clss, Bundle args)
	{
		TabInfo info = new TabInfo(clss, args);
		tab.setTag(info);
		tab.setTabListener(this);
		mTabs.add(info);
		if (mActionBar!=null)
			mActionBar.addTab(tab.setTabListener(this));
		notifyDataSetChanged();
	}

	@Override
	public int getCount()
	{
		return mTabs.size();
	}

	@Override
	public Fragment getItem(int position)
	{
		TabInfo info = mTabs.get(position);
		if (info.mFragment==null)
		{
			info.mFragment=(AbstractBodyFragment)Fragment.instantiate(mContext, info.mClss.getName(), info.mArgs);
		}
		return info.mFragment;
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels)
	{
	}

	@Override
	public void onPageSelected(int position)
	{
		if (mActionBar!=null)
			mActionBar.setSelectedNavigationItem(position); // onPageSelected() invoked by side effect
	}

	@Override
	public void onPageScrollStateChanged(int state)
	{
	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction fft)
	{
		RAApplication.hideSoftKeyboard(mContext);
		mContext.setProgressBarIndeterminateVisibility(false);
		FragmentTransaction ft = fft;
		if (ft == null)
			ft = mContext.getSupportFragmentManager().beginTransaction();
		mViewPager.setCurrentItem(tab.getPosition());
		if (fft == null)
			ft.commit();
		((AbstractBodyFragment)getItem(tab.getPosition())).onPageSelected();
	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft)
	{
		((AbstractBodyFragment)getItem(tab.getPosition())).onPageReSelected();
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft)
	{
		((AbstractBodyFragment)getItem(tab.getPosition())).onPageUnselected();
	}
	
	final AbstractBodyFragment getActiveFragment()
	{
		return mTabs.get(mViewPager.getCurrentItem()).mFragment;
	}
}