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
package org.droid2droid.ui.connect;

import static org.droid2droid.Constants.TAG_QRCODE;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_CAMERA;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_NET;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.V;

import java.io.IOException;

import org.droid2droid.R;
import org.droid2droid.RAApplication;
import org.droid2droid.internal.Messages;
import org.droid2droid.internal.NetworkTools;
import org.droid2droid.internal.ProtobufConvs;
import org.droid2droid.ui.FeatureTab;
import org.droid2droid.ui.TabsAdapter;
import org.droid2droid.ui.connect.qrcode.QRCodeScannerView;

import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.zxing.Result;

// TODO: After 3 minutes, stop the camera
public final class ConnectQRCodeFragment extends AbstractConnectFragment 
implements QRCodeScannerView.QRCodeResult
{
	public static int sDefaultCamera = 0; // API >=9 Camera.CameraInfo.CAMERA_FACING_BACK;
	
	private LinearLayout mViewer;

	private TextView mUsage;
	
	private QRCodeScannerView mQRCodeScanner;

	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN | FEATURE_CAMERA | FEATURE_NET);
		}

		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			Tab tab=actionBar.newTab()
					.setIcon(R.drawable.ic_tab_qrcode)
					.setText(R.string.connect_qrcode);
			tabsAdapter.addTab(tab, ConnectQRCodeFragment.class, null);
		}
	}

	@Override
	protected void onUpdateActiveNetwork(int activeNetwork)
	{
		if (mUsage==null)
			return;
		boolean airplane = Settings.System.getInt(
			getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
		if (airplane)
		{
			mUsage.setText(R.string.connect_qrcode_help_airplane);
		}
		else if ((activeNetwork & (NetworkTools.ACTIVE_BLUETOOTH | NetworkTools.ACTIVE_LOCAL_NETWORK)) != 0)
		{
			mUsage.setText(R.string.connect_qrcode_help);
		}
		else
		{
			mUsage.setText(R.string.connect_qrcode_help_ip_bt);
		}
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer = (LinearLayout) inflater.inflate(R.layout.connect_qrcode, container, false);
		mUsage = (TextView) mViewer.findViewById(R.id.usage);
		mQRCodeScanner = (QRCodeScannerView) mViewer.findViewById(R.id.qrcode);
		mQRCodeScanner.setOnResult(this);
		return mViewer;
	}

	@Override
	public void onResume()
	{
		super.onResume();
		openCamera();		
	}

	@Override
	public void onPause()
	{
		super.onPause();
		closeCamera();
	}

	@Override
	public void onPageSelected()
	{
		super.onPageSelected();
		mQRCodeScanner.startScan();
	}
	@Override
	public void onPageReSelected()
	{
		super.onPageReSelected();
		mQRCodeScanner.startScan();
	}
	@Override
	public void onPageUnselected()
	{
		super.onPageUnselected();
		mQRCodeScanner.stopScan();
	}
	private void openCamera()
	{
		try
		{
			if (V) Log.v(TAG_QRCODE,"open camera...");
			Window window = getActivity().getWindow();
			window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			mQRCodeScanner.setCamera(sDefaultCamera);
		}
		catch (IOException e)
		{
			if (E) Log.e(TAG_QRCODE,"Impossible to open camera.",e);
		}
	}

	private void closeCamera()
	{
		try
		{
			if (V) Log.v(TAG_QRCODE,"close camera...");
			Window window = getActivity().getWindow();
			window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			mQRCodeScanner.setCamera(-1);
		}
		catch (IOException e)
		{
			if (E) Log.e(TAG_QRCODE,"Impossible to open camera.",e);
		}
	}

	/**
	 * A valid qrcode has been found, so give an indication of success and show
	 * the results.
	 * 
	 * @param rawResult The contents of the barcode.
	 */
	@Override
	public void onQRCode(Result rawResult)
	{
		mQRCodeScanner.stopScan();
		Messages.Candidates candidates;
		try
		{
			String s = rawResult.getText();
			byte[] data = new byte[s.length()];
			getBytes(s, 0, s.length(), data, 0);
			candidates = Messages.Candidates.parseFrom(data);
			showConnect(
				ProtobufConvs.toUris(RAApplication.sAppContext,candidates)
				.toArray(new String[0]), 
				getConnectActivity().mFlags,null);
		}
		catch (InvalidProtocolBufferException e)
		{
			if (E) Log.d(TAG_QRCODE,"Error when analyse qrcode from QRCode.",e);
		}
	}

	/*
	 * WARNING: This method SHOULD NOT be here and is only used to bypass the
	 * bug in the String method getBytes(int start, int end, byte[] data, int
	 * index) in android honeycomb 3.2 (and maybe earlier honeycomb versions)
	 * This method always return a StringIndexOutOfBoundsException in this
	 * version of android Note: Earlier versions (gingerbread) are not affected
	 * by this bug
	 */
	private void getBytes(String s, int start, int end, byte[] data, int index)
	{
		final char[] value = s.toCharArray();
		if (0 <= start && start <= end && end <= s.length())
		{

			try
			{
				for (int i = start; i < end; i++)
				{
					data[index++] = (byte)(value[i] & 0xFF);
				}
			}
			catch (ArrayIndexOutOfBoundsException e)
			{
				throw new StringIndexOutOfBoundsException();
			}
		}
		else
		{
			throw new StringIndexOutOfBoundsException();
		}
	}
}
