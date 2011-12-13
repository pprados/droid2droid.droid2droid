package org.remoteandroid.test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.ui.connect.dtmf.PlayTask;
import org.remoteandroid.ui.connect.dtmf.RecognizerTask;
import org.remoteandroid.ui.connect.dtmf.RecordTask;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.*;

public class DTMFActivity extends Activity implements OnClickListener, RecognizerTask.OnRecognize
{
	private EditText recognizeredEditText;
	private String recognizeredText;
	
	private boolean started;
	private RecordTask recordTask;	
	private RecognizerTask recognizerTask;	
	private BlockingQueue<double[]> blockingQueue;
	private char lastValue;
	
	Thread mThreadRecord;
	Thread mThreadRecognize;

	
	public static final int TONE_CDMA_ONE_MIN_BEEP = 			88; //CDMA One Min Beep tone: 1150Hz+770Hz 400ms ON
	public static final int TONE_PROP_NACK  = 					26; //Proprietary tone, negative acknowlegement: 300Hz+400Hz+500Hz, 400ms ON
	public static final int TONE_CDMA_NETWORK_BUSY_ONE_SHOT  = 	96; //devrait etre STOP puisque emission OFF... CDMA_NETWORK_BUSY_ONE_SHOT tone: 425Hz 500ms ON, 500ms OFF.

	public static final int TONE_DTMF_S = 10;	// #
	public static final int TONE_DTMF_P = 11;	// *
	public static final int TONE_DTMF_A = 12;
	public static final int TONE_DTMF_B = 13;
	public static final int TONE_DTMF_C = 14;
	public static final int TONE_DTMF_D = 15;
	
	private int m_funkyTown[] = //Tableau d'emission
	{
		1,	2,	3,	4,	5,	6,	7,	8,	9,	0,
		
		TONE_DTMF_S,
		TONE_DTMF_P,
		TONE_DTMF_A,
		TONE_DTMF_B,
		TONE_DTMF_C,
		TONE_DTMF_D,
		
		TONE_CDMA_ONE_MIN_BEEP,  
		TONE_PROP_NACK ,
		TONE_CDMA_NETWORK_BUSY_ONE_SHOT
		
	};
	
	static int VOLUME = /*100*/50;
	static int STREAM_DTMF = 8;
	 
	private Button emettre;
	private Button ecouter;
	 
	private AudioManager mAudioManager; 
	
	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.test_dtmf_activity);

		mAudioManager=(AudioManager)getSystemService(AUDIO_SERVICE);
		recognizeredEditText = (EditText)this.findViewById(R.id.recognizeredText);
		recognizeredEditText.setFocusable(false);
		
		recognizeredText = ""; // sans cette instruction affichage de "null" dans l'EditText ???...
		
		ecouter = (Button) findViewById(R.id.ecouter);
		emettre = (Button) findViewById(R.id.emettre);

		ecouter.setOnClickListener(this);
		emettre.setOnClickListener(this);
	}

	@Override
	protected void onPause() 
	{
		super.onPause();
	}
	
	@Override
	protected void onDestroy() 
	{
		super.onDestroy();
		
		started = false;
		if (mThreadRecord!=null)
		{
			mThreadRecord.interrupt();
			mThreadRecord=null;
		}
		if (mThreadRecognize!=null)
		{
			mThreadRecognize.interrupt();
			mThreadRecognize=null;
		}
	}
	
	@Override
	protected void onResume() 
	{
		super.onResume();
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) 
	{
		savedInstanceState.putCharSequence("state", ecouter.getText());
		super.onSaveInstanceState(savedInstanceState);
	}
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) 
	{
	  super.onRestoreInstanceState(savedInstanceState);
	  ecouter.setText(savedInstanceState.getCharSequence("state"));
	}

	public int getAudioSource()
	{
		TelephonyManager telephonyManager = (TelephonyManager)getSystemService(TELEPHONY_SERVICE);
		if (telephonyManager.getCallState() != TelephonyManager.PHONE_TYPE_NONE)
			return MediaRecorder.AudioSource.VOICE_DOWNLINK;
		
		return MediaRecorder.AudioSource.MIC;
	}
	
	public void addText(Character c)
	{
		if(c != ' ')
		{
			recognizeredText += c;
			recognizeredEditText.setText(recognizeredText);
		}
	}
	
	public void keyReady(char key) 
	{
		if(key != ' ')
			if(lastValue != key)
				addText(key);
		lastValue = key;
	}
	
	public boolean isStarted() 		{return started;}

	@Override
	public void onClick(View v) 
	{
		if (v == emettre)
		{
			final Thread thread = new Thread(new PlayTask(mAudioManager,new byte[0]));
//				new Runnable() 
//	        {
//	            public void run() 
//	            {
//	            	//int mode = m_amAudioManager.getMode();
//	            	//if (m_amAudioManager.isMusicActive())
//	            	//{ TODO : Mettre en pause la musique
//	            		//SoundPool.autoPause();
//	            	
//		            	m_amAudioManager = (AudioManager)getSystemService(AUDIO_SERVICE);  
//		            	m_amAudioManager.setMode(AudioManager.MODE_IN_CALL); 
//		            	m_amAudioManager.setSpeakerphoneOn(true); 
//		            	
//						ToneGenerator toneGenerator = new ToneGenerator(STREAM_DTMF, VOLUME);
//						
//				        for(int u : m_funkyTown)
//				        {
//				        	toneGenerator.startTone(u);
//				            try {Thread.sleep(400);} catch (InterruptedException e) {e.printStackTrace();}
//				            toneGenerator.stopTone();
//				        }
//
//				        m_amAudioManager.setMode(AudioManager.MODE_NORMAL);
//				        
//				        //m_amAudioManager.setMode(mode);
//	            	//}
//				        
//	            }
//	        });
	       	thread.start();
		}
		
		
		if (v == ecouter)
		{
			if(started == false)
			{
				started = true;
				blockingQueue = new LinkedBlockingQueue<double[]>();
				recordTask = new RecordTask(getAudioSource(),blockingQueue);
				
				recognizerTask = new RecognizerTask(this,blockingQueue);
				mThreadRecord=new Thread(recordTask,"DTMF Record");
				mThreadRecognize=new Thread(recognizerTask,"DTMF recognizer");
				mThreadRecord.start();
				mThreadRecognize.start();
				
				ecouter.setText("Arreter");
			}
			else
			{
				started = false;
				mThreadRecord.interrupt();
				mThreadRecognize.interrupt();
				ecouter.setText("Ecouter");
			}
		}
	}

	@Override
	public void reconize(final byte reco)
	{
		if (D) Log.d(TAG_DTMF,PREFIX_LOG+"Reconize "+reco);
		runOnUiThread(new Runnable()
		{
			
			@Override
			public void run()
			{
				// TODO Auto-generated method stub
				recognizeredEditText.setText(recognizeredEditText.getText().toString()+RecognizerTask.RECO_DTMF.charAt(reco));
			}
		});
	}
	@Override
	public void reconize(byte[] reco)
	{
	}
	
}