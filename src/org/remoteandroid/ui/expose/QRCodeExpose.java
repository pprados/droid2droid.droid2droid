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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
class QRCodeExpose extends Expose
{
	public static class QRCodeActivity extends Activity
	{
		private static final int WHITE = 0xFFFFFFFF;

		private static final int BLACK = 0xFF000000;

		private ImageView mImg;

		private float mScreenBrightness;

		private int mMax;
		// http://barcode4j.sourceforge.net/ Pour encodage datamatrix
		@Override
		protected void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
			Display display = manager.getDefaultDisplay();
			int width = display.getWidth();
			int height = display.getHeight();
			int smallerDimension = width < height ? width : height;
			smallerDimension = smallerDimension * 7 / 8;
			final int smallerDim = smallerDimension;
			mImg = new ImageView(this);
			mImg.setBackgroundColor(WHITE);
			mMax=getResources().getInteger(R.integer.expose_qrcode_maxsize);
			if (mMax==0)
				mMax=smallerDim;
			mImg.setMinimumHeight(mMax);
			mImg.setMinimumWidth(mMax);
			setContentView(mImg);

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
			WindowManager.LayoutParams layoutParams=getWindow().getAttributes();
			mScreenBrightness=layoutParams.screenBrightness;
			getWindow().setFlags(LayoutParams.FLAG_KEEP_SCREEN_ON,LayoutParams.FLAG_KEEP_SCREEN_ON);
		}

		@Override
		protected void onResume()
		{
			super.onResume();
			WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
			layoutParams.screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
			getWindow().setAttributes(layoutParams);
		}

		@Override
		protected void onPause()
		{
			super.onPause();
			WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
			layoutParams.screenBrightness = mScreenBrightness;
			getWindow().setAttributes(layoutParams);
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
//			 Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>(2);
//			 hints.put(EncodeHintType.CHARACTER_SET, null);

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

	}
	
	
	QRCodeExpose()
	{
		super(R.string.expose_qrcode,KEY_QRCODE,FEATURE_SCREEN);
	}

	@Override
	public void startExposition(Activity activity)
	{
		activity.startActivity(new Intent(activity,QRCodeActivity.class));
	}
}
