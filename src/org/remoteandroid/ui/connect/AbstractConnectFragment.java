package org.remoteandroid.ui.connect;

import java.io.IOException;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.internal.Pair;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.ui.connect.old.AbstractBodyFragment;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.widget.Toast;

public class AbstractConnectFragment extends AbstractBodyFragment
implements TryConnectFragment.OnConnected
{
	private static final int DELAY_SHOW_TERMINATE=1000;
	private TryConnectFragment mDlg;
	protected void setProgressBarIndeterminateVisibility(boolean value)
	{
		ConnectActivity activity=(ConnectActivity)getActivity();
		if (activity!=null)
			activity.setProgressBarIndeterminateVisibility(value ? Boolean.TRUE : Boolean.FALSE);
	}
	
	protected void showConnect(String[] uris,boolean acceptAnonymous,Bundle param)
	{
		mDlg=TryConnectFragment.newTryConnectFragment(acceptAnonymous, uris,param);
		mDlg.setOnConnected(this);
		mDlg.show(getSupportFragmentManager(), "dialog");
	}

	protected void dismissDialog()
	{
		FragmentTransaction ft = getFragmentManager().beginTransaction();
	    mDlg.dismiss();
	    ft.commit();
	    mDlg=null;
	}
	@Override
	public void onConnected(final RemoteAndroidInfoImpl info)
	{
		mDlg.publishInDialog(R.string.connect_done,1000);
		Application.sHandler.postDelayed(new Runnable()
		{
			
			@Override
			public void run()
			{
				dismissDialog();
				getConnectActivity().onConnected(info);
			}
		}, DELAY_SHOW_TERMINATE);
	}

	@Override
	public void onCancel()
	{
		dismissDialog();
	}

	@Override
	public void onFailed(int err)
	{
		dismissDialog(); // Wait ok ?
		Toast.makeText(getActivity(), err, Toast.LENGTH_LONG).show();
	}
	
	final ConnectActivity getConnectActivity()
	{
		return (ConnectActivity)getActivity();
	}
	
	@Override
	public Object executePrejobs(ProgressJobs<?,?> progressJobs,TryConnectFragment fragment,Bundle param)
	{
		return null;
	}

	@Override
	public RemoteAndroidInfoImpl onConnect(String uri) throws SecurityException, IOException
	{
		if (getConnectActivity().isBroadcast())
		{
			// TODO
			return null;
		}
		else
		{
			Pair<RemoteAndroidInfoImpl,Long> msg=Application.getManager().askMsgCookie(Uri.parse(uri));
			if (msg==null || msg.second==0)
				throw new SecurityException();
			return msg.first;	
		}
	}
}
