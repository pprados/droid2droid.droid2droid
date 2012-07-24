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
package org.droid2droid;

import static org.droid2droid.internal.Constants.E;
import android.annotation.TargetApi;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;

@TargetApi(8)
public final class LogMarket
{
	interface WTF
	{
		public void wtf(String tag,String msg);
		public void wtf(String tag,Throwable e);
		public void wtf(String tag,String msg,Throwable e);
		
	}
	private static WTF _wtf;
	static
	{
		// Pour version 2.2
		if (VERSION.SDK_INT>=VERSION_CODES.FROYO)
		{
			_wtf=new WTF()
			{

				@Override
				public void wtf(String tag, String msg)
				{
					Log.wtf(tag, msg);
				}

				@Override
				public void wtf(String tag, Throwable e)
				{
					Log.wtf(tag, e);
				}

				@Override
				public void wtf(String tag, String msg, Throwable e)
				{
					Log.wtf(tag,msg,e);
				}
				
			};
		}
		else
			_wtf=new WTF()
			{

				@Override
				public void wtf(String tag, String msg)
				{
					if (E) Log.e(tag,msg);
				}

				@Override
				public void wtf(String tag, Throwable e)
				{
					if (E) Log.e(tag,"",e);
				}

				@Override
				public void wtf(String tag, String msg, Throwable e)
				{
					if (E) Log.e(tag,msg,e);
				}
				
			};
	}
	public static void wtf(String tag,String msg)
	{
		_wtf.wtf(tag, "WTF:"+msg);
	}
	public static void wtf(String tag,Throwable e)
	{
		_wtf.wtf(tag,e);
	}
	public static void wtf(String tag,String msg,Throwable e)
	{
		_wtf.wtf(tag,"WTF:"+msg,e);
	}
}
