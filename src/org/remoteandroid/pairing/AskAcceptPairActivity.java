package org.remoteandroid.pairing;

import static org.remoteandroid.Constants.LOCK_ASK_PAIRING;
import static org.remoteandroid.Constants.TIMEOUT_ASK_PAIR;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_INSTALL;
import static org.remoteandroid.internal.Constants.TAG_PAIRING;
import static org.remoteandroid.internal.Constants.V;

import java.lang.ref.WeakReference;

import org.remoteandroid.CommunicationWithLock;
import org.remoteandroid.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Window;

// TODO: protect with android:filterTouchesWhenObscured="true" ?
public final class AskAcceptPairActivity extends FragmentActivity implements
		DialogInterface.OnClickListener
{
	public static final String EXTRA_DEVICE="device";
	public static final String EXTRA_PASSKEY="passkey";
	
	private static final int	DIALOG_ASK_ACCEPT_PAIRING	= 1;

	private Handler				mHandler		= new Handler();

	FinishRunnable				mFinisher;
	class FinishRunnable implements Runnable
	{

		boolean	mIsFinish;
		WeakReference<AskAcceptPairActivity>	mActivity;

		FinishRunnable(AskAcceptPairActivity activity)
		{
			mActivity = new WeakReference<AskAcceptPairActivity>(activity);
		}

		@Override
		public void run()
		{
			if (mIsFinish) return;
			CommunicationWithLock.putResult(LOCK_ASK_PAIRING,
					Boolean.FALSE);
			AskAcceptPairActivity activity = mActivity.get();
			if (activity != null)
				activity.finish();
		}

	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		//getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		super.onCreate(savedInstanceState);
		showDialog(DIALOG_ASK_ACCEPT_PAIRING);
		if (V)
			Log.v(TAG_INSTALL, PREFIX_LOG + "srv create accept pairing dialog");
		mFinisher=new FinishRunnable(this);
		mHandler.postAtTime(mFinisher, SystemClock.uptimeMillis() + TIMEOUT_ASK_PAIR);
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		if (V)
			Log.v(TAG_INSTALL, PREFIX_LOG + "srv onNewIntent");
	}
	@Override
	protected void onPause()
	{
		super.onPause();
		if (V)
			Log.v(TAG_INSTALL, PREFIX_LOG + "srv remove accept pairing dialog");
		removeDialog(DIALOG_ASK_ACCEPT_PAIRING);
	}

	@Override
	public Dialog onCreateDialog(int id)
	{
		switch (id)
		{
			case DIALOG_ASK_ACCEPT_PAIRING:
				Intent intent = getIntent();
				String deviceName = intent.getStringExtra(EXTRA_DEVICE);
				String alpha = intent.getStringExtra(EXTRA_PASSKEY);
				String text;
				text = getString(R.string.ask_pairing_describe, deviceName,alpha);

				return new AlertDialog.Builder(this).setTitle(R.string.ask_pairing_title)
						.setMessage(text).setPositiveButton(R.string.ask_pairing_accept, this)
						.setNegativeButton(R.string.ask_pairing_decline, this).create();
			default:
				return null;
		}
	}

	@Override
	public void onClick(DialogInterface dialog, int which)
	{
		if (which == Dialog.BUTTON1)
		{
			// Accept
			if (V) Log.v(TAG_PAIRING, PREFIX_LOG + "Inform pairing is accepted");
			mFinisher.mIsFinish=true;			
			CommunicationWithLock.putResult(LOCK_ASK_PAIRING,
					Boolean.TRUE);
			moveTaskToBack(true);
			finish();
		}
		else
		{
			// Decline
			if (V) Log.v(TAG_PAIRING, PREFIX_LOG + "Inform pairing is refused");
			mFinisher.mIsFinish=true;			
			CommunicationWithLock.putResult(LOCK_ASK_PAIRING,
				Boolean.FALSE);
			moveTaskToBack(true);
			finish();
		}
	}
	
	@Override
	protected void onUserLeaveHint()
	{
		CommunicationWithLock.putResult(LOCK_ASK_PAIRING,
			Boolean.FALSE);
		moveTaskToBack(true);
		finish();
	}
}
