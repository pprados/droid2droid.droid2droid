package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.RemoteAndroidInfo.*;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;
import org.remoteandroid.ui.connect.qrcode.CaptureHandler;
import org.remoteandroid.ui.connect.qrcode.FinishListener;
import org.remoteandroid.ui.connect.qrcode.InactivityTimer;
import org.remoteandroid.ui.connect.qrcode.QRCodeScannerView;

import org.remoteandroid.ui.connect.qrcode.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;

public final class ConnectQRCodeFragment extends AbstractConnectFragment 
implements QRCodeScannerView.QRCodeResult
{
	public static int sDefaultCamera = 0; // API >=9 Camera.CameraInfo.CAMERA_FACING_BACK;
	
	private static final boolean NO_CAMERA = false;

	static class Cache
	{
		private CaptureHandler mHandler;

		private InactivityTimer mInactivityTimer;

	}

	Cache mCache;

	private LinearLayout mViewer;

	private TextView mUsage;
	
	private QRCodeScannerView mQRCode;
	private Camera mCamera;
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
	protected void updateStatus(int activeNetwork)
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
		mQRCodeScanner.setRotation(getActivity().getWindowManager().getDefaultDisplay().getRotation());
		
		DisplayMetrics metrics = new DisplayMetrics();
		getActivity().getWindowManager().getDefaultDisplay().getMetrics(
			metrics);

		Window window = getActivity().getWindow();

		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		mCache = null;

		if (mCache == null)
		{
			mCache = new Cache();
			mCache.mHandler = null;
			mCache.mInactivityTimer = new InactivityTimer(getActivity()); // FIXME
		}
		else
		{
			mCache.mInactivityTimer.setActivity(getActivity());
		}

		return mViewer;
	}

	@Override
	public void onResume()
	{
		super.onResume();
		mCache.mInactivityTimer.onResume();
		onConfigurationChanged(getResources().getConfiguration());
//		mQRCodeManager.onResume();
		
		openCamera();
	}

	@Override
	public void onPause()
	{
		super.onPause();
//		mQRCodeManager.onPause();
		closeCamera();
		
		if (I) Log.i(TAG_QRCODE, "onPause...");
		if (mCache.mHandler != null)
		{
			mCache.mHandler.quitSynchronously();
			mCache.mHandler = null;
		}

		mCache.mInactivityTimer.onPause();
//		if (!NO_CAMERA)
//			CameraManager.get().closeDriver();
	}

	private void openCamera()
	{
		if (Compatibility.VERSION_SDK_INT >= Compatibility.VERSION_GINGERBREAD)
		{
			mCamera = Camera.open(sDefaultCamera);
		}
		else
			mCamera = Camera.open();		
		mQRCodeScanner.setCamera(mCamera);
	}

	private void closeCamera()
	{
		mCamera.release();
		mCamera=null;
		mQRCodeScanner.setCamera(null);
	}

	@Override
	public void onDestroy()
	{
		if (I) Log.i(TAG_QRCODE, "onDestroy...");

		if (mCache != null && mCache.mInactivityTimer != null)

			mCache.mInactivityTimer.shutdown();
		super.onDestroy();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
//		CameraManager.get().closeDriver();
		mQRCodeScanner.setRotation(getActivity().getWindowManager().getDefaultDisplay().getRotation());
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
		if (I) Log.i( TAG_CONNECT, "handle valide decode " + rawResult);
		mCache.mInactivityTimer.onActivity();

		ConnectActivity activity = (ConnectActivity) getActivity();
		Messages.Candidates candidates;
		try
		{
			String s = rawResult.getText();

			byte[] data = new byte[s.length()];

			getBytes(s, 0, s.length(), data, 0);
			candidates = Messages.Candidates.parseFrom(data);
			showConnect(
				ProtobufConvs.toUris(Application.sAppContext,candidates)
				.toArray(new String[0]), 
				true,null); // FIXME: anonymous
		}
		catch (InvalidProtocolBufferException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
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
	public void getBytes(String s, int start, int end, byte[] data, int index)
	{
		char[] value = s.toCharArray();
		if (0 <= start && start <= end && end <= s.length())
		{

			try
			{
				for (int i = 0 + start; i < end; i++)
				{
					data[index++] = (byte) value[i];
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
