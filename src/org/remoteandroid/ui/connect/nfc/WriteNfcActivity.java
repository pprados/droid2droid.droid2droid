package org.remoteandroid.ui.connect.nfc;

import static org.remoteandroid.Constants.DELAY_SHOW_TERMINATE;

import org.remoteandroid.Application;
import org.remoteandroid.AsyncTaskWithException;
import org.remoteandroid.NfcUtils;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.pairing.Trusted;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

// TODO: améliorer l'interface
public class WriteNfcActivity extends Activity
{
	public static final String EXTRA_INFO="info";
	public static final String EXTRA_EXPOSE="expose";
	
	NfcAdapter mNfcAdapter;

	private RemoteAndroidInfo mInfo;
	private TextView mText;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getSystemService(NFC_SERVICE);
		setTitle(R.string.nfc_title);
		setContentView(R.layout.write_nfc);
		mText=(TextView)findViewById(R.id.help);
		Intent intent=getIntent();
		mInfo=(RemoteAndroidInfo)intent.getParcelableExtra(EXTRA_INFO);
		if (mInfo==null)
		{
			mInfo=Trusted.getInfo(this);
		}
		// Check for available NFC Adapter
		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (mNfcAdapter == null)
		{
			Toast.makeText(
				this, "NFC is not available", Toast.LENGTH_LONG).show();
			finish();
			return;
		}
	}

	public void onResume()
	{
		super.onResume();
		NdefMessage[] msgs;
		
		mText.setText(R.string.nfc_waiting);
		PendingIntent pendingIntent = 
				PendingIntent.getActivity(this, 0, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		mNfcAdapter.disableForegroundNdefPush(this);
	}

	@Override
	public void onNewIntent(Intent intent)
	{
		// onResume gets called after this to handle the intent
		setIntent(intent);
		final Tag tag=(Tag)intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		mText.setText(R.string.nfc_write);
		new AsyncTaskWithException<Void,Void,Void>()
		{
			@Override
			protected Void doInBackground(Void... params) throws Exception
			{
				NfcUtils.writeTag(WriteNfcActivity.this,tag);
				return null;
			}
			protected void onPostExecute(Void result) 
			{
				mText.setText(R.string.nfc_writed);
				Application.sHandler.postDelayed(new Runnable()
				{
					
					@Override
					public void run()
					{
						finish();
					}
				}, DELAY_SHOW_TERMINATE);
			}
			@Override
			protected void onException(Throwable e)
			{
				mText.setText(R.string.nfc_error);
			}

		}.execute();
	}

}