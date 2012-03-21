package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.ETHERNET_TRY_TIMEOUT;
import static org.remoteandroid.internal.Constants.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;

import org.remoteandroid.Application;
import org.remoteandroid.Cookies;
import org.remoteandroid.R;
import org.remoteandroid.internal.AbstractProtoBufRemoteAndroid;
import org.remoteandroid.internal.Driver;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.Pair;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.RemoteAndroidManagerImpl;
import org.remoteandroid.ui.AbstractBodyFragment;

import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.FragmentTransaction;
import android.widget.Toast;

public abstract class AbstractConnectFragment extends AbstractBodyFragment
implements ConnectDialogFragment.OnConnected
{
	public static final long ESTIMATION_CONNEXION_3G=TIMEOUT_CONNECT_WIFI;
	private static final int DELAY_SHOW_TERMINATE=1000;
	private ConnectDialogFragment mDlg;
	protected void setProgressBarIndeterminateVisibility(boolean value)
	{
		ConnectActivity activity=(ConnectActivity)getActivity();
		if (activity!=null)
			activity.setSupportProgressBarIndeterminateVisibility(value ? Boolean.TRUE : Boolean.FALSE);
	}
	
	protected void showConnect(String[] uris,boolean acceptAnonymous,Bundle param)
	{
		mDlg=ConnectDialogFragment.newTryConnectFragment(acceptAnonymous, uris,param);
		mDlg.setOnConnected(this);
		mDlg.show(getFragmentManager(), "dialog");
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
	
	protected final ConnectActivity getConnectActivity()
	{
		return (ConnectActivity)getActivity();
	}
	
	@Override
	public Object doTryConnect(ProgressJobs<?,?> progressJobs,
			ConnectDialogFragment fragment,
			String[] uris,
			Bundle param)
	{
		long[] estimations=new long[uris.length];
		Arrays.fill(estimations, ESTIMATION_CONNEXION_3G);
		progressJobs.setEstimations(estimations);
		progressJobs.resetCurrentStep();
		return ConnectDialogFragment.tryAllUris(progressJobs,uris,this);
	}

	@Override
	public Object onTryConnect(String uri) throws SecurityException, IOException, RemoteException
	{
		if (getConnectActivity().isBroadcast())
		{
			AbstractProtoBufRemoteAndroid binder=null;
			try
			{
				final Uri uuri=Uri.parse(uri);
				Driver driver=RemoteAndroidManagerImpl.sDrivers.get(uuri.getScheme());
				if (driver==null)
					throw new MalformedURLException("Unknown "+uri);
				binder=(AbstractProtoBufRemoteAndroid)driver.factoryBinder(Application.sAppContext,Application.getManager(),uuri);
				if (binder.connect(Type.CONNECT_FOR_BROADCAST, 0,ETHERNET_TRY_TIMEOUT))
					return ProgressJobs.OK; // Hack, simulate normal connection
				else
					throw new IOException();
			}
			finally
			{
				if (binder!=null)
					binder.close();
			}
		}
		else
		{
			Pair<RemoteAndroidInfoImpl,Long> msg=Application.getManager().askMsgCookie(Uri.parse(uri));
			if (msg==null || msg.second==0)
				throw new SecurityException();
			RemoteAndroidInfoImpl remoteInfo=msg.first;
			final long cookie=msg.second;
			if (cookie!=COOKIE_NO && cookie!=COOKIE_EXCEPTION)
				Application.sDiscover.addCookie(remoteInfo,cookie);
			return msg.first;
		}
	}
}
