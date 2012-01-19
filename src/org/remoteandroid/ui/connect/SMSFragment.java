package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.Constants.TAG_SMS;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.V;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.internal.Base64;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.ui.expose.TicketExpose;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class SMSFragment extends AbstractBodyFragment
{

	private static final String ACTION_RECEIVE_SMS = "android.intent.action.DATA_SMS_RECEIVED";
	private static final String EXTRA_PDU="pdus";
	public static final int MESSAGE_SIZE = SmsMessage.MAX_USER_DATA_BYTES;

	private View mViewer;
	private BlockingQueue<byte[]> mQueue = new ArrayBlockingQueue<byte[]>(10, false);
	
	static class Fragments
	{
		int max = -1;

		SparseArray<byte[]> bufs = new SparseArray<byte[]>();
	}

	
	public class SMSReceiver extends BroadcastReceiver
	{

		private Hashtable<String, Fragments> mAllFragments = new Hashtable<String, SMSFragment.Fragments>();

		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (!intent.getAction().equals(ACTION_RECEIVE_SMS))
				return;
			final String uri = intent.getDataString();

			if (Uri.parse(uri).getPort() != SMS_PORT)
				return;

			final Object[] pdus = (Object[]) intent.getExtras().get(EXTRA_PDU);
			byte[] data = null;

			for (int i = 0; i < pdus.length; i++)
			{
				SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdus[i]);
				final String sender = msg.getOriginatingAddress();
				data = msg.getUserData();
				readData(data, sender);
			}
		}

		private void readData(byte[] fragment, String sender)
		{

			Fragments currentFragments = mAllFragments.get(sender);
			if (currentFragments == null)
			{
				currentFragments = new Fragments();
				mAllFragments.put(sender, currentFragments);
			}

			int fragNumber = fragment[0] & 0x7F;
			boolean isLast=((fragment[0] & 0x80) == 0x80);
			if (isLast)
				currentFragments.max = fragNumber;
			if (V) Log.v(TAG_SMS,"Receive fragment  #"+fragNumber+" ["+(fragment.length-1)+"] "+(((fragment[0] & 0x80) == 0x80) ? "(last)":""));
			currentFragments.bufs.put(fragNumber, fragment);

			if (currentFragments.bufs.size()-1 == currentFragments.max)
			{
				int bufferSize = (currentFragments.bufs.size() - 1) * (MESSAGE_SIZE - 1)
						+ currentFragments.bufs.get(currentFragments.max).length - 1;
				if (V) Log.v(TAG_SMS, "BufferSize = " + bufferSize);
				byte[] result = new byte[bufferSize];
				for (int i = 0; i < currentFragments.bufs.size(); ++i)
				{
					byte[] r = currentFragments.bufs.get(i);
					if (r == null)
					{
						currentFragments.bufs.clear();
						mAllFragments.remove(sender);
						if (W) Log.w(TAG_SMS, PREFIX_LOG + "Receive invalide SMS");
						return; // Ignore bad message
					}
					System.arraycopy(r, 1, result, i * (MESSAGE_SIZE - 1), r.length - 1);
				}
				currentFragments.bufs.clear();
				mAllFragments.remove(sender);
				mQueue.add(result);
			}
		}

	}
	private BroadcastReceiver mSmsReceiver =new SMSReceiver(); 


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer = inflater.inflate(R.layout.connect_sms, container, false);
		final ConnectActivity.FirstStep firstStep = new ConnectActivity.FirstStep()
		{
			public int run(ConnectActivity.TryConnection tryConn)
			{
				try
				{
					tryConn.publishMessage(R.string.connect_sms_wait,0);

					// TODO: gerer le cancel
					byte[] bytes = mQueue.poll(
						SMS_TIMEOUT_WAIT, TimeUnit.MILLISECONDS);
					Messages.Candidates candidates = Messages.Candidates.parseFrom(bytes);
					tryConn.setUris(ProtobufConvs.toUris(Application.sAppContext,candidates));
					return 0;
				}
				catch (Exception e)
				{
					if (E) Log.e(TAG_CONNECT, PREFIX_LOG + "Error when retreive shorten ticket", e);
					return R.string.connect_sms_error_invalides_sms; 
				}
			}
		};
		((Button)mViewer.findViewById(R.id.execute)).setOnClickListener(new Button.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				ConnectActivity activity = (ConnectActivity) getActivity();
				activity.tryConnect(firstStep, new ArrayList<String>(), activity.isAcceptAnonymous());
			}
		});
		return mViewer;
	}

	protected void onUpdateActiveNetwork()
	{
		ConnectActivity activity = (ConnectActivity) getActivity();
		if (activity == null)
			return;

	}

	/*
	 * <!-- SMS TO REMOVE --> <receiver
	 * class=".ui.connect.SMSFragment$SMSReceiver"
	 * android:name=".ui.connect.SMSFragment$SMSReceiver" > <intent-filter
	 * android:priority="10" > <action
	 * android:name="android.intent.action.DATA_SMS_RECEIVED" />
	 * 
	 * <data android:scheme="sms" /> <data android:port="6800" />
	 * </intent-filter> </receiver>
	 */
	@Override
	public void onResume()
	{
		super.onResume();
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_RECEIVE_SMS);
		filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
		filter.addDataScheme("sms");
		filter.addDataAuthority("localhost", String.valueOf(SMS_PORT));
		getActivity().registerReceiver(mSmsReceiver, filter);
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
	}
	@Override
	public void onPause()
	{
		super.onPause();
		getActivity().unregisterReceiver(
			mSmsReceiver);
	}

}
