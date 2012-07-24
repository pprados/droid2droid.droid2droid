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

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockFragmentActivity;

public abstract class StyleFragmentActivity extends SherlockFragmentActivity
{
	private static final String	EXTRA_THEME_APPNAME	= "theme.app";

	private static final String	EXTRA_THEME_ID		= "theme.id";

	private static final String	EXTRA_TITLE			= "title";

	private Resources.Theme			mTheme	= null;

	private int					mThemeResource;

	public static final void startActivityForResult(Activity context, String action, int requestCode)
	{
		startActivityForResult(context, action, requestCode, null, 0);
	}

	public static final void startActivityForResult(Activity context, String action, int requestCode, int theme)
	{
		startActivityForResult(context, action, requestCode, null, theme);
	}

	public static final void startActivityForResult(Activity context, String action, int requestCode, String title)
	{
		startActivityForResult(context, action, requestCode, title, 0);
	}

	/**
	 * Start the activity.
	 * 
	 * @param action
	 * 				the action name
	 * @param context
	 *            The current context.
	 * @param requestCode
	 *            The request code id.
	 * @param title
	 *            The title to used in the activity. May be <code>null</code>.
	 * @param theme
	 *            The theme to use. May be <code>null</code>.
	 */
	public static final void startActivityForResult(Activity context, String action,int requestCode,
			String title, int theme)
	{
		Intent intent = new Intent(action);
		if (title != null)
		{
			intent.putExtra(EXTRA_TITLE, title);
		}
		if (theme != 0)
		{
			intent.putExtra(EXTRA_THEME_APPNAME, context.getApplicationInfo().packageName);
			intent.putExtra(EXTRA_THEME_ID, android.R.style.Theme_Light);
		}
		context.startActivityForResult(intent, requestCode);
	}

	// ---------------------------------------

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// Customize the theme and title
//		Intent intent = getIntent();
//		String themeApp = intent.getStringExtra(EXTRA_THEME_APPNAME);
//		int themeId = intent.getIntExtra(EXTRA_THEME_ID, 0);
//		if (themeApp != null && themeId != 0)
//		{
//			try
//			{
//				mResources = getPackageManager().getResourcesForApplication(themeApp);
//				// TODO: getPackageManager().getApplicationIcon(themeApp);
//				setTheme(themeId);
//			}
//			catch (NameNotFoundException e)
//			{
//				if (E)
//					Log.e(TAG_CONNECT, "Application " + themeApp + " not found for use the theme "
//							+ themeId + ".");
//			}
//		}
//		String title = intent.getStringExtra(EXTRA_TITLE);
//		if (title != null)
//			setTitle(title);

		super.onCreate(savedInstanceState);
	}


	@Override
	public Resources.Theme getTheme()
	{
		if (mTheme == null)
		{
			if (mThemeResource == 0)
			{
				mTheme = super.getTheme();
				return mTheme;
			}
			mTheme = getResources().newTheme();
			mTheme.applyStyle(mThemeResource, true);
		}
		return mTheme;
	}

	@Override
	public void setTheme(int resid)
	{
		mThemeResource = resid;
	}
}
