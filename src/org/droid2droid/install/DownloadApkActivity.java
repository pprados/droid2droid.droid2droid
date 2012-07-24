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
package org.droid2droid.install;

import static org.droid2droid.Constants.LOCK_WAIT_INSTALL;
import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.TAG_INSTALL;
import static org.droid2droid.internal.Constants.V;

import java.lang.ref.WeakReference;

import org.droid2droid.CommunicationWithLock;
import org.droid2droid.R;
import org.droid2droid.binder.AbstractSrvRemoteAndroid;
import org.droid2droid.binder.AbstractSrvRemoteAndroid.DownloadFile;
import org.droid2droid.ui.Notifications;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

public final class DownloadApkActivity extends FragmentActivity
implements DialogInterface.OnClickListener,
DialogInterface.OnDismissListener
{
	private static final int MESSAGE_POST_FINISH = 0x1;
    private static final int MESSAGE_POST_PROGRESS = 0x2;
    private static final int MESSAGE_POST_CANCEL = 0x3;
    private static final int MESSAGE_POST_INSTALL = 0x4;
    
    // Receive progress dialog
    public static final int DIALOG_RECEIVE_ONGOING = 1;

    public static WeakReference<DownloadApkActivity> sMe;

    private View mView;
    private ProgressBar mProgress;
	private TextView mPercentView;
	private TextView mLine1;
	private TextView mLine2;
	private TextView mLine3;
	private TextView mLine5;
    
    volatile AbstractSrvRemoteAndroid.DownloadFile mTransInfo;

    public volatile boolean mCancel=false;
    static class AsyncTaskResult
    {
    	AsyncTaskResult(DownloadApkActivity activity)
    	{
    		mActivity=activity;
    	}
    	DownloadApkActivity mActivity;
    }
	private final Handler mHandler=new Handler() 
	{
        @Override
        public void handleMessage(Message msg) 
        {
            switch (msg.what) {
            	case MESSAGE_POST_FINISH:
            		if (mTransInfo!=null)
            		{
            			//dismissDialog(DIALOG_RECEIVE_ONGOING); // FIXME: dismiss dialog ou remove dialog ?
            			removeDialog(DIALOG_RECEIVE_ONGOING);
            		}
            		finish();
            		break;
                case MESSAGE_POST_PROGRESS:
                    onProgressUpdate();
                    break;
                case MESSAGE_POST_CANCEL:
                	onCancel();
                	break;
                case MESSAGE_POST_INSTALL:
                	onInstall();
                	break;
            }
        }
    };
	public final void publishProgress(int fd) 
	{
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv publish progress");
        mHandler.obtainMessage(MESSAGE_POST_PROGRESS).sendToTarget();
    }
	public final void publishCancel() 
	{
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv publish cancel");
        mHandler.obtainMessage(MESSAGE_POST_CANCEL).sendToTarget();
	}
	public final void publishInstall()
	{
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv publish install");
        mHandler.obtainMessage(MESSAGE_POST_INSTALL).sendToTarget();
	}
	public final void publishFinish() 
	{
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv publish finish");
        mHandler.obtainMessage(MESSAGE_POST_FINISH,
                new AsyncTaskResult(this)).sendToTarget();
		
	}
	
	// Called if another event is show. Replace the current value.
	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		setDialog(intent);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv create download apk dialog");
		if (sMe!=null) 
		{
			finish();
			return;
		}
		LayoutInflater inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mView=inflater.inflate(R.layout.file_transfer, null);
		mLine1=(TextView)mView.findViewById(R.id.line1_view);
		mLine2=(TextView)mView.findViewById(R.id.line2_view);
		mLine3=(TextView)mView.findViewById(R.id.line3_view);
		mLine5=(TextView)mView.findViewById(R.id.line5_view);
		mProgress = (ProgressBar)mView.findViewById(R.id.progress_transfer);
        mPercentView = (TextView)mView.findViewById(R.id.progress_percent);
		sMe=new WeakReference<DownloadApkActivity>(this);
		showDialog(DIALOG_RECEIVE_ONGOING);
	}
	
	@Override
	protected void onPrepareDialog(int id, Dialog dialog)
	{
		setDialog(getIntent());
		super.onPrepareDialog(id, dialog);
	}
	
	private void setDialog(Intent intent)
	{
		int id=intent.getIntExtra(Notifications.EXTRA_ID, -1);
		DownloadFile df=mTransInfo=AbstractSrvRemoteAndroid.getDowloadFile(id);
		Log.d(TAG_INSTALL,PREFIX_LOG+"setDialog intent="+System.identityHashCode(intent)+" id="+id);
		assert df!=null;
		if (df==null)
		{
			if (E) Log.e(TAG_INSTALL,PREFIX_LOG+"ERROR phatom id "+id+" when want to open DownloadApkActivity");
			return;
		}

		mLine1.setText(getString(R.string.download_line1, df.from));
	    mLine2.setText(getString(R.string.download_line2, df.label));
	    mLine3.setText(getString(R.string.download_line3, Formatter.formatFileSize(this,df.size)));
	    mLine5.setText(getString(R.string.download_line5));
	    onProgressUpdate();
	}
	
	@Override // FIXME: utiliser les Fragments à la place
	protected Dialog onCreateDialog(int id)
	{
        Dialog dlg=new AlertDialog.Builder(this)
			.setView(mView)
			.setPositiveButton(R.string.download_apk_hide, this)
			.setNegativeButton(R.string.download_apk_cancel, this)
			.create();
        dlg.setOnDismissListener(this);
        return dlg;
	}

    /**
     * customize the content of view
     */
    
    @Override
    protected void onStop()
    {
    	super.onStop();
    	finish();
    }
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		sMe=null;
	}
	
	@Override
	protected void onRestart()
	{
		super.onRestart();
		// Detach "cancel installation"
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv inform LOCK_WAIT_INSTALL to restart");
		CommunicationWithLock.putResult(LOCK_WAIT_INSTALL, null);
		finish();
	}
	
	public void onProgressUpdate()
	{
		if (mTransInfo!=null)
		{
	        long progress = mTransInfo.progress * 100 / mTransInfo.size;
			if (mProgress!=null)
				mProgress.setProgress((int)progress);
	        mPercentView.setText(Notifications.formatProgressText(mTransInfo.size,mTransInfo.progress));
		}
	}
	public void onCancel()
	{
		mCancel=true;
		finish();
	}
	public void onInstall()
	{
		mProgress.setProgress(100);
		finish();
	}
	@Override
	public void onClick(DialogInterface dialog, int which)
	{
		if (which==Dialog.BUTTON1)
		{
			finish();
		}
		else
		{
			onCancel();
		}
	}
	@Override
	public void onDismiss(DialogInterface dialog)
	{
		mTransInfo=null;
	}
}
