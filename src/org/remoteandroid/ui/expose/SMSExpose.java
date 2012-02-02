package org.remoteandroid.ui.expose;

import org.remoteandroid.R;
import org.remoteandroid.ui.TabsAdapter;
import org.remoteandroid.ui.expose.sms.SMSSendingActivity;
import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.app.ActionBar;
import android.support.v4.app.ActionBar.Tab;
import android.support.v4.app.FragmentActivity;
import static org.remoteandroid.RemoteAndroidInfo.*;
class SMSExpose extends Expose
{
	SMSExpose()
	{
		super(R.string.expose_sms,KEY_SMS,FEATURE_SCREEN|FEATURE_TELEPHONY);
	}

	@Override
	public void startExposition(Activity context)
	{
		context.startActivity(new Intent(context,SMSSendingActivity.class));
	}

	@Override
	public void createTab(FragmentActivity activity,TabsAdapter tabsAdapter, ActionBar actionBar)
	{
		tabsAdapter.addTab(actionBar.newTab()
	        .setText(R.string.expose_sms), ExposeSMSFragment.class, null);
	}
}
