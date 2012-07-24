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

import static org.droid2droid.Constants.TAG_SERVER_BIND;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.V;

import org.droid2droid.RAApplication;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public final class RemoteAndroidBinderService extends Service 
{

	@Override
	public IBinder onBind(Intent intent)
	{
		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"onBind mBoss="+RAApplication.sDiscover);
		return RAApplication.sDiscover;
	}
}
