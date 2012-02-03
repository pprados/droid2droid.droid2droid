package org.remoteandroid.ui;

import java.util.ArrayList;

import org.remoteandroid.Application;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ActionBar.Tab;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;

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
public class TabsAdapter extends FragmentPagerAdapter implements ViewPager.OnPageChangeListener, ActionBar.TabListener
{
	private final FragmentActivity mContext;

	private final ActionBar mActionBar;

	private final ViewPager mViewPager;

	static final class TabInfo
	{
		private final Class<?> clss;

		private final Bundle args;

		TabInfo(Class<?> _class, Bundle _args)
		{
			clss = _class;
			args = _args;
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
		return Fragment.instantiate(mContext, info.clss.getName(), info.args);
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels)
	{
//		hideSoftKeyboard();
	}

	@Override
	public void onPageSelected(int position)
	{
		mActionBar.setSelectedNavigationItem(position);
		int id=mViewPager.getCurrentItem();
		Log.d("topto",""+id);
	}

	@Override
	public void onPageScrollStateChanged(int state)
	{
//		hideSoftKeyboard();
	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction fft)
	{
		hideSoftKeyboard();
		FragmentTransaction ft = fft;
		if (ft == null)
			ft = mContext.getSupportFragmentManager().beginTransaction();
		mViewPager.setCurrentItem(tab.getPosition());
		if (fft == null)
			ft.commit();
	}

	private void hideSoftKeyboard()
	{
		Application.hideSoftKeyboard(mContext);
	}
	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft)
	{
//		hideSoftKeyboard();
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft)
	{
	}
}