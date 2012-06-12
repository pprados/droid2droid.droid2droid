package org.remoteandroid.ui.expose;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;

import java.io.UnsupportedEncodingException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Hashtable;

import org.remoteandroid.RAApplication;
import org.remoteandroid.R;
import org.remoteandroid.binder.AbstractSrvRemoteAndroid;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.Pairing;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.AbstractBodyFragment;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public final class ExposeQRCodeFragment extends AbstractBodyFragment
{
	public static class Provider extends FeatureTab
	{	
		
		Provider()
		{
			super(FEATURE_SCREEN);
		}

		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			Tab tab=actionBar.newTab()
					.setIcon(R.drawable.ic_tab_qrcode)
			        .setText(R.string.expose_qrcode);
			tabsAdapter.addTab(tab, ExposeQRCodeFragment.class, null);
		}
	}
	
	private static final int WHITE = 0xFFFFFFFF;
	private static final int BLACK = 0xFF000000;

	private TextView mUsage;
	private ImageView mImg;
	private float mScreenBrightness;

	private int mMax;

	@Override
	public void onResume()
	{
		super.onResume();
//		WindowManager.LayoutParams layoutParams = getActivity().getWindow().getAttributes();
//		mScreenBrightness=layoutParams.screenBrightness;
//		layoutParams.screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
//		layoutParams.flags|=LayoutParams.FLAG_KEEP_SCREEN_ON;
//		getActivity().getWindow().setAttributes(layoutParams);
		boolean anoQRCode=getActivity().getPreferences(Context.MODE_PRIVATE).getBoolean("ano.qrcode", false);
		if (anoQRCode)
			Pairing.enableTemporaryAcceptAnonymous();
		mImg.requestFocus();
	}

	@Override
	public void onPause()
	{
		super.onPause();
//		WindowManager.LayoutParams layoutParams = getActivity().getWindow().getAttributes();
//		layoutParams.screenBrightness = mScreenBrightness;
//		layoutParams.flags&=~LayoutParams.FLAG_KEEP_SCREEN_ON;
//		getActivity().getWindow().setAttributes(layoutParams);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View main=inflater.inflate(R.layout.expose_qrcode, container, false);

		mUsage=(TextView)main.findViewById(R.id.usage);
		mImg=(ImageView)main.findViewById(R.id.qrcode);
		main.setFocusable(false);
		return main;
	}

	int mOldActiveNetwork;
	@Override
	protected void updateStatus(int activeNetwork)
	{
		if (V) Log.v(TAG_QRCODE,"ExposeQRCodeFragment.updateHelp...");
		if (mUsage==null) // Not yet initialized
			return;
		if (mOldActiveNetwork==activeNetwork)
			return;
		mOldActiveNetwork=activeNetwork;
		boolean airplane=Settings.System.getInt(getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
		if (airplane)
		{
			mUsage.setText(R.string.expose_qrcode_help_airplane);
			mImg.setVisibility(View.GONE);
		}
		else
		{
			mUsage.setText(R.string.expose_qrcode_help);
			mImg.setVisibility(View.VISIBLE);
			
			WindowManager manager = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
			Display display = manager.getDefaultDisplay();
			int width = display.getWidth();
			int height = display.getHeight();
			int smallerDimension = width < height ? width : height;
			smallerDimension = smallerDimension * 7 / 8;
			final int smallerDim = smallerDimension;

			mImg.setBackgroundColor(WHITE);
			mMax=getResources().getInteger(R.integer.expose_qrcode_maxsize);
			if (mMax==0)
				mMax=smallerDim;
			mImg.setMinimumHeight(mMax);
			mImg.setMinimumWidth(mMax);

			new AsyncTask<Void, Void, Bitmap>()
			{
				@Override
				protected Bitmap doInBackground(Void... params)
				{
					return buildQRCode(RAApplication.sAppContext,mMax);
				}

				@Override
				protected void onPostExecute(Bitmap bitmap)
				{
					if (bitmap != null)
					{
						if (getActivity()!=null)
						{
							mImg.setImageBitmap(bitmap);
//							WindowManager.LayoutParams layoutParams = getActivity().getWindow().getAttributes();
//							mScreenBrightness=layoutParams.screenBrightness;
//							layoutParams.screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
//							layoutParams.flags|=LayoutParams.FLAG_KEEP_SCREEN_ON;
//							getActivity().getWindow().setAttributes(layoutParams);
						}
					}
				}
			}.execute();
		}
	}

	@Override
	public void onPageUnselected()
	{
		super.onPageUnselected();
//		WindowManager.LayoutParams layoutParams = getActivity().getWindow().getAttributes();
//		layoutParams.screenBrightness = mScreenBrightness;
//		layoutParams.flags&=~LayoutParams.FLAG_KEEP_SCREEN_ON;
//		getActivity().getWindow().setAttributes(layoutParams);
	}
//---------------------------
	public static Bitmap buildQRCode(Context context,int smallerDimension)
	{
		BarcodeFormat format = BarcodeFormat.QR_CODE;

		try
		{
			Messages.Candidates candidates = Trusted.getConnectMessage(context);
			byte[] data = candidates.toByteArray();		
			if (data.length==0)
				return null;
			String contents = null;
			contents = new String(data, "ISO-8859-1");
			return encodeAsBitmap(contents, format, smallerDimension);
		}
		catch (UnknownHostException e)
		{
			if (E) Log.e(TAG_QRCODE,PREFIX_LOG+"Error when create QRCode ("+e.getMessage()+")");
			return null;
		}
		catch (SocketException e)
		{
			if (E) Log.e(TAG_QRCODE,PREFIX_LOG+"Error when create QRCode ("+e.getMessage()+")");
			return null;
		}
		catch (WriterException e)
		{
			if (E) Log.e(TAG_QRCODE,PREFIX_LOG+"Error when create QRCode ("+e.getMessage()+")");
			return null;
		} catch (UnsupportedEncodingException e) 
		{
			if (E) Log.e(TAG_QRCODE,PREFIX_LOG+"Error when create QRCode ("+e.getMessage()+")");
			return null;
		}
	}

	private static Bitmap encodeAsBitmap(String contents, BarcodeFormat format, int dimension)
			throws WriterException
	{
		if (V) Log.v(TAG_QRCODE,"Calculate QRCode");
		 Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>(2);
		 hints.put(EncodeHintType.CHARACTER_SET, QRCODE_BYTE_MODE_ENCODING); // WARNING: Patch in QRCodeWriter to accept this kind of char set

		Writer writer = new QRCodeWriter();
		BitMatrix result = writer.encode(contents, format, 0, 0, hints);
		int width = result.getWidth();
		int height = result.getHeight();
		int[] pixels = new int[width * height];
		// All are 0, or black, by default
		for (int y = 0; y < height; y++)
		{
			int offset = y * width;
			for (int x = 0; x < width; x++)
			{
				pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
			}
		}

		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
		Bitmap dimbitmap = Bitmap.createScaledBitmap(bitmap, dimension, dimension, false);
		bitmap.recycle();
		return dimbitmap;
	}

}
