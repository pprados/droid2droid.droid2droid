package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.SMS_PORT;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.Constants.*;
import static org.remoteandroid.RemoteAndroidInfo.*;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.binder.PendingBroadcastRequest;
import org.remoteandroid.internal.Base64;
import org.remoteandroid.internal.Constants;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.Pair;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;
import org.remoteandroid.ui.contacts.AbstractSMSFragment;
import org.remoteandroid.ui.expose.ExposeTicketFragment;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class ConnectSMSFragment extends AbstractSMSFragment
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
			tabsAdapter.addTab(actionBar.newTab()
		        .setText(R.string.connect_sms), ConnectSMSFragment.class, null);
		}
	}	

	private RemoteAndroidInfoImpl mCallBackInfo; 
	
	public void sendData(final String receiver)
	{
		Bundle bundle=new Bundle();
		bundle.putString(KEY_PHONE_NUMBER, receiver);
		showConnect(new String[0], true,bundle);
	}

	private static long[] sBootStrapEstimations=new long[]{ESTIMATION_SEND_SMS,
			ESTIMATION_WAIT_CALLBACK,ESTIMATION_CONNEXION_3G*3};
	private static long[] sBootStrapEstimationsBroadcast=new long[]{ESTIMATION_SEND_SMS};
	
	@Override
	public Object doTryConnect(
			ProgressJobs<?,?> progressJobs,
			ConnectDialogFragment fragment,
			String[] uris,
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
				long cookie=Application.sRandom.nextLong();
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
				long cookie=Application.sRandom.nextLong();
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
			return ConnectDialogFragment.tryAllUris(progressJobs, uris, this);
		}
		catch (final Exception e)
		{
			if (E) Log.e(TAG_CONNECT,PREFIX_LOG+"Error when retreive shorten ticket ("+e.getMessage()+")");
			if (D)
			{
				Application.sHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						Toast.makeText(Application.sAppContext, e.getMessage(), Toast.LENGTH_LONG).show();
					}
				});
			}
			return R.string.connect_ticket_message_error_get_internet;
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
}
