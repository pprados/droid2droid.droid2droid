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
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.V;

import java.util.Vector;

import org.remoteandroid.R;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;

/**
 * This class handles all the messaging which comprises the state machine for capture.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class CaptureHandler extends Handler
{

	private volatile Wrapper	mWrapper;

	private final DecodeThread		mDecodeThread;

	private State					mState;

	private enum State
	{
		AUTOFOCUS, DECODE, SUCCESS, DONE
	}
	ResultPointCallback mResultPointCallback=new ResultPointCallback()
	{
		public void foundPossibleResultPoint(ResultPoint point)
		{
			if (V) Log.v(TAG_CONNECT,"Found possible result point");
			mWrapper.getViewfinderView().addPossibleResultPoint(point);
		}
	};

	public CaptureHandler(final Wrapper wrapper)
	{
		this.mWrapper = wrapper;
		mDecodeThread = new DecodeThread(wrapper,mResultPointCallback);
		mDecodeThread.start();
		mState = State.SUCCESS;

		// Start ourselves capturing previews and decoding.
		CameraManager.get().startPreview();
		restartPreviewAndDecode();
	}

	public void setWrapper(Wrapper wrapper)
	{
		mWrapper=wrapper;
	}
	
	@Override
	public void handleMessage(Message message)
	{
		switch (message.what)
		{
			case R.id.auto_focus:
				if (V) Log.v(TAG_CONNECT, "Got auto-focus message");
				// When one auto focus pass finishes, start another. This is the closest thing to
				// continuous AF. It does seem to hunt a bit, but I'm not sure what else to do.
				if (mState == State.DECODE)
				{
					CameraManager.get().requestAutoFocus(this, R.id.auto_focus);
				}
				break;
				
			case R.id.start_decode:
				if (V) Log.v(TAG_CONNECT, "Got start decode message");
				if (mState!=State.DECODE)
				{
					mState=State.DECODE;
					CameraManager.get().requestPreviewFrame(mDecodeThread.getHandler(), R.id.decode);
				}
				else
					if (E) Log.e(TAG_CONNECT,"allrealdy in decode state");
				break;
			
			case R.id.current_decode:
				if (QRCODE_SHOW_CURRENT_DECODE)
				{
					if (D) Log.d(TAG_CONNECT, "Got current decode message");
					Bundle bundle = message.getData();
					Bitmap barcode = bundle == null ? null : (Bitmap) bundle
							.getParcelable(DecodeThread.BARCODE_BITMAP);
					mWrapper.handlePrevious((Result) message.obj, barcode);
				}
				break;
				
			case R.id.decode_succeeded:
				if (D) Log.d(TAG_CONNECT, "Got decode succeeded message");
				mState = State.SUCCESS;
				Bundle bundle = message.getData();
				Bitmap barcode = bundle == null ? null : (Bitmap) bundle
						.getParcelable(DecodeThread.BARCODE_BITMAP);
				mWrapper.handleDecode((Result) message.obj, barcode);
				break;
				
			case R.id.decode_failed:
				if (V) Log.v(TAG_CONNECT, "Got decode failed message");
				// We're decoding as fast as possible, so when one decode fails, start another.
				restartPreviewAndDecode();
				break;
				
		}
	}

	public void quitSynchronously()
	{
		mState = State.DONE;
		CameraManager.get().stopPreview();
		Message quit = Message.obtain(mDecodeThread.getHandler(), R.id.quit);
		quit.sendToTarget();
		try
		{
			mDecodeThread.join();
		}
		catch (InterruptedException e)
		{
			// continue
		}

		// Be absolutely sure we don't send any queued up messages
		removeMessages(R.id.decode_succeeded);
		removeMessages(R.id.decode_failed);
	}

	private void restartPreviewAndDecode()
	{
		if (V) Log.v(TAG_CONNECT,"State.SUCESSS. restartPreviewAndDecode...");
		mState = State.AUTOFOCUS;
		CameraManager.get().requestAutoFocus(this, R.id.auto_focus);
		mWrapper.drawViewfinder();
	}

}
