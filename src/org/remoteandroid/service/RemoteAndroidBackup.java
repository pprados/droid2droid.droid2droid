package org.remoteandroid.service;

import static org.remoteandroid.Application.sDeviceId;
import static org.remoteandroid.Application.sPackageName;
import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupManager;
import android.app.backup.SharedPreferencesBackupHelper;

/**
 * Backup the preferences.
 * 
 * @author pprados
 */
public class RemoteAndroidBackup extends BackupAgentHelper
{
	@Override
	public void onCreate()
	{
		super.onCreate();
		addHelper("defaultSharedPreferences", new SharedPreferencesBackupHelper(this,sDeviceId));
	}
	
	public static void dataChanged()
	{
		BackupManager.dataChanged(sPackageName + "_preferences");
	}
}