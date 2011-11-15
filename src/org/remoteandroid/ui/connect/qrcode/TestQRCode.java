package org.remoteandroid.ui.connect.qrcode;

import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;

import java.net.SocketException;
import java.net.UnknownHostException;

import org.remoteandroid.ui.connect.ConnectMessages;
import org.remoteandroid.ui.connect.ConnectionCandidats;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.ImageButton;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * 
 * @author Yohann Melo
 * 
 */
public class TestQRCode extends Activity
{
	private static final int WHITE = 0xFFFFFFFF;

	private static final int BLACK = 0xFF000000;

	private ImageButton mBtn;

	private float mScreenBrightness;

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
		mBtn = new ImageButton(this);
		mBtn.setBackgroundColor(WHITE);
		setContentView(mBtn);
		new AsyncTask<Void, Void, Bitmap>()
		{
			@Override
			protected Bitmap doInBackground(Void... params)
			{
				try
				{
					return buildQRCode(smallerDim);
				}
				catch (WriterException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
			}

			@Override
			protected void onPostExecute(Bitmap bitmap)
			{
				if (bitmap != null)
					mBtn.setImageBitmap(bitmap);
			}
		}.execute();
		// FIXME getWindow().setFlags(LayoutParams.FLAG_KEEP_SCREEN_ON,
		// LayoutParams.FLAG_KEEP_SCREEN_ON);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
		layoutParams.screenBrightness = mScreenBrightness;
		getWindow().setAttributes(
			layoutParams);
	}

	Bitmap buildQRCode(int smallerDimension) throws WriterException
	{
		BarcodeFormat format = BarcodeFormat.QR_CODE;

		try
		{
			ConnectMessages.Candidates candidates = ConnectionCandidats
					.getConnectMessage(this);
			// byte[] test=new byte[]{0,1,(byte)'A',65,(byte)0xFF};
			byte[] data = candidates.toByteArray();
			if (data.length != 0)
			{
				String contents = new String(data, 0);
				return encodeAsBitmap(
					contents, format, smallerDimension);
			}
			else
			{
				// TODO: Pas de connection possible
				if (I)
					Log.i(
						TAG_CONNECT, PREFIX_LOG + "Not connection available");
				return null;
			}
		}
		catch (UnknownHostException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		catch (SocketException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	Bitmap encodeAsBitmap(String contents, BarcodeFormat format, int dimension)
			throws WriterException
	{
		// Hashtable<EncodeHintType, Object> hints = new
		// Hashtable<EncodeHintType, Object>(2);
		// hints.put(EncodeHintType.CHARACTER_SET, null);

		Writer writer = new QRCodeWriter();
		BitMatrix result = writer.encode(
			contents, format, 0, 0, null);
		int width = result.getWidth();
		int height = result.getHeight();
		int[] pixels = new int[width * height];
		// All are 0, or black, by default
		for (int y = 0; y < height; y++)
		{
			int offset = y * width;
			for (int x = 0; x < width; x++)
			{
				pixels[offset + x] = result.get(
					x, y) ? BLACK : WHITE;
			}
		}

		Bitmap bitmap = Bitmap.createBitmap(
			width, height, Bitmap.Config.ARGB_8888);
		bitmap.setPixels(
			pixels, 0, width, 0, 0, width, height);
		Bitmap dimbitmap = Bitmap.createScaledBitmap(
			bitmap, dimension, dimension, false);
		bitmap.recycle();
		return dimbitmap;
	}

}
