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

import static org.droid2droid.Constants.TIMEOUT_COOKIE;
import static org.droid2droid.internal.Constants.COOKIE_NO;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Cookies // TODO: cookie persistant ?
{
	private class Cookie
	{
		long cookie;
		long timestamp;
	};
	private final Map<String, Cookie> mCookies=Collections.synchronizedMap(new HashMap<String,Cookie>());
	
	public void removeCookie(String key)
	{
		mCookies.remove(key);
	}
	public void addCookie(String key,long cookie)
	{
		Cookie cook=new Cookie();
		cook.cookie=cookie;
		cook.timestamp=System.currentTimeMillis()+TIMEOUT_COOKIE;
		mCookies.put(key,cook);
	}
	public long getCookie(String key)
	{
		Cookie cookie=mCookies.get(key);
		if (cookie!=null)
		{
			if (cookie.timestamp<System.currentTimeMillis())
			{
				mCookies.remove(cookie); // TODO: Purge async
				return COOKIE_NO;
			}
			cookie.timestamp=System.currentTimeMillis()+TIMEOUT_COOKIE; // TODO: add a maximum timestamp ?			
		}
		return (cookie==null) ? COOKIE_NO : cookie.cookie;
	}
	public void clear()
	{
		mCookies.clear();
	}
}