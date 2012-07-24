/*
 * Copyright (C) 2008 ZXing authors
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

package org.droid2droid.ui.connect.qrcode;

import static org.droid2droid.Constants.QRCODE_DELAY_AFTER_AUTOFOCUS;
import static org.droid2droid.Constants.QRCODE_DELAY_RETRY_AUTOFOCUS_IF_ERROR;
import static org.droid2droid.Constants.QRCODE_MAX_ERROR_AUTOFOCUS;
import static org.droid2droid.Constants.QRCODE_SHOW_CURRENT_DECODE;
import static org.droid2droid.Constants.TAG_QRCODE;
import static org.droid2droid.internal.Constants.V;
import static org.droid2droid.internal.Constants.W;
import static org.droid2droid.ui.connect.qrcode.QRCodeScannerView.msg_auto_focus;
import static org.droid2droid.ui.connect.qrcode.QRCodeScannerView.msg_decode;
import static org.droid2droid.ui.connect.qrcode.QRCodeScannerView.msg_decode_failed;
import static org.droid2droid.ui.connect.qrcode.QRCodeScannerView.msg_decode_succeeded;
import static org.droid2droid.ui.connect.qrcode.QRCodeScannerView.msg_request_frame;
import static org.droid2droid.ui.connect.qrcode.QRCodeScannerView.msg_show_frame;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;

/**
 * This class handles all the messaging which comprises the state machine for
 * capture.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Philippe Prados
 */
public final class CaptureHandler extends Handler
{
	private boolean mStarted;
	private boolean mWaitAutoFocus;
	private boolean mWaitDecode;
	private final QRCodeScannerView mQRCodeScannerView;
	private int mErrorAutofocus;
	
	
	private final Camera.PreviewCallback mPreviewCallback=new Camera.PreviewCallback()
	{
		@Override
		public void onPreviewFrame(byte[] data, Camera camera)
		{
			Point previousSize = mQRCodeScannerView.mPreviousSize;
			if (!mStarted)
			{
				// Warning: Start autofocus AFTER the first preview frame.
				mStarted=true;
				try
				{
					getCamera().autoFocus(mAutoFocusCallback);
					mWaitAutoFocus=true;
				}
				catch (RuntimeException e)
				{
					// Sometime, with ICS... try later
					if (W) Log.w(TAG_QRCODE,"Camera autoFocus throw a RuntimeException after onPrevious frame. Retry.");
					if (++mErrorAutofocus<QRCODE_MAX_ERROR_AUTOFOCUS)
					{
						mStarted=false;
						sendEmptyMessageDelayed(msg_auto_focus,QRCODE_DELAY_RETRY_AUTOFOCUS_IF_ERROR);
					}
				}
			}	
			else
			{
				if (previousSize!=null)
					obtainMessage(msg_decode, previousSize.x, previousSize.y, data).sendToTarget();
			}
		}

	};
	
	private final Camera.AutoFocusCallback mAutoFocusCallback=new Camera.AutoFocusCallback()
	{

		@Override
		@SuppressWarnings("unused")
		public void onAutoFocus(boolean success, Camera camera)
		{
			mWaitAutoFocus=false;
			if (QRCODE_DELAY_AFTER_AUTOFOCUS==0)
				sendEmptyMessage(msg_request_frame);
			else
				sendEmptyMessageDelayed(msg_request_frame,QRCODE_DELAY_AFTER_AUTOFOCUS);
		}
	};

	@SuppressWarnings("unused")
	private final ResultPointCallback mResultPointCallback = new ResultPointCallback()
	{
		@Override
		public void foundPossibleResultPoint(ResultPoint point)
		{
			if (V) Log.v(TAG_QRCODE, "Found possible result point");
			if (mQRCodeScannerView!=null)
				mQRCodeScannerView.addPossibleResultPoint(point);
		}
	};
	
	public CaptureHandler(Context context,final QRCodeScannerView qrcodeView)
	{
		mQRCodeScannerView=qrcodeView;	
	}

	private final Camera getCamera()
	{
		return mQRCodeScannerView.mCamera;
	}
	
	@Override
	public void handleMessage(Message message)
	{
		Camera camera=getCamera();
		switch (message.what)
		{
			case msg_auto_focus:
				if (camera!=null && !mWaitAutoFocus)
				{
					if (V) Log.v(TAG_QRCODE, "1 ask auto focus...");
					try
					{
						camera.autoFocus(mAutoFocusCallback);
					}
					catch (RuntimeException e)
					{
						if (W) Log.w(TAG_QRCODE,"Camera autoFocus throw a RuntimeException when receive autofocus message. Retry.");
						if (++mErrorAutofocus<QRCODE_MAX_ERROR_AUTOFOCUS)
						{ 
							sendEmptyMessageDelayed(msg_auto_focus,QRCODE_DELAY_RETRY_AUTOFOCUS_IF_ERROR);
						}
					}
				}
				break;

			case msg_request_frame:
				if (!mWaitAutoFocus && (camera!=null))
				{
					if (V) Log.v(TAG_QRCODE, "2. Ask frame...");
					camera.setOneShotPreviewCallback(mPreviewCallback);
				}
				break;

			case msg_show_frame:
				if (QRCODE_SHOW_CURRENT_DECODE)
				{
					Bundle bundle = message.getData();
					Bitmap barcode = bundle == null ? null : (Bitmap) bundle.getParcelable(DecodeCallback.BARCODE_BITMAP);					
					mQRCodeScannerView.handlePrevious((Result) message.obj, barcode);
				}
				break;
				
			case msg_decode:
				if (!mWaitDecode)
				{
					if (V) Log.v(TAG_QRCODE, "3. Ask decode...");
					byte[] data=(byte[])message.obj;
					int width=message.arg1;
					int height=message.arg2;
					mWaitDecode=true;
					boolean isDecode=new DecodeCallback().requestDecode(mQRCodeScannerView,data, width, height);
					if (!isDecode)
					{
						restartPreviewAndDecode();
					}
//					if (mStarted && !mWaitAutoFocus)
//					{
//						if (V) Log.v(TAG_QRCODE, "-> Ask autofocus during decode...");
//						mWaitAutoFocus=true;
//						mQRCodeScannerView.mCamera.autoFocus(mAutoFocusCallback);
//					}
				}
				break;

			case msg_decode_succeeded:
				if (V) Log.v(TAG_QRCODE, "5. State: decode_succeeded. Got decode succeeded message");
				mWaitDecode=false;
				getCamera().stopPreview();
				Bundle bundle = message.getData();
				Bitmap barcode = bundle == null ? null : (Bitmap) bundle
						.getParcelable(DecodeCallback.BARCODE_BITMAP);
				mQRCodeScannerView.handleDecode((Result) message.obj, barcode);
				break;

			case msg_decode_failed:
				if (V) Log.v(TAG_QRCODE, "5. State: decode_failed. Got decode failed message");
				// We're decoding as fast as possible, so when one decode fails,
				// start another.
				mWaitDecode=false;
				restartPreviewAndDecode();
				break;

		}
	}

	public final void startScan()
	{
		final Camera camera=getCamera();
		if (camera!=null)
		{
			mStarted=false;
			camera.setOneShotPreviewCallback(mPreviewCallback);
			camera.startPreview();
		}

	}
	public final void stopScan()
	{
		mStarted=false;
		final Camera camera=getCamera();
		if (camera!=null)
		{
			camera.stopPreview();
		}
	}

	private void restartPreviewAndDecode()
	{
		obtainMessage(msg_auto_focus).sendToTarget();
		mQRCodeScannerView.drawViewfinder();
	}
	
	public final void quitSynchronously()
	{
		final Camera camera=getCamera();
		if (camera!=null)
		{
			camera.stopPreview();
		}
		// Be absolutely sure we don't send any queued up messages
		removeMessages(msg_decode_succeeded);
		removeMessages(msg_decode_failed);
	}
}
