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
package org.droid2droid.ui.connect;

import static org.droid2droid.RemoteAndroidInfo.FEATURE_NET;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_NFC;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_SCREEN;

import org.droid2droid.R;
import org.droid2droid.RemoteAndroidInfo;
import org.droid2droid.internal.NetworkTools;
import org.droid2droid.internal.RemoteAndroidInfoImpl;
import org.droid2droid.ui.FeatureTab;
import org.droid2droid.ui.TabsAdapter;

import android.annotation.TargetApi;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

@TargetApi(9)
public final class ConnectNFCFragment extends AbstractConnectFragment
{
	private View mViewer;
	private TextView mUsage;
	
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET|FEATURE_NFC);
		}
		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			Tab tab=actionBar.newTab()
					.setIcon(R.drawable.ic_tab_nfc)
			        .setText(R.string.connect_nfc);
			tabsAdapter.addTab(tab, ConnectNFCFragment.class, null);
		}
	}	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer = inflater.inflate(R.layout.connect_nfc, container, false);
		mUsage = (TextView)mViewer.findViewById(R.id.usage);
		return mViewer;
	}
	
	@Override
	protected void updateStatus(int activeNetwork)
	{
		if (mUsage==null)
			return;
		boolean airplane=Settings.System.getInt(getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
		if (airplane)
		{
			mUsage.setText(R.string.connect_nfc_help_airplane);
		}
		else
		if ((activeNetwork & (NetworkTools.ACTIVE_NFC))!=0)
		{
			mUsage.setText(R.string.connect_nfc_help);
		}
		else
		{
			mUsage.setText(R.string.connect_nfc_help_nfc);
		}
	}
	private RemoteAndroidInfo mPendingInfo;
	
	@Override
	public void onDiscover(RemoteAndroidInfoImpl info)
	{
		if (info.isDiscoverByNFC)
		{
			if (getConnectActivity()==null)
			{
				mPendingInfo=info;
			}
			else
				showConnect(info.getUris(),getConnectActivity().mFlags,null);
		}
	}
	@Override
	public void onResume()
	{
		super.onResume();
		if (mPendingInfo!=null)
		{
			showConnect(mPendingInfo.getUris(),getConnectActivity().mFlags,null);
			mPendingInfo=null;
		}
	}
}
