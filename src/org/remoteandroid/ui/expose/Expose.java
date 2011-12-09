package org.remoteandroid.ui.expose;

import android.app.Activity;
import android.content.Context;

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
		new SMSExpose(),
		new DTMFExpose(),
		new TicketExpose()
	};
	
	Expose(int value,String key,int feature)
	{
		mValue=value;
		mKey=key;
		mFeature=feature;
	}
	public abstract void startExposition(Activity context);
	public int mValue;
	public String mKey;
	public int mFeature;
}
