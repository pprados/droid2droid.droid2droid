package org.remoteandroid.ui.connect;

import java.util.Hashtable;

import org.remoteandroid.R;

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

	public static class SMSReceiver extends BroadcastReceiver {
		private final String ACTION_RECEIVE_SMS = "android.intent.action.DATA_SMS_RECEIVED";

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.e("Action", " = " + intent.getAction());
			if (!intent.getAction().equals(ACTION_RECEIVE_SMS))
				return;
			final String uri = intent.getDataString();

			// intent.getByteArrayExtra(name)
			Log.e("Action", uri);
			if (!uri.contains(_sendingPort.toString()))
				return;

			final Object[] pdus = (Object[]) intent.getExtras().get("pdus");
			final SmsMessage[] msgs = new SmsMessage[pdus.length];
			byte[] data = null;

			for (int i = 0; i < msgs.length; i++) {
				msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
				final String sender = msgs[i].getOriginatingAddress();
				data = msgs[i].getUserData();
				readData(data, sender);
			}
		}
	}

	static class Fragments {
		int max = -1;
		SparseArray<byte[]> bufs = new SparseArray<byte[]>();
	}

	static Hashtable<String, Fragments> allFragments = new Hashtable<String, SMSFragment.Fragments>();

	public static void readData(byte[] fragment, String sender) {
		Fragments currentFragments = new Fragments();
		int fragNumber = fragment[0] & 0x7F;
		if ((fragment[0] & 0x80) == 0x80)
			currentFragments.max = fragNumber;

		currentFragments.bufs.put(fragNumber, fragment);
		
		if ((currentFragments.max!=fragNumber && (currentFragments.bufs.size()==SmsMessage.MAX_USER_DATA_BYTES - 1) || (currentFragments.max==fragNumber))) {
//		if (currentFragments.max == currentFragments.bufs.size()) {
			// Max pour tous les buffers, sauf pour le dernier
			int bufferSize = (currentFragments.bufs.size() - 1)
					* (SmsMessage.MAX_USER_DATA_BYTES - 1)
					+ currentFragments.bufs.get(currentFragments.max).length
					- 1;
			byte[] result = new byte[bufferSize];
			for (int i = 0; i < currentFragments.bufs.size(); ++i) {
				byte[] r = currentFragments.bufs.get(i);
				if (r == null)
					; // Illegal arguem...
				System.arraycopy(r, 1, result, i
						* (SmsMessage.MAX_USER_DATA_BYTES - 1), r.length - 1);
				currentFragments.bufs.clear();
				currentFragments.bufs.put(currentFragments.max, result);
				Log.e("SMSFragment", "sender = " + sender);
				Log.e("SMSFragment", "max = " + currentFragments.max);
				Log.e("SMSFragment", "bufs = " + currentFragments.bufs.toString());
				allFragments.put(sender, currentFragments);
				_progressBar.cancel();
				//TODO Rajouter un message pour activer le pairing
			}
		}
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
