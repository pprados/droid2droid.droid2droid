package org.remoteandroid.ui.connect;

import java.util.Hashtable;

import org.remoteandroid.R;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.*;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class SMSFragment extends AbstractBodyFragment {

	private static final Short _sendingPort = 6800;
	private View _viewer;
	private static ProgressDialog _progressBar;
	static final int SMS_HEADER = 10;
	public static final int MESSAGE_SIZE = 5;//SmsMessage.MAX_USER_DATA_BYTES - SMS_HEADER;
	
	public static class SMSReceiver extends BroadcastReceiver {
		private final String ACTION_RECEIVE_SMS = "android.intent.action.DATA_SMS_RECEIVED";
		private static Fragments currentFragments = new Fragments();
		static Hashtable<String, Fragments> allFragments = new Hashtable<String, SMSFragment.Fragments>();
		
		@Override
		public void onReceive(Context context, Intent intent) {
			if (V)
			Log.v(TAG_SMS, "Action = " + intent.getAction());
			if (!intent.getAction().equals(ACTION_RECEIVE_SMS))
				return;
			final String uri = intent.getDataString();

			// intent.getByteArrayExtra(name)
			if (V)
			Log.v(TAG_SMS, "Action uri = " + uri);
			if (!uri.contains(_sendingPort.toString()))
				return;

			final Object[] pdus = (Object[]) intent.getExtras().get("pdus");
			byte[] data = null;

			for (int i = 0; i < pdus.length; i++) {
				SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdus[i]);
				final String sender = msg.getOriginatingAddress();
				data = msg.getUserData();
				if (V) {
				Log.v(TAG_SMS, "data = " + data.toString());
				Log.v(TAG_SMS, "data length = " + data.length);
				}
				readData(data, sender);
			}   
		}
		
		public static void readData(byte[] fragment, String sender) {
			
			int fragNumber = fragment[0] & 0x7F;
			if (V)
			Log.v(TAG_SMS, "fragNumber = " + fragNumber);
			
			if ((fragment[0] & 0x80) == 0x80)
				currentFragments.max = fragNumber;

			if (V)
			Log.v(TAG_SMS, "currentFragments.max = " + currentFragments.max);
			
			currentFragments.bufs.put(fragNumber, fragment); 
			
			if (V)
			Log.v(TAG_SMS, "currentFragments.bufs = " + currentFragments.bufs.size());
			
			if ((currentFragments.max!=fragNumber && (currentFragments.bufs.size()==MESSAGE_SIZE - 1) || (currentFragments.max==fragNumber))) {
//			if (currentFragments.max == currentFragments.bufs.size()) {
				// Max pour tous les buffers, sauf pour le dernier
				int bufferSize = (currentFragments.bufs.size() - 1)
						* (MESSAGE_SIZE - 1)
						+ currentFragments.bufs.get(currentFragments.max).length
						- 1;
				if (V)
				Log.v("SMSFragment", "BufferSize = " + bufferSize);
				byte[] result = new byte[bufferSize];
				if (V) {
				Log.v("SMSFragment", "currentFragments.bufs.size = " + currentFragments.bufs.size());
				Log.v("SMSFragment", "currentFragments.bufs = " + currentFragments.bufs.toString());
				}
				for (int i = 0; i < currentFragments.bufs.size(); ++i) {
					byte[] r = currentFragments.bufs.get(i);
					if (r == null)
						throw new NullPointerException("Receiving a null message : readdata method");
					if (V)
					Log.v(TAG_SMS, "r.size = " + r.length);
					System.arraycopy(r, 1, result, i
							* (MESSAGE_SIZE - 1), r.length - 1);
					if (V) {
					Log.v(TAG_SMS, "sender = " + sender);
					Log.v(TAG_SMS, "max = " + currentFragments.max);
					Log.v(TAG_SMS, "bufs = " + currentFragments.bufs.toString());
					}
					//TODO Rajouter un message pour activer le pairing
				}
				if (_progressBar != null)
					_progressBar.cancel();
				currentFragments.bufs.clear();
				currentFragments.bufs.put(0, result);
				allFragments.put(sender, currentFragments);
			}
		}

	}

	static class Fragments {
		int max = -1;
		SparseArray<byte[]> bufs = new SparseArray<byte[]>();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		_viewer = inflater.inflate(R.layout.wainting_sms, container, false);
		_progressBar = new ProgressDialog(_viewer.getContext());
		_progressBar.setButton("Annuler", new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				_progressBar.cancel();
			}
		});
		_progressBar.setCancelable(true);
		_progressBar.setMessage("En attente de la réception d\'un message ...");
		_progressBar.show();
		return _viewer;
	}

}
