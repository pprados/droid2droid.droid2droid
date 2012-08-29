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
import org.droid2droid.internal.NetworkTools;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public final class MainFragment extends AbstractBodyFragment
{
	public interface CallBack
	{
		void onExpose();
		void onConnect();
	}
	
	private CallBack mCallBack;
	private int mOldActiveNetwork=-1;
	
	void setCallBack(CallBack callBack)
	{
		mCallBack=callBack;
	}
	private Drawable mDrawable;
	private Button mButtonExpose;
	private View mViewer;
		
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer =inflater.inflate(R.layout.main_expose_connect, container, false);
		
		mButtonExpose=((Button)mViewer.findViewById(R.id.main_expose));
		final PackageManager pm=getActivity().getPackageManager();
		boolean fakeTouch=false;
		if (VERSION.SDK_INT>=VERSION_CODES.HONEYCOMB)
			fakeTouch=pm.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH);
		if (
				!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) &&
				!fakeTouch)
		{
			mButtonExpose.requestFocus();
		}
		mButtonExpose.setOnClickListener(new Button.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mCallBack.onExpose();
			}
		});
		((Button)mViewer.findViewById(R.id.main_connect)).setOnClickListener(new Button.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mCallBack.onConnect();
			}
		});
		return mViewer;
	}	
	
	@Override
	protected void onUpdateActiveNetwork(int activeNetwork)
	{
		if (mOldActiveNetwork!=activeNetwork)
		{
			mOldActiveNetwork=activeNetwork;
			mButtonExpose.setEnabled((activeNetwork & NetworkTools.ACTIVE_DROID2DROID|NetworkTools.ACTIVE_NETWORK)==(NetworkTools.ACTIVE_DROID2DROID|NetworkTools.ACTIVE_NETWORK));
		}
	}
}
