package org.remoteandroid.ui.connect;

import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;
import org.remoteandroid.ui.contacts.AbstractSMSFragment;
import org.remoteandroid.ui.contacts.SMSSendingActivity.SendSMSDialogFragment;

import android.support.v4.app.ActionBar;
import android.view.View;
import android.widget.TextView;

public class ConnectSMSFragment extends AbstractSMSFragment
{
	private View mViewer;
	private TextView mUsage;
	
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET);
		}
		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			tabsAdapter.addTab(actionBar.newTab()
		        .setText(R.string.connect_sms), ConnectSMSFragment.class, null);
		}
	}	
	
	public void sendData(final String receiver)
	{
		Application.hideSoftKeyboard(getActivity());
		final SendSMSDialogFragment dlg=SendSMSDialogFragment.sendSMS(receiver);
		dlg.show(mFragmentManager, "dialog");
	}
}
