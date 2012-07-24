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

import android.content.Context;
import android.preference.PreferenceGroup;
import android.util.AttributeSet;
import android.view.View;

public final class ProgressGroup
extends PreferenceGroup
{

	private boolean	mProgress	= false;

	private View	oldView		= null;

	public ProgressGroup(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		setLayoutResource(R.layout.preference_progress_category);
	}

	@Override
	public void onBindView(View view)
	{
		super.onBindView(view);
		View textView = view.findViewById(R.id.scanning_text);
		View progressBar = view.findViewById(R.id.scanning_progress);

		int visibility = mProgress ? View.VISIBLE : View.INVISIBLE;
		textView.setVisibility(visibility);
		progressBar.setVisibility(visibility);

		if (oldView != null)
		{
			oldView.findViewById(R.id.scanning_progress).setVisibility(View.GONE);
			oldView.findViewById(R.id.scanning_text).setVisibility(View.GONE);
			oldView.setVisibility(View.GONE);
		}
		oldView = view;
	}

	/**
	 * Turn on/off the progress indicator and text on the right.
	 * 
	 * @param progressOn
	 *            whether or not the progress should be displayed
	 */
	public void setProgress(boolean progressOn)
	{
		mProgress = progressOn;
		notifyChanged();
	}
}
