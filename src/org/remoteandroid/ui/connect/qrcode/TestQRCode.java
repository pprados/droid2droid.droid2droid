package org.remoteandroid.ui.connect.qrcode;

import java.util.Hashtable;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageButton;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

public class TestQRCode extends Activity
{
	private static final int	WHITE	= 0xFFFFFFFF;

	private static final int	BLACK	= 0xFF000000;
	
	private float mScreenBrightness;
	
// http://barcode4j.sourceforge.net/ Pour encodage datamatrix
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		WindowManager manager = (WindowManager) getSystemService(WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
	    int width = display.getWidth();
	    int height = display.getHeight();
	    int smallerDimension = width < height ? width : height;
	    smallerDimension = smallerDimension * 7 / 8;
		ImageButton btn=new ImageButton(this);
		Bitmap bitmap;
		try
		{
			bitmap=buildQRCode(smallerDimension);
			btn.setImageBitmap(bitmap);
			btn.setBackgroundColor(WHITE);
			setContentView(btn);
		}
		catch (WriterException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//FIXME getWindow().setFlags(LayoutParams.FLAG_KEEP_SCREEN_ON, LayoutParams.FLAG_KEEP_SCREEN_ON);
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
		layoutParams.screenBrightness=mScreenBrightness;
		getWindow().setAttributes(layoutParams);
	}
	Bitmap buildQRCode(int smallerDimension) throws WriterException
	{
		BarcodeFormat format = BarcodeFormat.QR_CODE;
		byte[] test=new byte[]{0,1,(byte)'A',65,(byte)0xFF};
//		String contents=new String(test,0);
		String contents = "mailto:" + "0102030405060708090A0B0C0D0E0F@gmail.com";
		String encoding = null;
		return encodeAsBitmap(contents,format,smallerDimension);
	}
	Bitmap encodeAsBitmap(String contents, BarcodeFormat format, int dimension)
			throws WriterException
	{
		Hashtable<EncodeHintType, Object> hints = null;
		String encoding = guessAppropriateEncoding(contents);
		if (encoding != null)
		{
			hints = new Hashtable<EncodeHintType, Object>(2);
			hints.put(EncodeHintType.CHARACTER_SET, encoding);
		}
		
		Writer writer=new QRCodeWriter();
//		MultiFormatWriter writer = new MultiFormatWriter();
		BitMatrix result = writer.encode(contents, format, dimension, dimension, hints);
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
		return bitmap;
	}

	private static String guessAppropriateEncoding(CharSequence contents)
	{
		// Very crude at the moment
		for (int i = 0; i < contents.length(); i++)
		{
			if (contents.charAt(i) > 0xFF)
			{
				return "UTF-8";
			}
		}
		return null;
	}

}
