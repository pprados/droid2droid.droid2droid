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
package org.droid2droid.ui.expose;

import static org.droid2droid.RemoteAndroidInfo.FEATURE_ACCELEROMETER;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_LOCATION;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_NET;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_SCREEN;

import org.droid2droid.R;
import org.droid2droid.ui.AbstractBodyFragment;
import org.droid2droid.ui.FeatureTab;
import org.droid2droid.ui.TabsAdapter;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

@Deprecated
public final class ExposeBumpFragment extends AbstractBodyFragment
{
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET|FEATURE_LOCATION|FEATURE_ACCELEROMETER);
		}
		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			Tab tab=actionBar.newTab()
					.setIcon(R.drawable.ic_tab_bump)
			        .setText(R.string.expose_bump);
			tabsAdapter.addTab(tab, ExposeBumpFragment.class, null);
		}
	}	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.expose_bump, container, false);
	}
}
