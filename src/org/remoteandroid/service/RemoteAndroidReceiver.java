package org.remoteandroid.service;

import static org.remoteandroid.Constants.PREFERENCES_ACTIVE;

import org.remoteandroid.Application;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

// Auto start remote android with the last state
public class RemoteAndroidReceiver extends BroadcastReceiver 
{
	
    @Override
    public void onReceive(Context context, Intent intent) 
    {
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) 
        {
    		final SharedPreferences preferences=Application.getPreferences();
			if (preferences.getBoolean(PREFERENCES_ACTIVE, false))
				context.startService(new Intent(context,RemoteAndroidService.class));
        }
    }

}
