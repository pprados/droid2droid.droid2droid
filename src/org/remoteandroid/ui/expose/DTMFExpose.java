package org.remoteandroid.ui.expose;

import org.remoteandroid.R;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.connect.dtmf.PlayTask;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.util.Log;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.*;

class DTMFExpose extends Expose
{
	DTMFExpose()
	{
		super(R.string.expose_sound,KEY_SOUND);
	}

	@Override
	public void startExposition(Activity context)
	{
		try
		{
			AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);  
			Messages.Candidates candidates = Trusted.getConnectMessage(context);
			byte[] data = candidates.toByteArray();
//			data=new byte[]{0x01,0x23};
			data=new byte[]{0x12,0x34};
			new Thread(new PlayTask(audioManager, data)).start();
		}
		catch (Exception e)
		{
			// TODO
			if (D) Log.d(TAG_DTMF,PREFIX_LOG+"error ",e);
		}
	}
}
