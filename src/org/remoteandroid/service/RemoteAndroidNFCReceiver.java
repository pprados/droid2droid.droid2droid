package org.remoteandroid.service;

import static org.remoteandroid.Constants.NDEF_MIME_TYPE;
import static org.remoteandroid.Constants.NFC;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_NFC;
import static org.remoteandroid.internal.Constants.W;

import java.util.Arrays;

import org.remoteandroid.Application;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;

import com.google.protobuf.InvalidProtocolBufferException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

// Activity to broadcast the RemoteAndroidInfo from a NFC tag.
public class RemoteAndroidNFCReceiver extends Activity
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
		NfcManager nfcManager=(NfcManager)getSystemService(NFC_SERVICE);
		if (NFC && nfcManager!=null)
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
			        				Messages.BroadcastMsg bmsg=Messages.BroadcastMsg.newBuilder().mergeFrom(record.getPayload()).build();
			        				if (bmsg.getType()==Messages.BroadcastMsg.Type.CONNECT)
			        				{
										RemoteAndroidInfoImpl info=ProtobufConvs.toRemoteAndroidInfo(this,bmsg.getIdentity());
										info.isDiscoverNFC=true;
										info.isBonded=Trusted.isBonded(info);
										intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
										intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
										Application.sAppContext.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
			        				}
			        				else
										if (W) Log.d(TAG_NFC,PREFIX_LOG+"Connect tag. Ignore.");
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
