package org.remoteandroid.ui.connect.dtmf;

import java.io.IOException;
import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;
import org.remoteandroid.Application;
import org.remoteandroid.R;

import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.util.Log;
import static android.media.ToneGenerator.*;


/*
public class PlaySound extends Activity {
    // originally from http://marblemice.blogspot.com/2010/04/generate-and-play-tone-in-android.html
    // and modified by Steve Pomeroy <steve@staticfree.info>
    private final int duration = 3; // seconds
    private final int sampleRate = 8000;
    private final int numSamples = duration * sampleRate;
    private final double sample[] = new double[numSamples];
    private final double freqOfTone = 440; // hz

    private final byte generatedSnd[] = new byte[2 * numSamples];

    Handler handler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Use a new tread as this can take a while
        final Thread thread = new Thread(new Runnable() {
            public void run() {
                genTone();
                handler.post(new Runnable() {

                    public void run() {
                        playSound();
                    }
                });
            }
        });
        thread.start();
    }

    void genTone(){
        // fill out the array
        for (int i = 0; i < numSamples; ++i) {
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOfTone));
        }

        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

        }
    }

    void playSound(){
        final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, numSamples,
                AudioTrack.MODE_STATIC);
        audioTrack.write(generatedSnd, 0, generatedSnd.length);
        audioTrack.play();
    }
}
 */
public class PlayTask implements Runnable
{
	static int STREAM_DTMF = 8; // FIXME
	
	static final int[] TONES=
		{
		
			TONE_DTMF_1,
			TONE_DTMF_2,
			TONE_DTMF_3,
			TONE_DTMF_A,

			TONE_DTMF_4,
			TONE_DTMF_5,
			TONE_DTMF_6,
			TONE_DTMF_B,

			TONE_DTMF_7,
			TONE_DTMF_8,
			TONE_DTMF_9,
			TONE_DTMF_C,

			TONE_DTMF_S, // *
			TONE_DTMF_0,
			TONE_DTMF_P, // #
			TONE_DTMF_D,
			
			TONE_CDMA_ABBR_ALERT,
			TONE_CDMA_ONE_MIN_BEEP,
//			TONE_CDMA_KEYPAD_VOLUME_KEY_LITE,
//			TONE_PROP_NACK ,
//			TONE_CDMA_ABBR_INTERCEPT,
//			TONE_CDMA_NETWORK_BUSY_ONE_SHOT
/*			
			TONE_CDMA_ABBR_ALERT,
		TONE_CDMA_ABBR_REORDER,
		TONE_CDMA_CALLDROP_LITE,
		TONE_CDMA_CALL_SIGNAL_ISDN_PAT3,
		TONE_CDMA_CALL_SIGNAL_ISDN_PAT5,
		TONE_CDMA_CALL_SIGNAL_ISDN_PAT6,
		TONE_CDMA_CALL_SIGNAL_ISDN_PAT7,
		TONE_CDMA_KEYPAD_VOLUME_KEY_LITE,
		TONE_CDMA_NETWORK_BUSY,
		TONE_CDMA_REORDER,
		TONE_PROP_BEEP,
		TONE_PROP_PROMPT,
			TONE_CDMA_ONE_MIN_BEEP,
			TONE_CDMA_NETWORK_BUSY_ONE_SHOT,
			TONE_PROP_NACK,
*/			
		};
	private static final float BEEP_VOLUME = 0.10f;
	
	private AudioManager mAudioManager;
	private byte[] mBytes;
	ToneGenerator mToneGenerator;
	public PlayTask(AudioManager audioManager,byte[] bytes)
	{
		mAudioManager=audioManager;
		mBytes=bytes;
		mToneGenerator = new ToneGenerator(STREAM_DTMF, DTMF_VOLUME);
		
	}
	
	@Override
	public void run()
	{
		
		if (false)
		{
			versionAll();
			return;
		}
    	//int mode = m_amAudioManager.getMode();
    	//if (m_amAudioManager.isMusicActive())
    	//{ TODO : Mettre en pause la musique
    		//SoundPool.autoPause();
    	int audioMode=AudioManager.MODE_NORMAL;
    	boolean audioSpeaker;
		try
		{
			audioMode=mAudioManager.getMode();
			//audioSpeaker=mAudioManager.getToneGeneratorSpeakerphoneOn(); // FIXME

			mAudioManager.setMode(AudioManager.MODE_IN_CALL); 
			mAudioManager.setSpeakerphoneOn(true); 
			
//			versionOgg();
			
//			verionGen();
			
//			for (int i=0;i<TONES.length;++i)
//			{
//				Log.d("TEST","i="+i);
//	        	mToneGenerator.startTone(TONES[i]);
////	            try {Thread.sleep(DELAY_DTMF);} catch (InterruptedException e) {e.printStackTrace();}
//			}
//			mToneGenerator.stopTone();
			
            mToneGenerator.startTone(TONES[RecognizerTask.START_STOP]);
        	if (V) Log.v(TAG_DTMF,"Emit "+RecognizerTask.RECO_DTMF.charAt(RecognizerTask.START_STOP));
            try {Thread.sleep(DTMF_DELAY_START_STOP);} catch (InterruptedException e) {}
            mToneGenerator.stopTone();
            int last=-1;
	        for (int i=0;i<mBytes.length;++i)
	        {
	        	byte b=mBytes[i];
	        	
	        	int tone=(b>>4) & 0xF;
	        	if (tone==last)
	        		tone=RecognizerTask.REPEAT;
	        	if (V) Log.v(TAG_DTMF,"Emit "+RecognizerTask.RECO_DTMF.charAt(tone));
	        	mToneGenerator.startTone(TONES[tone]);
	            try {Thread.sleep(DTMF_DELAY_EMISSION);} catch (InterruptedException e) {e.printStackTrace();}
	            last=tone;
	            
	        	tone=b & 0xF;
	        	if (tone==last)
	        		tone=RecognizerTask.REPEAT;
	        	if (V) Log.v(TAG_DTMF,"Emit "+RecognizerTask.RECO_DTMF.charAt(tone));
	        	mToneGenerator.startTone(TONES[tone]);
	            try {Thread.sleep(DTMF_DELAY_EMISSION);} catch (InterruptedException e) {e.printStackTrace();}
	            last=tone;
	        }

	        mToneGenerator.startTone(TONES[RecognizerTask.START_STOP]);
        	if (V) Log.v(TAG_DTMF,"Emit "+RecognizerTask.RECO_DTMF.charAt(RecognizerTask.START_STOP));
            try {Thread.sleep(DTMF_DELAY_START_STOP);} catch (InterruptedException e) {}
		}
		finally
		{
            mToneGenerator.stopTone();
			mAudioManager.setMode(audioMode); 
			//mAudioManager.setSpeakerphoneOn(audioSpeaker); 
		}
	}

	private void verionGen()
	{
		genTone();
		playSound();
	}

	private void versionOgg()
	{
		try
		{
			AssetFileDescriptor file = Application.sAppContext.getResources().openRawResourceFd(R.raw.dtmf_0);			
			MediaPlayer mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			// When the beep has finished playing, rewind to queue up another one.
			mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
					{
						public void onCompletion(MediaPlayer player)
						{
							player.seekTo(0);
						}
					});
			mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(),file.getLength());
			file.close();
			mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
			mediaPlayer.prepare();
			mediaPlayer.start();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

//--------------------------------------------------------------	
	private final int duration = 3; // seconds
    private final int sampleRate = 8000;
    private final int numSamples = duration * sampleRate;
    private final double[] sample = new double[numSamples];
    private final double freqOfTone = 440; // hz
// DTMF-0 1336Hz, 941Hz
    private final byte generatedSnd[] = new byte[2 * numSamples];
    private final short shortSnd[] = new short[numSamples];
	
    void genTone(){
    	double freqOne=440;
    	double freqTwo=941;
    	// fill out the array
    	// TODO: merge les boucles

//        for (int i = 0; i < numSamples; ++i) {
//                sample[i] = 32767+16383*Math.sin(u1*i)+16383*Math.sin(u2*i);
//            }
        for (int i = 0; i < numSamples; ++i) {
        	
//        	    return (short) (A * (Math.sin(2 * Math.PI * f1 * t) + Math.sin(2 * Math.PI * f2 * t)));
//            sample[i] = (Math.sin(2 * Math.PI * i * freqOne)) + Math.sin(2 * Math.PI * i * freqTwo));
        }
//        for (int i = 0; i < numSamples; ++i) {
//            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate/freqOne));
//        }
//        for (int i = 0; i < numSamples; ++i) {
//            sample[i] += Math.sin(2 * Math.PI * i / (sampleRate/freqTwo));
//        }

        
        for(int i = 0; i < numSamples; ++i){
            double time = i/sampleRate;
            double freq = freqOne;//arbitrary frequency
            double sinValue =
              (Math.sin(2*Math.PI*freq*time) +
              Math.sin(2*Math.PI*(freq/1.8)*time) +
              Math.sin(2*Math.PI*(freq/1.5)*time))/3.0;
            shortSnd[i]=(short)(16000*sinValue);
          }//end for loop        
        
        // convert to 16 bit pcm sound array
        // assumes the sample buffer is normalised.
        int idx = 0;
        for (final double dVal : sample) {
            // scale to maximum amplitude
            final short val = (short) ((dVal * 32767));
            // in 16 bit wav PCM, first byte is the low order byte
            generatedSnd[idx++] = (byte) (val & 0x00ff);
            generatedSnd[idx++] = (byte) ((val & 0xff00) >>> 8);

        }
    }
    void playSound(){
        final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, numSamples,
                AudioTrack.MODE_STATIC);
        audioTrack.write(generatedSnd, 0, generatedSnd.length);
//        audioTrack.write(shortSnd, 0, shortSnd.length);
        audioTrack.play();
    }

	
	private void versionAll()
	{
		try
		{
			mAudioManager.setMode(AudioManager.MODE_IN_CALL); 
			mAudioManager.setSpeakerphoneOn(true); 
			for (int i=0;i<TONES.length;++i)
			{
				mToneGenerator.startTone(TONES[i]);
	            try {Thread.sleep(DTMF_DELAY_EMISSION);} catch (InterruptedException e) {e.printStackTrace();}
			}
			mToneGenerator.stopTone();
		}
		finally
		{}
	}
	
}
