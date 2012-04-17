package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.SMS_MESSAGE_SIZE;
import static org.remoteandroid.Constants.SMS_PORT;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.Constants.TAG_SMS;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_TELEPHONY;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;

import java.util.Arrays;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.binder.PendingBroadcastRequest;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;
import org.remoteandroid.ui.contacts.AbstractSMSFragment;

import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

public final class ConnectSMSFragment extends AbstractSMSFragment
implements PendingBroadcastRequest.OnBroadcastReceive
{
	private static final long ESTIMATION_SEND_SMS=1000;
	private static final long ESTIMATION_WAIT_CALLBACK=60000;
	private static final String KEY_PHONE_NUMBER="phone";
	
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET|FEATURE_TELEPHONY); // TODO: FEATURE_RA_ACTIVEDttt
		}
		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			Tab tab=actionBar.newTab()
					.setIcon(R.drawable.ic_tab_sms)
			        .setText(R.string.connect_sms);
			tabsAdapter.addTab(tab, ConnectSMSFragment.class, null);
		}
	}	

	private RemoteAndroidInfoImpl mCallBackInfo; 
	
	public void sendData(final String receiver)
	{
		Bundle bundle=new Bundle();
		bundle.putString(KEY_PHONE_NUMBER, receiver);
		showConnect(new String[0], getConnectActivity().mFlags,bundle);
	}

	private static long[] sBootStrapEstimations=new long[]{ESTIMATION_SEND_SMS,
			ESTIMATION_WAIT_CALLBACK,ESTIMATION_CONNEXION_3G*3};
	private static long[] sBootStrapEstimationsBroadcast=new long[]{ESTIMATION_SEND_SMS};
	
	@Override
	public Object doTryConnect(
			ProgressJobs<?,?> progressJobs,
			ConnectDialogFragment fragment,
			String[] uris,
			int flags,
			Bundle param)
	{
		try
		{
			String phoneNumber=param.getString(KEY_PHONE_NUMBER);
			progressJobs.resetCurrentStep();
			
			if (getConnectActivity().isBroadcast())
			{
				progressJobs.setEstimations(sBootStrapEstimationsBroadcast);
				PendingBroadcastRequest.registerListener(this);
				// 1. Send SMS
				long cookie=Application.randomNextLong();
				progressJobs.incCurrentStep();
				RemoteAndroidInfoImpl info=Trusted.getInfo(getActivity());
				Messages.BroadcastMsg msg=Messages.BroadcastMsg.newBuilder()
					.setType(Messages.BroadcastMsg.Type.EXPOSE)
					.setIdentity(ProtobufConvs.toIdentity(info))
					.setCookie(cookie)
					.build();
				sendSMS(phoneNumber,msg.toByteArray());
				return ProgressJobs.OK;
			}
			else
			{
				progressJobs.setEstimations(sBootStrapEstimations);
				PendingBroadcastRequest.registerListener(this);
				// 1. Send SMS
				long cookie=Application.randomNextLong();
				progressJobs.incCurrentStep();
				RemoteAndroidInfoImpl info=Trusted.getInfo(getActivity());
				Messages.BroadcastMsg msg=Messages.BroadcastMsg.newBuilder()
					.setType(Messages.BroadcastMsg.Type.CONNECT)
					.setIdentity(ProtobufConvs.toIdentity(info))
					.setCookie(cookie)
					.build();
				sendSMS(phoneNumber,msg.toByteArray());
			}
			
			// 2. Wait callback
			progressJobs.incCurrentStep();
			
			synchronized (this)
			{
				wait(ESTIMATION_WAIT_CALLBACK);
			}
			if (mCallBackInfo==null)
			{
				return R.string.connect_alert_never_receive_sms;
			}
			if (D) Log.d(TAG_SMS,PREFIX_LOG+"callbacked from "+mCallBackInfo);
			uris=mCallBackInfo.getUris();
			long[] estimations=new long[uris.length+sBootStrapEstimations.length];
			Arrays.fill(estimations, ESTIMATION_CONNEXION_3G);
			System.arraycopy(sBootStrapEstimations, 0, estimations, 0, sBootStrapEstimations.length);
			progressJobs.setEstimations(estimations);
			progressJobs.incCurrentStep();
			return ConnectDialogFragment.tryAllUris(progressJobs, uris, flags,this);
		}
		catch (InterruptedException e)
		{
			// Ignore
			if (D) Log.d(TAG_CONNECT,PREFIX_LOG+"Interrupt wait",e);
			return null;
		}
		finally
		{
			PendingBroadcastRequest.removeListener(this);
		}
	}
	@Override
	public synchronized boolean onBroadcast(long cookie,RemoteAndroidInfoImpl info)
	{
		mCallBackInfo=info;
		notify();
		return true;
	}
	
	private void sendSMS(String receiver,byte[] buf)
	{
		int fragmentSize = SMS_MESSAGE_SIZE;
		if (buf.length < fragmentSize)
			fragmentSize = buf.length + 1;
		byte[] fragment = new byte[fragmentSize];
		int fragNumber = 0;
		int nbStep=buf.length/(SMS_MESSAGE_SIZE - 1)+1;
		if (V) Log.v(TAG_SMS,"Sending "+nbStep+" sms...");
		int maxsize=(SMS_MESSAGE_SIZE - 1);
		for (int i = 0; i < buf.length; i += maxsize)
		{
			boolean last = (buf.length - i) < maxsize;
			int len = Math.min(buf.length - i, maxsize);
			System.arraycopy(buf, i, fragment, 1, len);
			byte[] pushFragment=fragment;
			if (last)
			{
				pushFragment=new byte[len+1];
				System.arraycopy(buf, i, pushFragment, 1, len);
			}
			pushFragment[0] = (byte) ((last ? 0x80 : 0) | fragNumber);
			SmsManager.getDefault().sendDataMessage(
				receiver, null, SMS_PORT, pushFragment, null, null);
			++fragNumber;
		}
		if (V) Log.v(TAG_SMS,"Sending "+nbStep+" sms done.");
	}
	@Override
	protected void updateStatus(int activeNetwork)
	{
		if (mUsage==null) // Not yet initialized
			return;
		boolean airplane=Settings.System.getInt(getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
		if (airplane)
		{
			mUsage.setText(R.string.connect_sms_help_airplane);
		}
		else
		{
			if ((activeNetwork & NetworkTools.ACTIVE_REMOTE_ANDROID)==0)
			{
				mUsage.setText(R.string.connect_sms_help_activate);
				mEditText.setVisibility(View.GONE);
				mList.setVisibility(View.GONE);
			}
			else
			{
				mUsage.setText(R.string.connect_sms_help);
				mEditText.setVisibility(View.VISIBLE);
				mList.setVisibility(View.VISIBLE);
			}
		}
	}
	
}
