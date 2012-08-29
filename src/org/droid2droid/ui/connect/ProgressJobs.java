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
package org.droid2droid.ui.connect;

import static org.droid2droid.Constants.PROGRESS_TIMER;

import java.util.Timer;
import java.util.TimerTask;

import android.os.AsyncTask;

public abstract class ProgressJobs<Params,Result> extends AsyncTask<Params,Integer,Result>
{
	public static final Object OK=new Object();
	public static final Object CANCEL=new Object();
	
	private final Timer mTimer=new Timer();
	private long[] mEstimation;
	private long mProgress;
	
	private long mStartTime;
	private int mDelayMax;
	private int mCurrentStep=0;
	private final int mMax=1000;
	private int mLastMsg;
	
	ProgressJobs()
	{
		mStartTime=System.currentTimeMillis();
		mTimer.schedule(new TimerTask()
		{
			@Override
			public void run()
			{
				onTimeEvent();
			}
		}, 0,PROGRESS_TIMER);	

	}
	void stop()
	{
		mTimer.cancel();
	}
	synchronized public void setEstimations(long[] delays)
	{
		mEstimation=delays;
		
		mDelayMax=0;
		for (int i=0;i<delays.length;++i)
		{
			mDelayMax+=delays[i];
		}
	}
	synchronized void onTimeEvent()
	{
		publishProgress(mLastMsg);
	}
	void resetCurrentStep()
	{
		mCurrentStep=-1;
		mProgress=0;
		mStartTime=System.currentTimeMillis();
	}
	synchronized void incCurrentStep()
	{
		mProgress=0;
		if (mCurrentStep>=0)
		{
			for (int i=0;i<=mCurrentStep;++i)
				mProgress+=mEstimation[i];
		}
		mStartTime=System.currentTimeMillis();
		++mCurrentStep;
		publishProgress(mLastMsg);
	}

	int getMax()
	{
		return mMax;
	}
	synchronized public void publishProgress(int msg)
	{
		mLastMsg=msg;
		if (mEstimation==null || mCurrentStep<0) return;
		final long delay=System.currentTimeMillis()-mStartTime;
		if (delay>mEstimation[mCurrentStep])
			return; // Too long
		int delta=(int)((mProgress+delay)*mMax/mDelayMax);
		super.publishProgress(msg,delta);
	}
}

