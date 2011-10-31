/*
 * Copyright (C) 2010 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.remoteandroid.ui.connect.qrcode;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;

import java.util.Hashtable;

import org.remoteandroid.R;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.Reader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

final class DecodeHandler extends Handler
{

	private final Wrapper				mWrapper;

	private final Reader						mReader;

	private boolean								mRunning	= true;

	private Hashtable<DecodeHintType, Object>	mHints;

	DecodeHandler(Wrapper wrapper, Hashtable<DecodeHintType, Object> hints)
	{
		mReader = new QRCodeReader();
		this.mHints = hints;
		this.mWrapper = wrapper;
	}

	@Override
	public void handleMessage(Message message)
	{
		if (!mRunning)
		{
			return;
		}
		switch (message.what)
		{
			case R.id.decode:
				decode((byte[]) message.obj, message.arg1, message.arg2);
				break;

			case R.id.quit:
				mRunning = false;
				Looper.myLooper().quit();
				break;
		}
	}

	/**
	 * Decode the data within the viewfinder rectangle, and time how long it took. For efficiency,
	 * reuse the same reader objects from one decode to the next.
	 * 
	 * @param data
	 *            The YUV preview frame.
	 * @param width
	 *            The width of the preview frame.
	 * @param height
	 *            The height of the preview frame.
	 */
	private void decode(byte[] data, int width, int height)
	{
		long start = System.currentTimeMillis();
		Result rawResult = null;
		PlanarYUVLuminanceSource source = CameraManager.get().buildLuminanceSource(data, width, height);
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
		//BinaryBitmap bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source)); //FIXME

//----------
		if (QRCODE_SHOW_CURRENT_DECODE)
		{
			Message message = Message.obtain(mWrapper.getHandler(), R.id.current_decode,
					rawResult);
			Bundle bundle = new Bundle();
			bundle.putParcelable(DecodeThread.BARCODE_BITMAP, 
					//Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
					source.renderCroppedGreyscaleBitmap()
					);
			message.setData(bundle);
			message.sendToTarget();
		}
//---------		
		try
		{
			//rawResult = mReader.decode(bitmap, mHints);
			try { Thread.sleep(2); } catch (Exception e) {}
			throw NotFoundException.getNotFoundInstance(); // FIXME: A virer
		}
		catch (ReaderException re)
		{
			// continue
			if (V) Log.v(TAG_CONNECT, "******** Not found. "+re.getMessage());
		}
		finally
		{
			mReader.reset();
		}

		long end = System.currentTimeMillis();
		if (V) Log.v(TAG_CONNECT, "Stop decode "+ (end - start) + " ms");
		if (rawResult != null)
		{
			// Don't log the barcode contents for security.
			Message message = Message.obtain(mWrapper.getHandler(), R.id.decode_succeeded,
					rawResult);
			Bundle bundle = new Bundle();
			bundle.putParcelable(DecodeThread.BARCODE_BITMAP, source.renderCroppedGreyscaleBitmap());
			message.setData(bundle);
			message.sendToTarget();
		}
		else
		{
			Message message = Message.obtain(mWrapper.getHandler(), R.id.decode_failed);
			message.sendToTarget();
		}
	}

}
