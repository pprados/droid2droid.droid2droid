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
package org.droid2droid.binder;

import java.util.LinkedList;
import java.util.List;

import org.droid2droid.internal.RemoteAndroidInfoImpl;

public final class PendingBroadcastRequest
{
	public interface OnBroadcastReceive
	{
		boolean onBroadcast(long cookie,RemoteAndroidInfoImpl info);
	}
	private static List<OnBroadcastReceive> sCallBacks=new LinkedList<OnBroadcastReceive>();
	public static synchronized void registerListener(OnBroadcastReceive receive)
	{
		sCallBacks.add(receive);
	}
	public static synchronized void removeListener(OnBroadcastReceive receive)
	{
		sCallBacks.remove(receive);
	}
	public static synchronized boolean notify(long cookie,RemoteAndroidInfoImpl info)
	{
		boolean rc=false;
		for (OnBroadcastReceive cb:sCallBacks)
		{
			if (cb.onBroadcast(cookie,info))
				rc=true;
		}
		return rc;
	}
}
