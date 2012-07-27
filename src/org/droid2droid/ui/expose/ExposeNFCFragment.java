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

import static org.droid2droid.Constants.TAG_QRCODE;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_NET;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_NFC;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.droid2droid.internal.Constants.V;

import org.droid2droid.AsyncTaskWithException;
import org.droid2droid.NfcUtils;
import org.droid2droid.R;
import org.droid2droid.internal.NetworkTools;
import org.droid2droid.internal.RemoteAndroidInfoImpl;
import org.droid2droid.pairing.Trusted;
import org.droid2droid.ui.AbstractBodyFragment;
import org.droid2droid.ui.FeatureTab;
import org.droid2droid.ui.TabsAdapter;

import android.annotation.TargetApi;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

@TargetApi(10)
public final class ExposeNFCFragment extends AbstractBodyFragment
{
	TextView mUsage;
	Button mWrite;
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
			        .setText(R.string.expose_nfc);
			tabsAdapter.addTab(tab, ExposeNFCFragment.class, null);
		}
	}	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View main=inflater.inflate(R.layout.expose_nfc, container, false);
		mUsage=(TextView)main.findViewById(R.id.usage);
		mWrite=((Button)main.findViewById(R.id.write));
		mWrite.setEnabled(false);
		mWrite.setOnClickListener(new Button.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				final Tag tag=(Tag)getActivity().getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
				if (tag!=null)
				{
					new AsyncTaskWithException<Void,Void,Integer>()
					{
						@Override
						protected Integer doInBackground(Void... params) throws Exception
						{
							return NfcUtils.writeTag(getActivity(),tag,Trusted.getInfo(getActivity()));
						}
						@Override
						protected void onPostExecute(Integer result) 
						{
							Toast.makeText(getActivity(), result, Toast.LENGTH_LONG).show();
							mWrite.setEnabled(false);
						}
						@Override
						protected void onException(Throwable e)
						{
							Toast.makeText(getActivity(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
							mWrite.setEnabled(false);
						}
	
					}.execute();
				}
			}
		});
		return main;
	}
	
	@Override
	protected void onUpdateActiveNetwork(int activeNetwork)
	{
		if (V) Log.v(TAG_QRCODE,"ExposeQRCodeFragment.updateHelp...");
		if (mUsage==null) // Not yet initialized
			return;
		
		if ((activeNetwork & NetworkTools.ACTIVE_NFC)==0)
		{
			mUsage.setText(R.string.expose_nfc_help_no_nfc);
			mWrite.setVisibility(View.GONE);
		}
		else
		{
			mUsage.setText(R.string.expose_nfc_help);
			mWrite.setVisibility(View.VISIBLE);
		}
	}
	
	@Override
	public void onDiscover(RemoteAndroidInfoImpl info)
	{
		if (info.isDiscoverByNFC)
		{
			// Detect a NFC tag. It's possible to write it with my current infos.
			mWrite.setEnabled(true);
		}
	}
	
}
