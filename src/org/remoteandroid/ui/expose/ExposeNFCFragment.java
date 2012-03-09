package org.remoteandroid.ui.expose;

import static org.remoteandroid.Constants.NDEF_MIME_TYPE;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NFC;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_NFC;
import static org.remoteandroid.internal.Constants.V;

import java.io.IOException;

import org.remoteandroid.AsyncTaskWithException;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.AbstractBodyFragment;
import org.remoteandroid.ui.AbstractFeatureTabActivity;
import org.remoteandroid.ui.EditPreferenceActivity;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;

import android.content.Context;
import android.content.Intent;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActionBar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class ExposeNFCFragment extends AbstractBodyFragment
implements AbstractBodyFragment.OnNfcEvent
{
	TextView mUsage;
	Button mWrite;
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET|FEATURE_NFC);
		}
		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			tabsAdapter.addTab(actionBar.newTab()
		        .setText(R.string.expose_nfc), ExposeNFCFragment.class, null);
		}
	}	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View main=inflater.inflate(R.layout.expose_nfc, container, false);
		mUsage=(TextView)main.findViewById(R.id.usage);
		mWrite=((Button)main.findViewById(R.id.write));
		mWrite.setEnabled(false);
		mWrite.setOnClickListener(new Button.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				final Tag tag=(Tag)getActivity().getIntent().getParcelableExtra(NfcAdapter.EXTRA_TAG);
				new AsyncTaskWithException<Void,Void,Integer>()
				{
					@Override
					protected Integer doInBackground(Void... params) throws Exception
					{
						return writeTag(tag);
					}
					protected void onPostExecute(Integer result) 
					{
						Toast.makeText(getActivity(), result, Toast.LENGTH_LONG).show();
						mWrite.setEnabled(false);
					}
					@Override
					protected void onException(Throwable e)
					{
						Toast.makeText(getActivity(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
						mWrite.setEnabled(false);
					}

				}.execute();
			}
		});
		return main;
	}
	
	@Override
	protected void updateStatus(int activeNetwork)
	{
		if (V) Log.v("Frag","ExposeQRCodeFragment.updateHelp...");
		if (mUsage==null) // Not yet initialized
			return;
		
		if ((activeNetwork & NetworkTools.ACTIVE_NFC)==0)
		{
			mUsage.setText(R.string.expose_nfc_help_no_nfc);
			mWrite.setVisibility(View.GONE);
		}
		else
		{
			mUsage.setText(R.string.expose_nfc_help);
			mWrite.setVisibility(View.VISIBLE);
		}
	}
	
	@Override
	public void onNfcTag(Intent intent)
	{
		//final Tag tag=(Tag)intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		mWrite.setEnabled(true);
	}
	
	private int writeTag(Tag tag) throws IOException, FormatException
	{
		
		RemoteAndroidInfo info=Trusted.getInfo(getActivity());
//RemoteAndroidInfoImpl ii=((RemoteAndroidInfoImpl)info)	;
//ii.uris.clear();ii.uris.add("ip://192.168.0.63:19876");
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
				return R.string.expose_nfc_error_unknown_format;
			}
			
			if (!tech.isWritable())
			{
				if (E) Log.e(TAG_NFC, PREFIX_LOG+"NFC No writable");
				return R.string.expose_nfc_error_no_writable;
			}
			tech.connect();
			NdefMessage msg=AbstractFeatureTabActivity.createNdefMessage(getActivity(),info,true);
			tech.writeNdefMessage(msg);
			return R.string.expose_nfc_writed;
		}
		finally
		{
			try
			{
				if (tech!=null) tech.close();
			}
			catch (IOException e)
			{
				if (E) Log.e("NFC", "IOException while closing MifareUltralight...", e);
			}
		}
	}
}
