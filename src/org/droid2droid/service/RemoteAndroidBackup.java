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
package org.droid2droid.service;

import static org.droid2droid.RAApplication.sDeviceId;
import static org.droid2droid.RAApplication.sPackageName;
import android.annotation.TargetApi;
import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupManager;
import android.app.backup.SharedPreferencesBackupHelper;

/**
 * Backup the preferences.
 * 
 * @author pprados
 */
@TargetApi(8)
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
	public static void dataChanged()
	{
		BackupManager.dataChanged(sPackageName + "_preferences");
	}
}