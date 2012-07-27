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


import static org.droid2droid.Droid2DroidManager.EXTRA_FLAGS;
import static org.droid2droid.Droid2DroidManager.FLAG_ACCEPT_ANONYMOUS;
import static org.droid2droid.Droid2DroidManager.FLAG_PROPOSE_PAIRING;
import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.TAG_INSTALL;

import java.io.File;

import org.droid2droid.R;
import org.droid2droid.RAApplication;
import org.droid2droid.ui.connect.ConnectActivity;
import org.droid2droid.ui.expose.ExposeActivity;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.actionbarsherlock.widget.ShareActionProvider;


// TODO: expose NFC tag
public final class MainActivity extends AbstractNetworkEventActivity
implements MainFragment.CallBack
{
//	private RemoteAndroidNfcHelper mNfcIntegration; // FIXME
	private FragmentManager	mFragmentManager;
	private MainFragment	mFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_CONTEXT_MENU);
		super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);
		setProgressBarIndeterminateVisibility(Boolean.FALSE); // Important: Use Boolean value !
		mFragmentManager = getSupportFragmentManager(); // getSupportFragmentManager();
		mFragment = (MainFragment) mFragmentManager.findFragmentById(R.id.fragment);
		RAApplication.startService();
//		mNfcIntegration=new RemoteAndroidNfcHelperImpl(this);
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
//		mNfcIntegration.onNewIntent(this, intent);
	}	
	@Override
	protected void onResume()
	{
		super.onResume();
		mFragment.setCallBack(this);
//		NfcUtils.onResume(this, mNfcIntegration);
	}
	@Override
	protected void onPause()
	{
		super.onPause();
		mFragment.setCallBack(null);
//		mNfcIntegration.onPause(this);
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_fragment_menu, menu);
		
		// Share menu
	    try
	    {
		    MenuItem item = menu.findItem(R.id.share);
	        
		    PackageManager pm=getPackageManager();
		    ApplicationInfo info=pm.getApplicationInfo(getPackageName(), 0);
		    
		    Intent shareIntent=new Intent(Intent.ACTION_SEND)
		    	.putExtra(Intent.EXTRA_SUBJECT,pm.getText(getPackageName(), info.labelRes, info))
		    	.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(info.sourceDir)))
//		    	.setType("application/vnd.android.package-archive");
		    	.setType("*/*") // Android >3.x refuse to download apk
//		    	.setType("image/jpeg")
		    	;
		    ShareActionProvider shareActionProvider = (ShareActionProvider) item.getActionProvider();
		    shareActionProvider.setShareHistoryFileName(ShareActionProvider.DEFAULT_SHARE_HISTORY_FILE_NAME);
		    shareActionProvider.setShareIntent(shareIntent);
	    }
		catch (NameNotFoundException e)
		{
			// Ignore
			if (E) Log.e(TAG_INSTALL,"Impossible to share RemoteAndroid");
		}
		return true;
	}
	
	@Override
	public void onExpose()
	{
		Intent intent = new Intent(this, ExposeActivity.class);
		startActivity(intent);
	}
	@Override
	public void onConnect()
	{
		Intent intent = new Intent(this, ConnectActivity.class) // For broadcast mode
			.putExtra(EXTRA_FLAGS, FLAG_PROPOSE_PAIRING|FLAG_ACCEPT_ANONYMOUS);
		startActivity(intent);
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
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
		return mFragment;
	}
}
