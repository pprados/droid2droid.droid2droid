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

import android.content.Context;
import android.content.Intent;

import com.actionbarsherlock.app.SherlockFragment;

public abstract class AbstractNetworkEventFragment extends SherlockFragment
{
	protected AbstractNetworkEventFragment()
	{
	}

	protected void onReceiveNetworkEvent(Context context,Intent intent)
	{
		
	}
	protected void onReceiveBluetoothEvent(Context context, Intent intent)
	{
		
	}
	protected void onReceiveAirplaneEvent(Context context,Intent intent)
	{
		
	}
	protected void onUpdateActiveNetwork()
	{
		
	}
	protected int getActiveNetwork()
	{
		return getNetworkActivity().getActiveNetwork();
	}
	public AbstractNetworkEventActivity getNetworkActivity()
	{
		return (AbstractNetworkEventActivity)super.getActivity();
	}
	
}
