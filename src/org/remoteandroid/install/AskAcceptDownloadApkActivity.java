package org.remoteandroid.install;

import static org.remoteandroid.Constants.LOCK_ASK_DOWNLOAD;
import static org.remoteandroid.Constants.LOCK_ASK_PAIRING;
import static org.remoteandroid.internal.Constants.*;

import java.lang.ref.WeakReference;

import org.remoteandroid.CommunicationWithLock;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.R;
import org.remoteandroid.ui.Notifications;

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

	private Handler				mHandler		= new Handler();

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
					new Boolean(false));
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
					new Boolean(true));
			finish();
		}
		else
		{
			// Cancel
			if (V) Log.v(TAG_INSTALL, PREFIX_LOG + "srv inform download is refused");
			CommunicationWithLock.putResult(LOCK_ASK_DOWNLOAD + mId,
					new Boolean(false));
			finish();
		}
	}

	@Override
	protected void onUserLeaveHint()
	{
		CommunicationWithLock.putResult(LOCK_ASK_DOWNLOAD + mId,
			new Boolean(false));
		moveTaskToBack(true);
		finish();
	}
}
