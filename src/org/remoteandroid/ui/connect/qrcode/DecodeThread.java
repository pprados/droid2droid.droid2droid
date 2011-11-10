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

import java.util.Hashtable;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.DecodeHintType;
import com.google.zxing.ResultPointCallback;

/**
 * This thread does all the heavy lifting of decoding the images.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 */
final class DecodeThread extends Thread
{

	public static final String						BARCODE_BITMAP	= "barcode_bitmap";

	private volatile Wrapper				mWrapper;

	private final Hashtable<DecodeHintType, Object>	mHints;

	private Handler									mHandler;

	private final CountDownLatch					mHandlerInitLatch;

	DecodeThread(Wrapper wrapper, ResultPointCallback resultPointCallback)
	{

		this.mWrapper = wrapper;
		mHandlerInitLatch = new CountDownLatch(1);

		mHints = new Hashtable<DecodeHintType, Object>(1);
//		if (characterSet != null)
//		{
//			hints.put(DecodeHintType.CHARACTER_SET, characterSet);
//		}
		mHints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, resultPointCallback);
		//mHints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
	}
	void setActivity(Wrapper wrapper) // FIXME: Le nom de la methode
	{
		mWrapper=wrapper;
	}
	Handler getHandler()
	{
		try
		{
			mHandlerInitLatch.await();
		}
		catch (InterruptedException ie)
		{
			// continue?
		}
		return mHandler;
	}

	@Override
	public void run()
	{
		Looper.prepare();
		Thread.currentThread().setPriority(NORM_PRIORITY+1); // FIXME: A confirmer ?
		mHandler = new DecodeHandler(mWrapper, mHints);
		mHandlerInitLatch.countDown();
		Looper.loop();
	}

}