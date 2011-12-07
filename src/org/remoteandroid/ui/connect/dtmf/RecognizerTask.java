package org.remoteandroid.ui.connect.dtmf;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

import android.util.Log;

public class RecognizerTask implements Runnable
{
	
	public interface OnRecognize
	{
		void reconize(byte b);
		void reconize(byte[] bytes);

	}

	public static final byte REPEAT = (byte) 16;

	public static final byte START_STOP = (byte) 17;
//	public static final byte START = (byte) 17;
//	public static final byte STOP = (byte) 18;

	public static final byte EMPTY = (byte) -1;

	public static final String RECO_DTMF="123A456B789C*0#DSsR";
	
	private static int[] sTones=new int[]
			{
				45,77, // DTMF_1
				45,86, // DTMF_2
				45,95, // DTMF_3
				45,104,// DTMF_A
				
				49,77, // DTMF_4
				49,86, // DTMF_5
				49,95, // DTMF_6
				49,105,// DTMF_B
				
				55,77, // DTMF_7
				55,86, // DTMF_8
				55,95, // DTMF_9
				55,104,// DTMF_C
				
				60,77, // DTMF_S
				60,86, // DTMF_0
				60,95, // DTMF_P
				60,104,// DTMF_D
				
				17,76, // Start // 22,88, 6,76,
				32,77, // Stop
				27,82, // Repeat
			};
	static final byte[] ERROR = new byte[0];

	private BlockingQueue<double[]> mBlockingQueue;

	private OnRecognize mCallBack;

	private byte[] mData = new byte[10];

	private int mCursor = 0;

	private byte mLast = -1;

	private boolean mState;
	private int mNbPresence;

	private static final boolean STATE_START = false;

	private static final boolean STATE_DATA = true;

	public RecognizerTask(OnRecognize callback, BlockingQueue<double[]> blockingQueue)
	{
		mBlockingQueue = blockingQueue;
		mCallBack = callback;
	}

	private byte getRecognizedKey(double[] spectrum)
	{
		int lowMax = getMax(0, 75, spectrum);
		int highMax = getMax(75, 150, spectrum);
		for (int i = (sTones.length/2) - 1; i >= 0; --i)
		{
			if (match(i,lowMax, highMax))
			{
//				if (V) Log.v(TAG_DTMF, PREFIX_LOG + "Find i="+i+" low=" + lowMax + " high=" + highMax);
				return (byte)i;
			}
		}
		return EMPTY;
	}
	public boolean match(int pos,int lowFrequency, int highFrequency) 
	{
		final int posLowFrequency=sTones[pos*2];
		final int posHighFrequency=sTones[pos*2+1];
		int lowFreq = (lowFrequency - posLowFrequency) * (lowFrequency - posLowFrequency);
		int highFreq = (highFrequency - posHighFrequency) * (highFrequency - posHighFrequency);
		//if (D) Log.d(TAG_DTMF,PREFIX_LOG+"Reco ? lowFreq="+lowFrequency+" highFreq="+highFrequency);
		if (lowFreq < (DTMF_FREQUENCY_DELTA*2) && highFreq < (DTMF_FREQUENCY_DELTA*2))
		{
			//if (D) Log.d(TAG_DTMF,PREFIX_LOG+"Reco lowFreq="+lowFreq+" highFreq="+highFreq);
			return true;
		}
		return false;
	}


	private int getMax(int start, int end, double[] spectrum)
	{
		int max = 0;
		double maxValue = 0;
		for (int i = start; i <= end; ++i)
			if (maxValue < spectrum[i])
			{
				maxValue = spectrum[i];
				max = i;
			}
		return max;
	}

	private void reset()
	{
		mState = STATE_START;
		mCursor = 0;
		mLast = -1;
		mNbPresence=0;
	}

	@Override
	public void run()
	{
		try
		{
			//test();
			while (!Thread.interrupted())
			{
				double[] dataBlock = mBlockingQueue.take();
				double[] spectrum = FFT.magnitudeSpectrum(dataBlock);

				byte key = getRecognizedKey(spectrum);
//				if (key!=EMPTY)
//					mCallBack.reconize(key);
				if (V && key>EMPTY) 
					Log.v(TAG_DTMF, "Reconize " + RECO_DTMF.charAt(key));
				if (key!=EMPTY)
				{
					byte[] rc=fsm(key);
					 if (rc!=null)
					 {
						 mCallBack.reconize(rc);
					 }
				}
				DoubleArrayPool.release(dataBlock);
			}
		}
		catch (InterruptedException e)
		{
			if (D)
				Log.d(
					TAG_DTMF, "RecognizerTask InterruptedException ", e);
		}
	}

	private void normalize(double[] spectrum)
	{
		double maxValue = 0.0;
		for (int i = 0; i < spectrum.length; ++i)
			if (maxValue < spectrum[i])
				maxValue = spectrum[i];

		if (maxValue != 0)
			for (int i = 0; i < spectrum.length; ++i)
				spectrum[i] /= maxValue;
	}
	private void emitLast(byte c)
	{
		if (mLast==c)
		{
			++mNbPresence;
		}
		else
		{
			if (mLast!=c && mNbPresence<DTMF_MIN_PRESENCE)
			{
				mNbPresence=0;
				mLast = -1;
				return;
			}
			if (mLast==REPEAT)
			{
				if (mCursor > 0)
				{
					if (D) mCallBack.reconize(mLast);
					add(mData[mCursor - 1]);
				}
			}
			else
			{
				if (D) mCallBack.reconize(mLast);
				add(mLast);
			}
			mNbPresence=1;
			mLast = c;
		}
	}
	private byte[] fsm(byte c)
	{
		if (mState == STATE_START)
		{
			mLast=c;
			++mNbPresence;
			switch (c)
			{
				case START_STOP:
					break;
				default:
					if (mLast==START_STOP && mNbPresence>=DTMF_MIN_PRESENCE_START_STOP)
					{
						mState = STATE_DATA;
					}
					else
					{
						mLast=-1;
						mNbPresence=0;
					}
					break;
			}
		}
		else
		{
			switch (c)
			{
				case START_STOP:
					if (mLast==c)
					{
						if (++mNbPresence>=DTMF_MIN_PRESENCE_START_STOP)
						{
							emitLast(c);
							mState = STATE_START;
							byte[] rc = toByteArray();
							reset();
							return rc;
						}
					}
					else
					{
						emitLast(c);
					}
					break;

				case EMPTY:
					break;

				case REPEAT:
					emitLast(c);
					break;

				default:
					emitLast(c);
					break;
			}
		}

		return null;
	}

	private void add(byte b)
	{
		if (mCursor == mData.length)
		{
			byte[] data = new byte[mData.length * 2];
			System.arraycopy(
				mData, 0, data, 0, mData.length);
			mData = data;
		}
		mData[mCursor++] = b;
	}

	private byte[] toByteArray()
	{
		if ((mCursor & 1) == 1)
			return ERROR;
		byte[] rc = new byte[mCursor / 2];
		for (int i = 0; i < mCursor; i += 2)
		{
			rc[i / 2] = (byte) (mData[i] << 4 | mData[i + 1]);
		}
		return rc;
	}

	private void test()
	{
		Log.d(
			TAG_DTMF, "test1=" + Arrays.equals(
				test(new byte[]
				{ START_STOP, START_STOP,0, 0, 0, 1, 1, 1, START_STOP, START_STOP }), new byte[]
				{ 0x01 }));
		Log.d(
			TAG_DTMF, "test1=" + Arrays.equals(
				test(new byte[]
				{ START_STOP, START_STOP,0, 0, 1, 1, 2, 2, 3, 3, START_STOP, START_STOP }), new byte[]
				{ 0x01, 0x23 }));
		Log.d(
			TAG_DTMF, "test1=" + Arrays.equals(
				test(new byte[]
				{ START_STOP, START_STOP,0, 0, 0, 0, 1, 1, START_STOP, START_STOP }), new byte[]
				{ 0x01 }));
		Log.d(
			TAG_DTMF, "test2=" + Arrays.equals(
				test(new byte[]
				{ START_STOP, 1, 1, REPEAT, REPEAT, 1, 1, 2, 2, START_STOP, START_STOP }), new byte[]
				{ 0x11, 0x12 }));
		Log.d(
			TAG_DTMF, "test3=" + Arrays.equals(
				test(new byte[]
				{ START_STOP, 1, REPEAT, REPEAT,REPEAT,1, 1, REPEAT, REPEAT, REPEAT,1, 1, 2, 2, START_STOP, START_STOP }), new byte[]
				{ 0x11, 0x11, 0x12 }));
		Log.d(
			TAG_DTMF, "test4=" + Arrays.equals(
				test(new byte[]
				{ START_STOP, 1 }), ERROR));
		Log.d(
			TAG_DTMF, "test5=" + Arrays.equals(
				test(new byte[]
				{ START_STOP, 1, START_STOP }), ERROR));
	}

	private byte[] test(byte[] test)
	{
		for (int i = 0; i < test.length; ++i)
		{
			byte[] rc = fsm(test[i]);
			if (rc != null)
				return rc;
		}
		return ERROR;
	}

}
