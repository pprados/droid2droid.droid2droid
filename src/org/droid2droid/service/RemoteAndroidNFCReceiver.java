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
package org.droid2droid.service;

import static org.droid2droid.Constants.NFC;
import static org.droid2droid.internal.Constants.NDEF_MIME_TYPE;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.TAG_NFC;
import static org.droid2droid.internal.Constants.W;

import java.util.Arrays;

import org.droid2droid.discovery.Discover;
import org.droid2droid.internal.Messages;
import org.droid2droid.internal.ProtobufConvs;
import org.droid2droid.internal.RemoteAndroidInfoImpl;
import org.droid2droid.pairing.Trusted;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

// Activity to broadcast the RemoteAndroidInfo from a NFC tag.
@TargetApi(10)
public final class RemoteAndroidNFCReceiver extends Activity
{
	private NfcAdapter mNfcAdapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		checkNdefDiscovered();
		finish();
		overridePendingTransition(0, 0);
	}
	
	private void checkNdefDiscovered()
	{
		if (NFC)
		{
			NfcManager nfcManager=(NfcManager)getSystemService(NFC_SERVICE);
			if (nfcManager!=null)
			{
				mNfcAdapter=nfcManager.getDefaultAdapter();
				Intent intent=getIntent();
				if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) 
				{
					// Check the caller. Refuse spoof events
					checkCallingPermission("com.android.nfc.permission.NFCEE_ADMIN");
	
					Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			        if (rawMsgs != null) 
			        {
			        	for (int i = 0; i < rawMsgs.length; i++) 
			            {
			        		NdefMessage msg = (NdefMessage) rawMsgs[i];
			        		for (NdefRecord record:msg.getRecords())
			        		{
			        			if ((record.getTnf()==NdefRecord.TNF_MIME_MEDIA) 
			        					&& Arrays.equals(NDEF_MIME_TYPE, record.getType()))
			        			{
			        				try
									{
										RemoteAndroidInfoImpl info=ProtobufConvs.toRemoteAndroidInfo(this,Messages.Identity.newBuilder().mergeFrom(record.getPayload()).build());
										info.isDiscoverByNFC=true;
										info.isBonded=Trusted.isBonded(info);
										Discover.getDiscover().discover(info);
									}
									catch (InvalidProtocolBufferException e)
									{
										if (W) Log.d(TAG_NFC,PREFIX_LOG+"Invalide data");
									}
			        			}
			        		}
			            }
			        }
			    }
			}
		}			
	}
	
}
