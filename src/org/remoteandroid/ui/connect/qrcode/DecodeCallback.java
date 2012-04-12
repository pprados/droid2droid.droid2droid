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

import static org.remoteandroid.Constants.QRCODE_SHOW_CURRENT_DECODE;
import static org.remoteandroid.Constants.TAG_QRCODE;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.ui.connect.qrcode.QRCodeScannerView.msg_decode_failed;
import static org.remoteandroid.ui.connect.qrcode.QRCodeScannerView.msg_decode_succeeded;
import static org.remoteandroid.ui.connect.qrcode.QRCodeScannerView.msg_show_frame;

import java.util.Hashtable;

import org.remoteandroid.Application;

import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.Reader;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

final class DecodeCallback 
{
	public static final String BARCODE_BITMAP = "barcode_bitmap";
	//private QRCodeScannerView mScannerView;

	private final Reader mReader;
	private int mRotation;

	private CaptureHandler mHandler;
	private Camera mCamera;
	private Rect mCameraRect;
	private Rect mScanningRect;
	private Rect mScanningRectInCameraPrevious;
	private Hashtable<DecodeHintType, Object> mHints;

	DecodeCallback()
	{
		mHints = new Hashtable<DecodeHintType, Object>(1);
//		mHints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
		mReader = new QRCodeReader();
	}
	public boolean requestDecode(QRCodeScannerView scannerView,final byte[] data,final int width, final int height)
	{
		mHandler=scannerView.mCaptureHandler;
		mCamera=scannerView.mCamera;
		if (scannerView.getCameraRect()==null)
		{
			return false; // Too early
		}
		mCameraRect=new Rect(scannerView.getCameraRect());
		mScanningRect =new Rect(scannerView.getFramingRectInPreview());
		mRotation=scannerView.mRotation;
		if (mHandler==null) 
			return false;
		if (data==null)
			return false;
		
		if (mRotation==90)
		{
			mScanningRectInCameraPrevious=new Rect(
				mScanningRect.top*width/mCameraRect.bottom,  
				(mCameraRect.right-mScanningRect.right)*height/mCameraRect.right, 
				mScanningRect.bottom*width/mCameraRect.bottom,
				(mCameraRect.right-mScanningRect.left)*height/mCameraRect.right
				);
		}
		else
		{
			mScanningRectInCameraPrevious=new Rect(
				(mCameraRect.right-mScanningRect.right)*width/mCameraRect.right,
				mScanningRect.top*height/mCameraRect.bottom,  
				(mCameraRect.right-mScanningRect.left)*width/mCameraRect.right,
				mScanningRect.bottom*height/mCameraRect.bottom
				);
		}
		Application.sThreadPool.execute(new Runnable()
		{
			@Override
			public void run()
			{
				decode(data,width,height);
			}
		});
		return true;
	}
	
	/**
	 * Decode the data within the viewfinder rectangle, and time how long it
	 * took. For efficiency, reuse the same reader objects from one decode to
	 * the next.
	 * 
	 * @param data The YUV preview frame.
	 * @param width The width of the preview frame.
	 * @param height The height of the preview frame.
	 */
	private void decode(byte[] data, int width, int height)
	{
		if (V) Log.v(TAG_QRCODE,"decode...");
		//long start = System.currentTimeMillis();
		PlanarYUVLuminanceSource source=null;
		Result rawResult = null;
		try
		{
			source = buildLuminanceSource(data, width, height);
			if (source==null)
				return;
			BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
			// BinaryBitmap bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source)); //FIXME
			// ----------
			if (QRCODE_SHOW_CURRENT_DECODE)
			{
				Message message = Message.obtain(mHandler, msg_show_frame, rawResult);
				Bundle bundle = new Bundle();
				bundle.putParcelable(
					DecodeCallback.BARCODE_BITMAP,
					source.renderCroppedGreyscaleBitmap(mRotation));
				message.setData(bundle);
				message.sendToTarget();
			}
			// ---------
			rawResult = mReader.decode(bitmap, mHints);
		}
		catch (IllegalArgumentException e)
		{
			// Try to analyse during the rotation.
			if (V)	Log.v(TAG_QRCODE, "Decode during the rotation.",e);
		}
		catch (ReaderException re)
		{
			// continue
			if (V)	Log.v(TAG_QRCODE, "Not found tag. " + re.getClass().getName()+":"+re.getMessage());
		}
		finally
		{
			mReader.reset();
		}

		//long end = System.currentTimeMillis();
		//if (V) Log.v(TAG_QRCODE, "Stop decode " + (end - start) + " ms");
		if (rawResult != null)
		{
			// Don't log the barcode contents for security.
			Message message = Message.obtain(
				mHandler, msg_decode_succeeded, rawResult);
			Bundle bundle = new Bundle();
			bundle.putParcelable(
				DecodeCallback.BARCODE_BITMAP,
				source.renderCroppedGreyscaleBitmap(mRotation));
			message.setData(bundle);
			message.sendToTarget();
		}
		else
		{
			Message message = Message.obtain(mHandler, msg_decode_failed);
			message.sendToTarget();
		}
	}
	
	/**
	 * A factory method to build the appropriate LuminanceSource object based on
	 * the format of the preview buffers, as described by Camera.Parameters.
	 * 
	 * @param data A preview frame.
	 * @param width The width of the image.
	 * @param height The height of the image.
	 * @return A PlanarYUVLuminanceSource instance.
	 */
	@SuppressWarnings("deprecation")
	private PlanarYUVLuminanceSource buildLuminanceSource(byte[] data, int width, int height)
	{
		if (mCamera==null) return null;
		final Camera.Parameters parameters = mCamera.getParameters();
		int previewFormat = parameters.getPreviewFormat();
		String previewFormatString = parameters.get("preview-format");

		switch (previewFormat)
		{
			// This is the standard Android format which all devices are REQUIRED to support.
			// In theory, it's the only one we should ever care about.
			case PixelFormat.YCbCr_420_SP:
			// This format has never been seen in the wild, but is compatible as we only care about the Y channel, so allow it.
			case PixelFormat.YCbCr_422_SP:
				return new PlanarYUVLuminanceSource(data, width, height, mScanningRectInCameraPrevious, false/*reverse*/);
			default:
				// The Samsung Moment incorrectly uses this variant instead of the 'sp' version.
				// Fortunately, it too has all the Y data up front, so we can read it.
				if ("yuv420p".equals(previewFormatString))
				{
					return new PlanarYUVLuminanceSource(data, width, height, mScanningRectInCameraPrevious, false/*reverse*/);
				}
		}
		throw new IllegalArgumentException("Unsupported picture format: " + previewFormat + '/' + previewFormatString);
	}

}
