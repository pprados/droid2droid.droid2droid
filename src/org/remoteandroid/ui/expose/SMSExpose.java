package org.remoteandroid.ui.expose;

import org.remoteandroid.R;
import org.remoteandroid.ui.connect.sms.SMSSendingActivity;

import android.app.Activity;
import android.content.Intent;

class SMSExpose extends Expose
{
	SMSExpose()
	{
		super(R.string.expose_sms,KEY_SMS);
	}

	@Override
	public void startExposition(Activity context)
	{
		context.startActivity(new Intent(context,SMSSendingActivity.class));
	}
}
