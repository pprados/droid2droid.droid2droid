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

package org.remoteandroid.ui.connect.qrcode.old;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.WindowManager;

import java.io.IOException;

import org.remoteandroid.R;
import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;

/**
 * 
 * @author Yohann Melo
 * 
 */

public final class BeepManager
{

	private static final float BEEP_VOLUME = 0.10f;

	private static final long VIBRATE_DURATION = 200L;

	private Context mContext;

	private MediaPlayer mMediaPlayer;

	private boolean mPlayBeep;

	private boolean mVibrate;

	public BeepManager(Context context)
	{
		mContext = context;
		
		mMediaPlayer = null;
		updatePrefs();
	}

	public void setActivity(Activity activity)
	{
		mContext = activity;
	}

	public void updatePrefs()
	{
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(mContext);
		mPlayBeep = shouldBeep(
			prefs, mContext);
		// vibrate = prefs.getBoolean(PreferencesActivity.KEY_VIBRATE, false);
		if (mPlayBeep && mMediaPlayer == null)
		{
			// The volume on STREAM_SYSTEM is not adjustable, and users found it too loud,
			// so we now play on the music stream.
			// FIXME: Volume
//			WindowManager wm=(WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
//			wm.getDefaultDisplay().setVolumeControlStream(AudioManager.STREAM_MUSIC);
			mMediaPlayer = buildMediaPlayer(mContext);
		}
	}

	public void playBeepSoundAndVibrate()
	{
		if (mPlayBeep && mMediaPlayer != null)
		{
			mMediaPlayer.start();
		}
		if (mVibrate)
		{
			Vibrator vibrator = (Vibrator) mContext
					.getSystemService(Context.VIBRATOR_SERVICE);
			vibrator.vibrate(VIBRATE_DURATION);
		}
	}

	private static boolean shouldBeep(SharedPreferences prefs, Context activity)
	{
		boolean shouldPlayBeep = true;
		// FIXME prefs.getBoolean(PreferencesActivity.KEY_PLAY_BEEP, true);
		if (shouldPlayBeep)
		{
			// See if sound settings overrides this
			AudioManager audioService = (AudioManager) activity
					.getSystemService(Context.AUDIO_SERVICE);
			if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL)
			{
				shouldPlayBeep = false;
			}
		}
		return shouldPlayBeep;
	}

	private static MediaPlayer buildMediaPlayer(Context activity)
	{
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

		AssetFileDescriptor file = activity.getResources().openRawResourceFd(R.raw.beep);
		try
		{
			mediaPlayer.setDataSource(
				file.getFileDescriptor(), file.getStartOffset(),
				file.getLength());
			file.close();
			mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
			mediaPlayer.prepare();
		}
		catch (IOException ioe)
		{
			if (W) Log.w(TAG_QRCODE, ioe);
			mediaPlayer = null;
		}
		return mediaPlayer;
	}

}
