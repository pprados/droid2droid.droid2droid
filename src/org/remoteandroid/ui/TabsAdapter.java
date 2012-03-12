package org.remoteandroid.ui;

import static org.remoteandroid.internal.Constants.V;

import java.util.ArrayList;

import org.remoteandroid.Application;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.util.Log;

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
public class TabsAdapter extends FragmentPagerAdapter implements ViewPager.OnPageChangeListener, ActionBar.TabListener
{
	private final FragmentActivity mContext;

	private final ActionBar mActionBar;

	private final ViewPager mViewPager;
	
	static final class TabInfo
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
		if (V) Log.v("Frag","onPageSelected("+position+")...");
		mActionBar.setSelectedNavigationItem(position);
		mTabs.get(position).mFragment.onPageSelected();
		if (V) Log.v("Frag","onPageSelected("+position+") done");
	}

	@Override
	public void onPageScrollStateChanged(int state)
	{
	}

	@Override
	public void onTabSelected(Tab tab, FragmentTransaction fft)
	{
		if (V) Log.v("Frag","onTabSelected()...");
		Application.hideSoftKeyboard(mContext);
		mContext.setProgressBarIndeterminateVisibility(false);
		FragmentTransaction ft = fft;
		if (ft == null)
			ft = mContext.getSupportFragmentManager().beginTransaction();
		mViewPager.setCurrentItem(tab.getPosition());
		if (fft == null)
			ft.commit();
		((AbstractBodyFragment)getItem(mViewPager.getCurrentItem())).onPageSelected();
		if (V) Log.v("Frag","onTabSelected() done");
	}

	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft)
	{
	}

	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft)
	{
		((AbstractBodyFragment)getItem(mViewPager.getCurrentItem())).onPageUnselected();
	}
	
	final AbstractBodyFragment getActiveFragment()
	{
		return mTabs.get(mViewPager.getCurrentItem()).mFragment;
	}
}