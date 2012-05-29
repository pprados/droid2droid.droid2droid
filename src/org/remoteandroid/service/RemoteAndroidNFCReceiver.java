package org.remoteandroid.service;

import static org.remoteandroid.Constants.NFC;
import static org.remoteandroid.internal.Constants.NDEF_MIME_TYPE;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_NFC;
import static org.remoteandroid.internal.Constants.W;

import java.util.Arrays;

import org.remoteandroid.discovery.Discover;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;

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
/** @deprecated */
public final class RemoteAndroidNFCReceiver extends Activity
{
	NfcAdapter mNfcAdapter;
	
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
										info.isDiscoverNFC=true;
										info.isBonded=Trusted.isBonded(info);
										Discover.getDiscover().discover(info);
									}
									catch (InvalidProtocolBufferException e) // $codepro.audit.disable logExceptions
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
