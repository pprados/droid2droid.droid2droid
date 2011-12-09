package org.remoteandroid;

import org.remoteandroid.internal.Compatibility;

import android.os.Build;
import android.util.Log;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.*;

public class LogMarket
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
		if (Compatibility.VERSION_SDK_INT>=Compatibility.VERSION_FROYO)
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
