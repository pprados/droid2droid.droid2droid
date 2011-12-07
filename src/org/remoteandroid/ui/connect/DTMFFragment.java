package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.Constants.TAG_DTMF;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.ui.connect.dtmf.RecognizerTask;
import org.remoteandroid.ui.connect.dtmf.RecordTask;

import com.google.protobuf.InvalidProtocolBufferException;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class DTMFFragment extends AbstractBodyFragment implements RecognizerTask.OnRecognize
{
	View mViewer;

	LinkedBlockingQueue<double[]> mBlockingQueue = new LinkedBlockingQueue<double[]>();
	RecordTask mRecordTask = new RecordTask(getAudioSource(),mBlockingQueue);
	RecognizerTask mRecognizerTask = new RecognizerTask(this,mBlockingQueue);
	
	Thread mThreadRecord;
	Thread mThreadRecognize;
	
	Messages.Candidates mCandidates;
	
	public final int getAudioSource()
	{
		TelephonyManager telephonyManager = (TelephonyManager)getActivity().getSystemService(Context.TELEPHONY_SERVICE);
		if (telephonyManager.getCallState() != TelephonyManager.PHONE_TYPE_NONE)
			return MediaRecorder.AudioSource.VOICE_DOWNLINK;
		
		return MediaRecorder.AudioSource.MIC;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer = inflater.inflate(R.layout.connect_sms, container, false);
		final ConnectActivity.FirstStep firstStep = new ConnectActivity.FirstStep()
		{
			public int run(ConnectActivity.TryConnection tryConn)
			{
				try
				{
					tryConn.publishMessage(R.string.connect_dtmf,0);

					synchronized (this)
					{
						try
						{
							wait(DTMF_TIMEOUT_WAIT);
						}
						catch (InterruptedException e)
						{
							// TODO
						}
					}
//					Messages.Candidates candidates = Messages.Candidates.parseFrom(bytes);
//					tryConn.setUris(ProtobufConvs.toUris(candidates));
					return 0;
				}
				catch (Exception e)
				{
					if (E) Log.e(TAG_CONNECT, PREFIX_LOG + "Error when retreive shorten ticket", e);
					return R.string.connect_sms_error_invalides_sms; 
				}
			}
		};
		((Button)mViewer.findViewById(R.id.execute)).setOnClickListener(new Button.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				ConnectActivity activity = (ConnectActivity) getActivity();
				activity.tryConnect(firstStep, new ArrayList<String>(), activity.isAcceptAnonymous());
			}
		});
		return mViewer;
	}
	@Override
	public void onResume()
	{
		super.onResume();
		
		mThreadRecord=new Thread(mRecordTask,"DTMF record");
		mThreadRecognize=new Thread(mRecognizerTask,"DTMF recognizer");
		mThreadRecord.start();
		mThreadRecognize.start();
	}
	@Override
	public void onPause()
	{
		super.onPause();
		mThreadRecord.interrupt();
		mThreadRecord=null;
		mThreadRecognize.interrupt();
		mThreadRecognize=null;
	}

	@Override
	public synchronized void reconize(byte reco)
	{
		// Ignore
	}
	@Override
	public synchronized void reconize(byte[] bytes)
	{
		try
		{
			mCandidates=Messages.Candidates.parseFrom(bytes);
			notify();
		}
		catch (InvalidProtocolBufferException e)
		{
			// TODO Auto-generated catch block
			if (D) Log.d(TAG_DTMF,PREFIX_LOG+"Invalide bytes");
		}
	}
}
