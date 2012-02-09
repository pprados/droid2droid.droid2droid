package org.remoteandroid.install;

import java.io.File;

/*
 * Install from market, after authent
 * $.post('/install', {
 *   id: 'com.attacker.maliciousapp',
 * device: initProps['selectedDeviceId'],
 * token: initProps['token'],
 * xhr: '1' }, function(data) {
 * });
 */

import org.remoteandroid.CommunicationWithLock;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import static org.remoteandroid.Constants.EXTRA_INSTALLER_PACKAGE_NAME;
import static org.remoteandroid.Constants.LOCK_WAIT_INSTALL;
import static org.remoteandroid.internal.Constants.*;
public class InstallApkActivity extends Activity
{
	public static final String EXTRA_FILENAME="filename";

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
