package org.remoteandroid.ui.expose;

import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_TELEPHONY;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;
import org.remoteandroid.ui.contacts.AbstractSMSFragment;
import org.remoteandroid.ui.contacts.SMSSendingActivity.SendSMSDialogFragment;

import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;

public class ExposeSMSFragment extends AbstractSMSFragment
{
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_TELEPHONY);
		}

		@Override
		public void createTab(FragmentActivity activity,TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			tabsAdapter.addTab(actionBar.newTab()
		        .setText(R.string.expose_sms), ExposeSMSFragment.class, null);
		}
	}
	
	public void sendData(final String receiver)
	{
		Application.hideSoftKeyboard(getActivity());
		final SendSMSDialogFragment dlg=SendSMSDialogFragment.sendSMS(receiver);
		dlg.show(mFragmentManager, "dialog");

		
	}
}
