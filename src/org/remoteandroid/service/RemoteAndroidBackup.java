package org.remoteandroid.service;

import static org.remoteandroid.RAApplication.sDeviceId;
import static org.remoteandroid.RAApplication.sPackageName;
import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupManager;
import android.app.backup.SharedPreferencesBackupHelper;

/**
 * Backup the preferences.
 * 
 * @author pprados
 */
public final class RemoteAndroidBackup extends BackupAgentHelper
{
	public RemoteAndroidBackup()
	{
	}
	@Override
	public void onCreate()
	{
		super.onCreate();
		addHelper("defaultSharedPreferences", new SharedPreferencesBackupHelper(this,sDeviceId));
	}
//	@Override
//	public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState)
//			throws IOException
//	{
//		super.onBackup(oldState, data, newState);
//	}
//	@Override
//	public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException
//	{
//		super.onRestore(data, appVersionCode, newState);
//	}
	
//	@Override
//	public void onFullBackup(FullBackupDataOutput data) throws IOException
//	{
//		super.onFullBackup(data);
//	}
//	@Override
//	public void onRestoreFile(ParcelFileDescriptor data, long size, File destination, int type, long mode, long mtime)
//			throws IOException
//	{
//		super.onRestoreFile(data, size, destination, type, mode, mtime);
//	}

	public static void dataChanged()
	{
		BackupManager.dataChanged(sPackageName + "_preferences");
	}
}