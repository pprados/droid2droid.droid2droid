/******************************************************************************
 *
 * droid2droid - Distributed Android Framework
 * ==========================================
 *
 * Copyright (C) 2012 by Atos (http://www.http://atos.net)
 * http://www.droid2droid.org
 *
 ******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
******************************************************************************/
package org.droid2droid.ui.connect.nfc;

import static org.droid2droid.Constants.DELAY_SHOW_TERMINATE;
import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.TAG_NFC;

import org.droid2droid.AsyncTaskWithException;
import org.droid2droid.NfcUtils;
import org.droid2droid.R;
import org.droid2droid.RAApplication;
import org.droid2droid.RemoteAndroidInfo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

@TargetApi(10)
public class WriteNfcActivity extends Activity
{
	public static final String EXTRA_INFO="info";

	private NfcAdapter mNfcAdapter;

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
		mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (mNfcAdapter == null)
		{
			if (E) Log.e(TAG_NFC,"Invalide adapter");
			finish();
			return;
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
		mText.setText(R.string.nfc_waiting);
		PendingIntent pendingIntent = 
				PendingIntent.getActivity(this, 0, 
					new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
	}

	@TargetApi(14)
	@SuppressWarnings("deprecation")
	@Override
	protected void onPause()
	{
		super.onPause();
		if (VERSION.SDK_INT>=VERSION_CODES.ICE_CREAM_SANDWICH)
			mNfcAdapter.setNdefPushMessage(null,this);
		else
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
				NfcUtils.writeTag(WriteNfcActivity.this,tag,mInfo);
				return null;
			}
			@Override
			protected void onPostExecute(Void result) 
			{
				mText.setText(R.string.nfc_writed);
				RAApplication.sHandler.postDelayed(new Runnable()
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