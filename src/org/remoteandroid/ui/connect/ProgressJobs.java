package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.PROGRESS_TIMER;

import java.util.Timer;
import java.util.TimerTask;

import android.os.AsyncTask;

public abstract class ProgressJobs<Params,Result> extends AsyncTask<Params,Integer,Result>
{
	public static final Object CANCEL=new Object();
	
	Timer mTimer=new Timer();
	private long[] mEstimation;
	private long mProgress;
	
	private long mStartTime;
	private int mDelayMax;
	private int mCurrentStep=0;
	private int mMax=1000;
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

