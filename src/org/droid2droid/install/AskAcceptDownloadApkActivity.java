/******************************************************************************
 *
 * droid2droid - Distributed Android Framework
 * ==========================================
 *
 * Copyright (C) 2012 by Atos (http://www.http://atos.net)
 * http://www.droid2droid.org
 *
 ******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
******************************************************************************/
package org.droid2droid.install;

import static org.droid2droid.Constants.LOCK_ASK_DOWNLOAD;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.TAG_INSTALL;
import static org.droid2droid.internal.Constants.V;

import java.lang.ref.WeakReference;

import org.droid2droid.CommunicationWithLock;
import org.droid2droid.ConnectionType;
import org.droid2droid.R;
import org.droid2droid.ui.Notifications;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.FragmentActivity;
import android.text.format.Formatter;
import android.util.Log;

public final class AskAcceptDownloadApkActivity extends FragmentActivity implements
		DialogInterface.OnClickListener
{
	public static final String EXTRA_DEVICE="device";
	public static final String EXTRA_DESCRIPTION="description";
	public static final String EXTRA_SIZE="size";
	public static final String EXTRA_TIMEOUT="timeout";
	public static final String EXTRA_CHANEL="chanel";
	
	private static final int	DIALOG_IMPORT	= 1;

	private final Handler			mHandler		= new Handler();

	private int					mId;

	class FinishRunnable implements Runnable
	{

		WeakReference<AskAcceptDownloadApkActivity>	mActivity;

		FinishRunnable(AskAcceptDownloadApkActivity activity)
		{
			mActivity = new WeakReference<AskAcceptDownloadApkActivity>(activity);
		}

		@Override
		public void run()
		{
			CommunicationWithLock.putResult(LOCK_ASK_DOWNLOAD + mId,
					Boolean.FALSE);
			AskAcceptDownloadApkActivity activity = mActivity.get();
			if (activity != null)
				activity.finish();
		}

	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		showDialog(DIALOG_IMPORT);
		if (V)
			Log.v(TAG_INSTALL, PREFIX_LOG + "srv create confirm apk dialog");
		long timeout = getIntent().getLongExtra(EXTRA_TIMEOUT, -1);
		mId = getIntent().getIntExtra(Notifications.EXTRA_ID, -1);

		mHandler.postAtTime(new FinishRunnable(this), SystemClock.uptimeMillis() + timeout);
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (V)
			Log.v(TAG_INSTALL, PREFIX_LOG + "srv remove confirm apk dialog");
		removeDialog(DIALOG_IMPORT);
	}

	@Override
	public Dialog onCreateDialog(int id)
	{
		switch (id)
		{
			case DIALOG_IMPORT:
				Intent intent = getIntent();
				String deviceName = intent.getStringExtra(EXTRA_DEVICE);
				String description = intent.getStringExtra(EXTRA_DESCRIPTION);
				long totalBytes = intent.getLongExtra(EXTRA_SIZE, 0);
				ConnectionType type=ConnectionType.valueOf(intent.getStringExtra(EXTRA_CHANEL));
				String text;
				if (type!=ConnectionType.GSM)
				{
					text = getString(R.string.ask_download_apk_describe_wifi, deviceName,
							description, Formatter.formatFileSize(this, totalBytes));
				}
				else
				{
					text = getString(R.string.ask_download_apk_describe, deviceName, description,
							Formatter.formatFileSize(this, totalBytes));
				}

				return new AlertDialog.Builder(this).setTitle(R.string.ask_download_apk_title)
						.setMessage(text).setPositiveButton(R.string.ask_download_apk_accept, this)
						.setNegativeButton(R.string.ask_download_apk_decline, this).create();
			default:
				return null;
		}
	}

	@Override
	public void onClick(DialogInterface dialog, int which)
	{
		if (which == Dialog.BUTTON1)
		{
			// Ok
			if (V) Log.v(TAG_INSTALL, PREFIX_LOG + "srv inform download is accepted");
			CommunicationWithLock.putResult(LOCK_ASK_DOWNLOAD + mId,
					Boolean.TRUE);
			finish();
		}
		else
		{
			// Cancel
			if (V) Log.v(TAG_INSTALL, PREFIX_LOG + "srv inform download is refused");
			CommunicationWithLock.putResult(LOCK_ASK_DOWNLOAD + mId,
					Boolean.FALSE);
			finish();
		}
	}

	@Override
	protected void onUserLeaveHint()
	{
		CommunicationWithLock.putResult(LOCK_ASK_DOWNLOAD + mId,
			Boolean.FALSE);
		moveTaskToBack(true);
		finish();
	}
}
