package org.remoteandroid.ui.expose;


import org.remoteandroid.ui.TabsAdapter;

import android.app.Activity;
import android.content.Context;
import android.support.v4.app.ActionBar;
import android.support.v4.app.ActionBar.Tab;
import android.support.v4.app.FragmentActivity;

public abstract class Expose
{
	protected static final String KEY_QRCODE="QRCode";
	protected static final String KEY_SMS="SMS";
	protected static final String KEY_SOUND="SOUND";
	protected static final String KEY_INPUT="Input";

	// TODO: adapter suivant les capacités du téléphone
	public static final Expose[] sExpose=
	{
		new QRCodeExpose(),
//		new SMSExpose(),
//		new SoundExpose(),
//		new TicketExpose()
	};
	
	Expose(int value,String key,int feature)
	{
		mValue=value;
		mKey=key;
		mFeature=feature;
	}
	
	public abstract void createTab(FragmentActivity activity,TabsAdapter tabsAdapter, ActionBar actionBar);
	@Deprecated
	public abstract void startExposition(Activity context);
	public int mValue;
	public String mKey;
	public int mFeature;
}
