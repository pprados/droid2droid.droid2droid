package org.remoteandroid.ui.connect;

import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.*;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.W;

import java.util.Arrays;

import org.remoteandroid.R;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.ui.AbstractBodyFragment;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.UninitializedMessageException;

public final class ConnectNFCFragment extends AbstractConnectFragment
implements AbstractBodyFragment.OnNfcEvent
{
	private View mViewer;
	private TextView mUsage;
	
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET|FEATURE_NFC);
		}
		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			Tab tab=actionBar.newTab()
					.setIcon(R.drawable.ic_tab_nfc)
			        .setText(R.string.connect_nfc);
			tabsAdapter.addTab(tab, ConnectNFCFragment.class, null);
		}
	}	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		setProgressBarIndeterminateVisibility(true);
		mViewer = (View) inflater.inflate(R.layout.connect_nfc, container, false);
		mUsage = (TextView)mViewer.findViewById(R.id.usage);
		return mViewer;
	}
	
	@Override
	protected void updateStatus(int activeNetwork)
	{
		if (mUsage==null)
			return;
		boolean airplane=Settings.System.getInt(getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
		if (airplane)
		{
			mUsage.setText(R.string.connect_nfc_help_airplane);
		}
		else
		if ((activeNetwork & (NetworkTools.ACTIVE_NFC))!=0)
		{
			mUsage.setText(R.string.connect_nfc_help);
		}
		else
		{
			mUsage.setText(R.string.connect_nfc_help_nfc);
		}
	}
	
	@Override
	public void onNfcTag(Intent intent)
	{
		Messages.Identity identity=nfcCheckDiscovered(intent);
		if (identity==null) 
		{
			Toast.makeText(getActivity(), R.string.connect_nfc_invalide_tag, Toast.LENGTH_LONG).show();
			return; // Bad format
		}
		RemoteAndroidInfoImpl info=ProtobufConvs.toRemoteAndroidInfo(getActivity(),identity);
		showConnect(info.getUris(), getConnectActivity().mFlags,null);
	}
	private Messages.Identity nfcCheckDiscovered(Intent intent)
	{
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) 
		{
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
		        				return Messages.Identity.newBuilder().mergeFrom(record.getPayload()).build();
							}
							catch (InvalidProtocolBufferException e)
							{
								if (W) Log.d(TAG_NFC,PREFIX_LOG+"Invalide data");
							}
	        				catch (UninitializedMessageException e)
	        				{
								if (W) Log.d(TAG_NFC,PREFIX_LOG+"Invalide data");
	        				}
	        			}
	        		}
	            }
	        }
		}
		return null;
	}
    
	
}
