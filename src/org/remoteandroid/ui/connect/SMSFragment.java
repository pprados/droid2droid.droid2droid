package org.remoteandroid.ui.connect;

import java.util.Dictionary;

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
import android.widget.Toast;

public class SMSFragment extends AbstractBodyFragment {

	private static final Short _sendingPort = 6800;
	private View _viewer;
	private ProgressDialog _progressBar;

	public class SMSReceiver extends BroadcastReceiver {
		private final String ACTION_RECEIVE_SMS = "android.intent.action.DATA_SMS_RECEIVED";

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.e("Action", " = " + intent.getAction());
			if (!intent.getAction().equals(ACTION_RECEIVE_SMS))
				return;
			String uri = intent.getDataString();

			// intent.getByteArrayExtra(name)
			Log.e("Action", uri);
			if (!uri.contains(_sendingPort.toString()))
				return;

			Object[] pdus = (Object[]) intent.getExtras().get("pdus");
			SmsMessage[] msgs = new SmsMessage[pdus.length];
			byte[] data = null;

			String sender = "";
			String msgTxt = "";
			for (int i = 0; i < msgs.length; i++) {
				msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
				sender = msgs[i].getOriginatingAddress();
				data = msgs[i].getUserData();
				readData(data, sender);
			}

			Toast.makeText(context, sender + " - " + msgTxt, Toast.LENGTH_SHORT)
					.show();
		}
	}

	static class Fragments {
		int max = -1;
		SparseArray<byte[]> bufs = new SparseArray<byte[]>();
	}

	Dictionary<String, Fragments> allFragments;

	public void readData(byte[] fragment, String sender) {
		Fragments currentFragments = new Fragments();
		int fragNumber = fragment[0] & ~0x80;
		if ((fragment[0] & 0x80) == 0x80)
			currentFragments.max = fragNumber;

		currentFragments.bufs.put(fragNumber, fragment);
		
		if (currentFragments.max == currentFragments.bufs.size()) {
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
						* SmsMessage.MAX_USER_DATA_BYTES - 1, r.length - 1);
				currentFragments.bufs.clear();
				currentFragments.bufs.put(currentFragments.max, result);
				allFragments.put(sender, currentFragments);
			}
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		_viewer = inflater.inflate(R.layout.wainting_sms, container, false);
//		_progressBar = ProgressDialog.show(_viewer.getContext(), "", 
//                "Loading. Please wait...", true);
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
