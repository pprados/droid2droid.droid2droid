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

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Finishes an activity after a period of inactivity if the device is on battery
 * power.
 * 
 * @author Yohann Melo
 */
public final class InactivityTimer
{

	private static final int INACTIVITY_DELAY_SECONDS = 5 * 60;

	private final ScheduledExecutorService mInactivityTimer = Executors
			.newSingleThreadScheduledExecutor(new DaemonThreadFactory());

	private volatile Activity mActivity;

	private ScheduledFuture<?> mInactivityFuture = null;

	private final PowerStatusReceiver mPowerStatusReceiver = new PowerStatusReceiver();

	public InactivityTimer(Activity activity)
	{
		this.mActivity = activity;
		onActivity();
	}

	public void setActivity(Activity activity)
	{
		mActivity = activity;
	}

	public void onActivity()
	{
		cancel();
		if (!mInactivityTimer.isShutdown())
		{
			try
			{
				mInactivityFuture = mInactivityTimer.schedule(
					new FinishListener(mActivity), INACTIVITY_DELAY_SECONDS,
					TimeUnit.SECONDS);
			}
			catch (RejectedExecutionException ree)
			{
				// surprising, but could be normal if for some reason the
				// implementation just
				// doesn't
				// think it can shcedule again. Since this time-out is
				// non-essential, just forget it
			}
		}
	}

	public void onPause()
	{
		mActivity.unregisterReceiver(mPowerStatusReceiver);
	}

	public void onResume()
	{
		mActivity.registerReceiver(mPowerStatusReceiver,
			new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
	}

	private void cancel()
	{
		if (mInactivityFuture != null)
		{
			mInactivityFuture.cancel(true);
			mInactivityFuture = null;
		}
	}

	public void shutdown()
	{
		cancel();
		mInactivityTimer.shutdown();
	}

	private static final class DaemonThreadFactory implements ThreadFactory
	{
		public Thread newThread(Runnable runnable)
		{
			Thread thread = new Thread(runnable);
			thread.setDaemon(true);
			return thread;
		}
	}

	private final class PowerStatusReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction()))
			{
				// 0 indicates that we're on battery
				// In Android 2.0+, use BatteryManager.EXTRA_PLUGGED
				if (intent.getIntExtra(
					"plugged", -1) == 0)
				{
					InactivityTimer.this.onActivity();
				}
				else
				{
					InactivityTimer.this.cancel();
				}
			}
		}
	}

}
