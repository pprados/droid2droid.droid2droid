package org.remoteandroid;

import static org.remoteandroid.Constants.TIMEOUT_COOKIE;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Cookies // TODO: cookie persistant
{
	private class Cookie
	{
		long cookie;
		long timestamp;
	};
	private Map<String, Cookie> mCookies=Collections.synchronizedMap(new HashMap<String,Cookie>());
	
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
				return 0;
			}
			cookie.timestamp=System.currentTimeMillis()+TIMEOUT_COOKIE; // TODO: add a maximum timestamp ?			
		}
		return (cookie==null) ? 0 : cookie.cookie;
	}
	public void clear()
	{
		mCookies.clear();
	}
}