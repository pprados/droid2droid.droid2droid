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

import org.remoteandroid.R;

import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;

/**
 * 
 * @author Yohann Melo
 * 
 */
final class AutoFocusCallback implements Camera.AutoFocusCallback
{

	private Handler mAutoFocusHandler;

	private int mAutoFocusMessage;

	void setHandler(Handler autoFocusHandler, int autoFocusMessage)
	{
		this.mAutoFocusHandler = autoFocusHandler;
		this.mAutoFocusMessage = autoFocusMessage;
	}

	public void onAutoFocus(boolean success, Camera camera)
	{
		if (mAutoFocusHandler != null)
		{
			if (V)
				Log.v(
					TAG_CONNECT, "Got auto-focus callback");
			mAutoFocusHandler.sendEmptyMessage(R.id.start_decode);

			if (QRCODE_REPEAT_AUTOFOCUS)
			{
				Message message = mAutoFocusHandler.obtainMessage(
					mAutoFocusMessage, success);
				mAutoFocusHandler.sendMessageDelayed(
					message, QRCODE_AUTOFOCUS_INTERVAL_MS);
			}
			mAutoFocusHandler = null;
		}
		else
		{
			if (V)
				Log.v(
					TAG_CONNECT,
					"Got auto-focus callback, but no handler for it");
		}
	}

}
