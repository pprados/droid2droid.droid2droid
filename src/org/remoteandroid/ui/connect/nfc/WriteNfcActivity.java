package org.remoteandroid.ui.connect.nfc;

import java.io.IOException;

import org.remoteandroid.AsyncTaskWithException;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.EditPreferenceActivity;
import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

// TODO: améliorer l'interface
public class WriteNfcActivity extends Activity
{
	public static final String EXTRA_INFO="info";
	public static final String EXTRA_EXPOSE="expose";
	
	NfcAdapter mNfcAdapter;

	private RemoteAndroidInfo mInfo;
	private boolean mExpose;
	private TextView mText;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		getSystemService(NFC_SERVICE);
		setContentView(R.layout.write_nfc);
		mText=(TextView)findViewById(R.id.help);
		Intent intent=getIntent();
		mInfo=(RemoteAndroidInfo)intent.getParcelableExtra(EXTRA_INFO);
		mExpose=intent.getBooleanExtra(EXTRA_EXPOSE,true);
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
				writeTag(tag);
				return null;
			}
			protected void onPostExecute(Void result) 
			{
				mText.setText(R.string.nfc_writed);
			}
			@Override
			protected void onException(Throwable e)
			{
				mText.setText(R.string.nfc_error);
			}

		}.execute();
	}

	private void writeTag(Tag tag) throws IOException, FormatException
	{
		
		Ndef tech = Ndef.get(tag);
		try
		{
			String[] techList=tag.getTechList();
			String ndefName=Ndef.class.getName();
			int i=0;
			for (;i<techList.length;++i)
			{ 
				if (techList[i].equals(ndefName))
					break;
			}
			if (i==techList.length) 
			{
				Toast.makeText(this, R.string.nfc_error_unknown_format, Toast.LENGTH_LONG).show();
				return;
			}
			
			if (!tech.isWritable())
			{
				if (E) Log.e(TAG_NFC, PREFIX_LOG+"NFC No writable");
				Toast.makeText(this, R.string.nfc_error_no_writable, Toast.LENGTH_LONG).show();
				return;
			}
			tech.connect();
			NdefMessage msg=EditPreferenceActivity.createNdefMessage(this,mInfo,mExpose);
			tech.writeNdefMessage(msg);
		}
		finally
		{
			try
			{
				if (tech!=null) tech.close();
			}
			catch (IOException e)
			{
				Log.e("NFC", "IOException while closing MifareUltralight...", e);
			}
		}
	}

}