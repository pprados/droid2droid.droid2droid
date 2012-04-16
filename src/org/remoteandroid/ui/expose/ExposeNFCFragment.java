package org.remoteandroid.ui.expose;

import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NFC;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.Constants.*;

import java.io.IOException;

import org.remoteandroid.AsyncTaskWithException;
import org.remoteandroid.NfcUtils;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.AbstractBodyFragment;
import org.remoteandroid.ui.AbstractFeatureTabActivity;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;

import android.content.Context;
import android.content.Intent;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;

public final class ExposeNFCFragment extends AbstractBodyFragment
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
			Tab tab=actionBar.newTab()
					.setIcon(R.drawable.ic_tab_nfc)
			        .setText(R.string.expose_nfc);
			tabsAdapter.addTab(tab, ExposeNFCFragment.class, null);
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
				if (tag!=null)
				{
					new AsyncTaskWithException<Void,Void,Integer>()
					{
						@Override
						protected Integer doInBackground(Void... params) throws Exception
						{
							return NfcUtils.writeTag(getActivity(),tag);
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
			}
		});
		return main;
	}
	
	@Override
	protected void updateStatus(int activeNetwork)
	{
		if (V) Log.v(TAG_QRCODE,"ExposeQRCodeFragment.updateHelp...");
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
		// Detect a NFC tag. It's possible to write it with my current infos.
		mWrite.setEnabled(true);
	}
	
}
