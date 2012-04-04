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

package org.remoteandroid.ui.connect.qrcode;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;
import java.util.List;

import org.remoteandroid.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.WindowManager;

import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;

/**
 * This class handles all the messaging which comprises the state machine for
 * capture.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Yohann Melo
 */
public final class CaptureHandler extends Handler
{
	/*private*/ boolean mStarted;
	private QRCodeScannerView mQRCodeScannerView;
	
	private final Camera.PreviewCallback mPreviewCallback=new Camera.PreviewCallback()
	{

		@Override
		public void onPreviewFrame(byte[] data, Camera camera)
		{
			Point previousSize = mQRCodeScannerView.mPreviousSize;
			if (V) Log.v(TAG_QRCODE,"3. get preview frame ("+previousSize.x+","+previousSize.y+")");
			if (!mStarted && QRCODE_AUTOFOCUS)
			{
				// Warning: Start autofocus AFTER the first preview frame.
				if (V) Log.v(TAG_QRCODE,"0. First frame previous. Start autofocus");
				mStarted=true;
				mQRCodeScannerView.mCamera.autoFocus(mAutoFocusCallback);						
			}	
			else
			{
				Message message = obtainMessage(R.id.decode, previousSize.x, previousSize.y, data);
				message.sendToTarget();
			}
		}

	};
	
	/*private*/ final Camera.AutoFocusCallback mAutoFocusCallback=new Camera.AutoFocusCallback()
	{

		public void onAutoFocus(boolean success, Camera camera)
		{
			if (D) Log.d(TAG_QRCODE,"onAutoFocus");
			sendEmptyMessage(R.id.request_frame);
		}
	};

	private final DecodeCallback mDecodeCallback;
	
	
	private final ResultPointCallback mResultPointCallback = new ResultPointCallback()
	{
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
		mDecodeCallback=new DecodeCallback(mQRCodeScannerView);
//		startScan();
	}

	public final void startScan()
	{
		final Camera camera=getCamera();
		camera.setOneShotPreviewCallback(mPreviewCallback);
		camera.startPreview();

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
			case R.id.auto_focus:
				if (V) Log.v(TAG_QRCODE, "1 ask auto focus...");
				if (camera!=null)
				{
					camera.autoFocus(mAutoFocusCallback);
				}
				break;

			case R.id.request_frame:
				if (V) Log.v(TAG_QRCODE, "2. Ask frame...");
				if (camera!=null)
					camera.setOneShotPreviewCallback(mPreviewCallback);
				break;

			case R.id.show_frame:
				if (QRCODE_SHOW_CURRENT_DECODE)
				{
					Bundle bundle = message.getData();
					Bitmap barcode = bundle == null ? null : (Bitmap) bundle.getParcelable(DecodeCallback.BARCODE_BITMAP);
					mQRCodeScannerView.handlePrevious((Result) message.obj, barcode);
				}
				break;
				
			case R.id.decode:
				if (V) Log.v(TAG_QRCODE, "3. Ask decode...");
				byte[] data=(byte[])message.obj;
				int width=message.arg1;
				int height=message.arg2;
				mDecodeCallback.requestDecode(data, width, height);
				break;

			case R.id.decode_succeeded:
				if (V) Log.v(TAG_QRCODE, "5. State: decode_succeeded. Got decode succeeded message");
				//FIXME: CameraManager.get().stopPreview();
				mQRCodeScannerView.mCamera.stopPreview();
				Bundle bundle = message.getData();
				Bitmap barcode = bundle == null ? null : (Bitmap) bundle
						.getParcelable(DecodeCallback.BARCODE_BITMAP);
				mQRCodeScannerView.handleDecode((Result) message.obj, barcode);
				break;

			case R.id.decode_failed:
				if (V) Log.v(TAG_QRCODE, "5. State: decode_failed. Got decode failed message");
				// We're decoding as fast as possible, so when one decode fails,
				// start another.
				restartPreviewAndDecode();
				break;

		}
	}

	private void restartPreviewAndDecode()
	{
		startScan();
		mQRCodeScannerView.drawViewfinder();
	}
	public final void quitSynchronously()
	{
		// Be absolutely sure we don't send any queued up messages
		removeMessages(R.id.decode_succeeded);
		removeMessages(R.id.decode_failed);
	}
}
