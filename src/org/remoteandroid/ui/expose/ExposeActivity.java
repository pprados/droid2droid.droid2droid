package org.remoteandroid.ui.expose;


import static org.remoteandroid.Constants.NFC;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.TAG_NFC;
import static org.remoteandroid.internal.Constants.W;

import java.util.Arrays;

import org.remoteandroid.R;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.AbstractFeatureTabActivity;
import org.remoteandroid.ui.EditPreferenceActivity;
import org.remoteandroid.ui.FeatureTab;

import com.google.protobuf.InvalidProtocolBufferException;

import android.app.PendingIntent;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.nfc.NfcManager;
import android.nfc.Tag;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;


public final class ExposeActivity extends AbstractFeatureTabActivity
{
	private static final FeatureTab[] sTabs=
	{
		new ExposeQRCodeFragment.Provider(),
//		new ExposeSoundFragment.Provider(),
//		new ExposeWifiDirectFragment.Provider(),
//		new ExposeBumpFragment.Provider(),
		new ExposeTicketFragment.Provider(),
		new ExposeNFCFragment.Provider(),
	};

	@Override
	protected FeatureTab[] getFeatureTabs()
	{
		return sTabs;
	}
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
    	setTitle(R.string.expose);
		onNfcCreate();
	}
	
}
