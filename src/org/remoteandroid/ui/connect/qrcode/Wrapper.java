package org.remoteandroid.ui.connect.qrcode;

import android.graphics.Bitmap;
import android.os.Handler;

import com.google.zxing.Result;

/**
 * 
 * @author Yohann Melo
 * 
 */
public interface Wrapper
{
	ViewfinderView getViewfinderView();

	public void handleDecode(Result rawResult, Bitmap barcode);

	public void handlePrevious(Result rawResult, Bitmap barcode);

	public void drawViewfinder();

	Handler getHandler();
}
