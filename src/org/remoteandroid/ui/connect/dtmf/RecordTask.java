package org.remoteandroid.ui.connect.dtmf;

import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.*;

import java.util.concurrent.BlockingQueue;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.util.Log;

public class RecordTask implements Runnable
{
	private static final int DTMF_FREQUENCY = 16000;
	private static int DTMF_CHANNEL_CONFIGURATION = AudioFormat.CHANNEL_CONFIGURATION_MONO;
	private static int DTMF_AUDO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

	private BlockingQueue<double[]> mBlockingQueue;
	private int mAudioSource;

	public RecordTask(int audioSource, BlockingQueue<double[]> blockingQueue) 
	{
		mAudioSource = audioSource;
		mBlockingQueue = blockingQueue;
	}

	@Override
	public void run() 
	{
		try 
		{
			int bufferSize = AudioRecord.getMinBufferSize(DTMF_FREQUENCY, DTMF_CHANNEL_CONFIGURATION, DTMF_AUDO_ENCODING);
			AudioRecord audioRecord = 
				new AudioRecord(mAudioSource, DTMF_FREQUENCY, DTMF_CHANNEL_CONFIGURATION, DTMF_AUDO_ENCODING, bufferSize);
			short[] buffer = new short[bufferSize];
			audioRecord.startRecording();
			
			//while (activity.isStarted())
			while (!Thread.interrupted())
			{
				audioRecord.read(buffer, 0, bufferSize);
				double[] dataBlock = DoubleArrayPool.fromShortArray(buffer);
				mBlockingQueue.put(dataBlock);
			}
			audioRecord.stop();
		} 
		catch (InterruptedException t) 
		{
			if (D) Log.d(TAG_DTMF, "RecordTask interrupted");
		}
	}
	
}