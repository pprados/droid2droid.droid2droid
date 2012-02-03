package org.remoteandroid.ui.expose;

import static org.remoteandroid.Constants.TAG_QRCODE;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;

import java.net.SocketException;
import java.net.UnknownHostException;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;
import org.remoteandroid.ui.connect.old.AbstractBodyFragment;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActionBar;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class ExposeQRCodeFragment extends AbstractBodyFragment
{
	public static class Provider extends FeatureTab
	{	
		
		Provider()
		{
			super(FEATURE_SCREEN);
		}

		@Override
		public void createTab(FragmentActivity activity,TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			tabsAdapter.addTab(actionBar.newTab()
		        .setText(R.string.expose_qrcode), ExposeQRCodeFragment.class, null);
		}
	}
	
	private static final int WHITE = 0xFFFFFFFF;

	private static final int BLACK = 0xFF000000;

	private ImageView mImg;

	private float mScreenBrightness;

	private int mMax;

	@Override
	public void onResume()
	{
		super.onResume();
		WindowManager.LayoutParams layoutParams = getActivity().getWindow().getAttributes();
		mScreenBrightness=layoutParams.screenBrightness;
		layoutParams.screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
		layoutParams.flags|=LayoutParams.FLAG_KEEP_SCREEN_ON;
		getActivity().getWindow().setAttributes(layoutParams);
		mImg.requestFocus();
	}

	@Override
	public void onPause()
	{
		super.onPause();
		WindowManager.LayoutParams layoutParams = getActivity().getWindow().getAttributes();
		layoutParams.screenBrightness = mScreenBrightness;
		layoutParams.flags&=~LayoutParams.FLAG_KEEP_SCREEN_ON;
		getActivity().getWindow().setAttributes(layoutParams);
	}

	private static Bitmap buildQRCode(int smallerDimension)
	{
		BarcodeFormat format = BarcodeFormat.QR_CODE;

		try
		{
			Messages.Candidates candidates = Trusted.getConnectMessage(Application.sAppContext);
			byte[] data = candidates.toByteArray();
			if (data.length==0)
				return null;
			String contents = null;
			contents = new String(data, 0);
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
		}
	}

	private static Bitmap encodeAsBitmap(String contents, BarcodeFormat format, int dimension)
			throws WriterException
	{
//		 Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>(2);
//		 hints.put(EncodeHintType.CHARACTER_SET, null);

		Writer writer = new QRCodeWriter();
		BitMatrix result = writer.encode(contents, format, 0, 0, null/*hints*/);
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

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View main=inflater.inflate(R.layout.expose_qrcode, container, false);
		WindowManager manager = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
		int width = display.getWidth();
		int height = display.getHeight();
		int smallerDimension = width < height ? width : height;
		smallerDimension = smallerDimension * 7 / 8;
		final int smallerDim = smallerDimension;

		mImg=(ImageView)main.findViewById(R.id.qrcode);
		mImg.setBackgroundColor(WHITE);
		mMax=getResources().getInteger(R.integer.expose_qrcode_maxsize);
		if (mMax==0)
			mMax=smallerDim;
		mImg.setMinimumHeight(mMax);
		mImg.setMinimumWidth(mMax);
//		setContentView(mImg);

		new AsyncTask<Void, Void, Bitmap>()
		{
			@Override
			protected Bitmap doInBackground(Void... params)
			{
				return buildQRCode(mMax);
			}

			@Override
			protected void onPostExecute(Bitmap bitmap)
			{
				if (bitmap != null)
				{
					mImg.setImageBitmap(bitmap);
				}
			}
		}.execute();
		main.setFocusable(false);
		return main;
	}
	

}
