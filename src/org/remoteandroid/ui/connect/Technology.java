package org.remoteandroid.ui.connect;

import org.remoteandroid.R;

import android.app.Fragment;
import android.content.Context;
//import android.support.v4.app.Fragment;

class Technology
{
	enum Type { EMPTY,DISCOVER,QRCODE,NFC,SMS,SOUND,INPUT};
	public static Technology sDefault=new Technology(Type.EMPTY, true, 0, 0, R.string.connect_empty_help);
	private static int[] sStringIds=
		{
			0,0,R.string.connect_empty_help,
			R.string.connect_discover,R.string.connect_discover_description,R.string.connect_discover_help,
			R.string.connect_qrcode,R.string.connect_qrcode_description,R.string.connect_qrcode_help,
			R.string.connect_nfc,R.string.connect_nfc_description,R.string.connect_nfc_help,
			R.string.connect_sms,R.string.connect_sms_description,R.string.connect_sms_help,
			R.string.connect_sound,R.string.connect_sound_description,R.string.connect_sound_help,
			R.string.connect_input,R.string.connect_input_description,R.string.connect_input_help
		};
	
	Type mId;
	boolean mStatus;
	int mContent;
	int mDescription;
	int mHelp;
	Fragment mFragment;
	
	Technology(Type id,boolean status,int content,int description,int help)
	{
		mId=id;
		mStatus=status;
		mContent=content;
		mDescription=description;
		mHelp=help;
	}
	
	public static Technology[] initTechnologies(Context context)
	{
		// TODO: Filtrer suivant les capacités du terminal
		Type[] values=Type.values();
		Technology[] technologies=new Technology[values.length];
		for (int i=0;i<values.length;++i)
		{
			Type type=values[i];
			technologies[i]=
				new Technology(type,
					true,
					sStringIds[type.ordinal()*3],
					sStringIds[type.ordinal()*3+1],
					sStringIds[type.ordinal()*3+2]);
		}
		return technologies;
	}
	AbstractBodyFragment makeFragment()
	{
		AbstractBodyFragment fragment=null;
		switch (mId)
		{
			case EMPTY:
				fragment=new EmptyBodyFragment();
				break;
			case DISCOVER:
				fragment=new DiscoverFragment();
				break;
			case QRCODE:
				fragment=new QRCodeFragment();
				break;
			case NFC:
				fragment=new NFCFragment();
				break;
			case SMS:
				fragment=new SMSFragment();
				break;
			case SOUND:
				fragment=new SoundFragment();
				break;
			case INPUT:
				fragment=new InputIdFragment();
				break;
		}
		if (fragment!=null)
			fragment.setTechnology(this);
		return fragment;
	}
	@Override
	public String toString()
	{
		return mId.toString();
	}
}
