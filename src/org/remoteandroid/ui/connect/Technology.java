package org.remoteandroid.ui.connect;

import java.util.ArrayList;

import org.remoteandroid.Application;
import org.remoteandroid.R;

import android.app.Fragment;
import android.content.Context;
//import android.support.v4.app.Fragment;
import android.telephony.TelephonyManager;
import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.RemoteAndroidInfo.*;
import static org.remoteandroid.NetworkTools.*;

abstract class Technology
{
	public static Technology sDefault=new Technology(0, 0, 0, 0, R.string.connect_empty_help,0)
	{
		@Override
		AbstractBodyFragment makeFragment()
		{
			return new EmptyBodyFragment();
		}
	};
	private static final int INTERNAL_FEATURE_NETWORK=(CONNECTION_WITH_INTERNET) ? ACTIVE_NETWORK : ACTIVE_LOCAL_NETWORK;
	private static Technology[] sAllTechnology= new Technology[]
		{
			sDefault,
			new Technology(
				FEATURE_NET,
				ACTIVE_LOCAL_NETWORK|ACTIVE_BLUETOOTH,
				R.string.connect_discover,
				R.string.connect_discover_description,
				R.string.connect_discover_help,
				R.string.connect_discover_empty_help)
				{
					@Override
					AbstractBodyFragment makeFragment()
					{
						return new DiscoverFragment();
					}
				},
			new Technology(
				FEATURE_CAMERA|FEATURE_SCREEN,
				INTERNAL_FEATURE_NETWORK|ACTIVE_BLUETOOTH,
				R.string.connect_qrcode,
				R.string.connect_qrcode_description,
				R.string.connect_qrcode_help,
				R.string.connect_qrcode_empty_help)
				{
					@Override
					AbstractBodyFragment makeFragment()
					{
						return new QRCodeFragment();
					}
				},
			new Technology(
				FEATURE_TELEPHONY,
				INTERNAL_FEATURE_NETWORK|ACTIVE_BLUETOOTH,
				R.string.connect_sms,
				R.string.connect_sms_description,
				R.string.connect_sms_help,
				R.string.connect_sms_empty_help)
				{
					@Override
					AbstractBodyFragment makeFragment()
					{
						return new SMSFragment();
					}
				},
			new Technology(
				FEATURE_SCREEN|FEATURE_NET,
				INTERNAL_FEATURE_NETWORK|ACTIVE_BLUETOOTH,
				R.string.connect_input,
				R.string.connect_input_description,
				R.string.connect_input_help,
				R.string.connect_input_empty_help)
				{
					@Override
					AbstractBodyFragment makeFragment()
					{
						return new InputIdFragment();
					}
				},
			
		};
	
	
	int mFeature;
	int mMaskActiveNetwork;
	int mActiveNetwork;
	int mContent;
	int mDescription;
	int mHelp;
	int mEmptyHelp;
	
	private Technology(
			int feature,
			int activeNetwork,
			int content,int description,int help,int emptyHelp)
	{
		mFeature=feature;
		mActiveNetwork=activeNetwork;
		mContent=content;
		mDescription=description;
		mHelp=help;
		mEmptyHelp=emptyHelp;
	}
	
	public static Technology[] getTechnologies()
	{
		// TODO: Filtrer suivant les capacités du terminal
		ArrayList<Technology> a=new ArrayList<Technology>();
		
		for (int i=0;i<sAllTechnology.length;++i)
		{
			Technology tech=sAllTechnology[i];
			if ((tech.mFeature & Application.sFeature)==tech.mFeature)
			{
				a.add(tech);
			}
		}
		return a.toArray(new Technology[a.size()]);
	}
	
	abstract AbstractBodyFragment makeFragment();
	
	@Override
	public String toString()
	{
		return getClass().getName();
	}
}
