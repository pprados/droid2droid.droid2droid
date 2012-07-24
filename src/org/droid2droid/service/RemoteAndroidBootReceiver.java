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

import static org.droid2droid.Constants.PREFERENCES_ACTIVE;

import org.droid2droid.RAApplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

// Auto start remote android with the last state
public final class RemoteAndroidBootReceiver extends BroadcastReceiver 
{
	
    @Override
    public void onReceive(Context context, Intent intent) 
    {
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) 
        {
    		final SharedPreferences preferences=RAApplication.getPreferences();
			if (preferences.getBoolean(PREFERENCES_ACTIVE, false))
				context.startService(new Intent(context,RemoteAndroidService.class));
        }
    }

}
