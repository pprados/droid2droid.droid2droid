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
package org.droid2droid.install;

import static org.droid2droid.Constants.EXTRA_INSTALLER_PACKAGE_NAME;
import static org.droid2droid.Constants.LOCK_WAIT_INSTALL;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.TAG_INSTALL;
import static org.droid2droid.internal.Constants.V;
import static org.droid2droid.internal.Constants.W;

import java.io.File;

import org.droid2droid.CommunicationWithLock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
public final class InstallApkActivity extends Activity
{
	private static final String EXTRA_FILENAME="filename";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"on Create install apk");
		// Keep alive
//		new AsyncTask<Void, Void, Void>()
//		{
//			@Override
//			protected Void doInBackground(Void... paramArrayOfParams)
//			{
				PackageManager packageManager=getPackageManager();
				packageManager.setComponentEnabledSetting(getComponentName(),
						PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
						PackageManager.DONT_KILL_APP);
//				return null;
//			}
//			@Override
//			protected void onPostExecute(Void result)
//			{
//				super.onPostExecute(result);
				startRealInstallApkActivity();
//			}
//		}.execute();
	}
	
	private void startRealInstallApkActivity()
	{
		PackageManager packageManager=getPackageManager();
		packageManager.setComponentEnabledSetting(getComponentName(),
				PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
				PackageManager.DONT_KILL_APP);

		final Intent intent = new Intent(Intent.ACTION_VIEW);
		String filename=getIntent().getStringExtra(EXTRA_FILENAME);
		intent.setDataAndType(Uri.fromFile(new File(filename)), 
			"application/vnd.android.package-archive");
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_FROM_BACKGROUND);
		startActivity(intent);			
	}
	@Override
	protected void onRestart()
	{
		super.onRestart();
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"on Restart install apk");
		finish();
	}
	@Override
	protected void onPause()
	{
		super.onPause();
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"on Pause install apk");
	}
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		if (W) Log.w(TAG_INSTALL,PREFIX_LOG+"Abort installation");
		CommunicationWithLock.putResult(LOCK_WAIT_INSTALL, null);
	}
	
	public static void startActivity(Context context,File fileName)
	{
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"Start install apk activity");
		Intent intent = new Intent(context,InstallApkActivity.class);
		intent.putExtra(InstallApkActivity.EXTRA_FILENAME, fileName.toString());
		intent.putExtra(EXTRA_INSTALLER_PACKAGE_NAME,context.getPackageName()); // Trace the installer package (this app.)
		intent.setFlags(
			Intent.FLAG_ACTIVITY_NEW_TASK
			//|Intent.FLAG_FROM_BACKGROUND
			//|Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
			//|Intent.FLAG_ACTIVITY_NO_ANIMATION
			//|Intent.FLAG_ACTIVITY_NO_HISTORY
			//|Intent.FLAG_ACTIVITY_TASK_ON_HOME
			);
		context.startActivity(intent);	
	}
}
