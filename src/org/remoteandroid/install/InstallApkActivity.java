package org.remoteandroid.install;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

public class InstallApkActivity extends Activity
{
	public static final String EXTRA_FILENAME="filename";

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		// Keep alive
		new AsyncTask<Void, Void, Void>()
		{
			@Override
			protected Void doInBackground(Void... paramArrayOfParams)
			{
				PackageManager packageManager=getPackageManager();
				packageManager.setComponentEnabledSetting(getComponentName(),
						PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
						PackageManager.DONT_KILL_APP);
				return null;
			}
			@Override
			protected void onPostExecute(Void result)
			{
				super.onPostExecute(result);
				startRealInstallApkActivity();
			}
		}.execute();
	}
	
	private void startRealInstallApkActivity()
	{
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
		finish();
	}
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		System.out.println("L'instalation a été abandonnée");
	}
}
