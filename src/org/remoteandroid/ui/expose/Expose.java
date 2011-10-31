package org.remoteandroid.ui.expose;

import android.content.Context;

public abstract class Expose
{
	protected static final String KEY_QRCODE="QRCode";
	protected static final String KEY_SMS="SMS";
	protected static final String KEY_SOUND="SOUND";
	protected static final String KEY_INPUT="Input";

	public static final Expose[] sExpose=
	{
		new QRCodeExpose(),
		new SMSExpose(),
		new SoundExpose(),
		new InputExpose()
	};
	
	Expose(int value,String key)
	{
		mValue=value;
		mKey=key;
	}
	public abstract void startExposition(Context context);
	public int mValue;
	public String mKey;
}
