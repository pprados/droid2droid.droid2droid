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

import org.remoteandroid.R;
import org.remoteandroid.internal.Base64;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.ui.expose.InputExpose;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class SMSFragment extends AbstractBodyFragment {

	static final int TIMEOUT_WAIT_SMS=1000000;
	private static final short SMS_PORT = 6800;
	private View _viewer;
	private static ProgressDialog _progressBar;
	static final int SMS_HEADER = 10;
	public static final int MESSAGE_SIZE = SmsMessage.MAX_USER_DATA_BYTES - SMS_HEADER;
	
	static BlockingQueue<byte[]> sQueue=new ArrayBlockingQueue<byte[]>(10, false);
			
	public static class SMSReceiver extends BroadcastReceiver {
		private final String ACTION_RECEIVE_SMS = "android.intent.action.DATA_SMS_RECEIVED";
		private Hashtable<String, Fragments> mAllFragments = new Hashtable<String, SMSFragment.Fragments>();
		
		@Override
		public void onReceive(Context context, Intent intent) {
			if (V)
			Log.v(TAG_SMS, "Action = " + intent.getAction());
			if (!intent.getAction().equals(ACTION_RECEIVE_SMS))
				return;
			final String uri = intent.getDataString();

			// intent.getByteArrayExtra(name)
			if (V) Log.v(TAG_SMS, "Action uri = " + uri);
			if (Uri.parse(uri).getPort()!=SMS_PORT)
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
		//TODO: nettoyage temporelle sur les fragments dans mAllFragments
		
		private void readData(byte[] fragment, String sender) {
			
			Fragments currentFragments = mAllFragments.get(sender);
			if (currentFragments==null)
			{
				currentFragments=new Fragments();
				mAllFragments.put(sender, currentFragments);
			}

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
			
			if ((currentFragments.max!=fragNumber && (currentFragments.bufs.size()==MESSAGE_SIZE - 1) 
					|| 
					(currentFragments.max==fragNumber && currentFragments.bufs.size()==fragNumber))) {
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
					{
						currentFragments.bufs.clear();
						mAllFragments.remove(sender);
						if (W) Log.w(TAG_SMS,PREFIX_LOG+"Receive invalide SMS");
						throw new NullPointerException("Receiving a null message : readdata method");
					}
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
				mAllFragments.remove(sender);
				sQueue.add(result);
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
//		_progressBar = new ProgressDialog(_viewer.getContext());
//		_progressBar.setButton("Annuler", new OnClickListener() {
//			
//			@Override
//			public void onClick(DialogInterface dialog, int which) {
//				_progressBar.cancel();
//			}
//		});
//		_progressBar.setCancelable(true);
//		_progressBar.setMessage("En attente de la réception d\'un message ...");
//		_progressBar.show();
		
		ConnectActivity.FirstStep firstStep=new ConnectActivity.FirstStep()
		{
			public int run(ConnectActivity.TryConnection tryConn) 
			{
				try
				{
					// TODO: gerer le cancel
					byte[] bytes=sQueue.poll(TIMEOUT_WAIT_SMS,TimeUnit.MILLISECONDS);
					//TIMEOUT_WAIT_SMS,TimeUnit.MILLISECONDS
					Messages.Candidates candidates=Messages.Candidates.parseFrom(bytes);
					tryConn.setUris(ProtobufConvs.toUris(candidates));
					return 0;
				}
				catch (Exception e)
				{
					// TODO
					if (E) Log.e(TAG_CONNECT,PREFIX_LOG+"Error when retreive shorten ticket",e);
					return R.string.connect_input_message_error_get_internet; // TODO: message erreur
				}
			}
		};
		ConnectActivity activity=(ConnectActivity)getActivity();
		activity.tryConnect(firstStep,new ArrayList<String>(),((ConnectActivity)getActivity()).isAcceptAnonymous());

		return _viewer;
	}

}
