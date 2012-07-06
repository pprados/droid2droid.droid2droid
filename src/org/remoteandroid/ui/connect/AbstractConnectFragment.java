package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.DELAY_SHOW_TERMINATE;
import static org.remoteandroid.Constants.ETHERNET_TRY_TIMEOUT;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.COOKIE_EXCEPTION;
import static org.remoteandroid.internal.Constants.COOKIE_NO;
import static org.remoteandroid.internal.Constants.COOKIE_SECURITY;
import static org.remoteandroid.internal.Constants.TIMEOUT_CONNECT_WIFI;
import static org.remoteandroid.internal.Constants.V;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;

import org.remoteandroid.R;
import org.remoteandroid.RAApplication;
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
import android.util.Log;
import android.widget.Toast;

public abstract class AbstractConnectFragment extends AbstractBodyFragment
implements ConnectDialogFragment.OnConnected
{
	public static final long ESTIMATION_CONNEXION_3G=TIMEOUT_CONNECT_WIFI;
	private ConnectDialogFragment mDlg;
	protected void setProgressBarIndeterminateVisibility(boolean value)
	{
		ConnectActivity activity=(ConnectActivity)getActivity();
		if (activity!=null)
			activity.setSupportProgressBarIndeterminateVisibility(value ? Boolean.TRUE : Boolean.FALSE);
	}
	
	protected void showConnect(String[] uris,int flags,Bundle param)
	{
		mDlg=ConnectDialogFragment.newTryConnectFragment(flags, uris,param);
		mDlg.setOnConnected(this);
		mDlg.show(getFragmentManager(), "dialog");
	}

	protected void dismissDialog()
	{
		if (mDlg!=null)
		{
			FragmentTransaction ft = getFragmentManager().beginTransaction();
			final ConnectDialogFragment dlg=mDlg;
			mDlg=null;
		    dlg.dismiss();
		    ft.commit();
		}
	}
	@Override
	public void onPause()
	{
		super.onPause();
		dismissDialog();
	}
	@Override
	public void onConnected(final RemoteAndroidInfoImpl info)
	{
		if (mDlg!=null)
		{
			mDlg.publishInDialog(R.string.connect_done,1000);
			RAApplication.sHandler.postDelayed(new Runnable()
			{
				
				@Override
				public void run()
				{
					dismissDialog();
					getConnectActivity().onConnected(info);
				}
			}, DELAY_SHOW_TERMINATE);
		}
		else
		{
			getConnectActivity().onConnected(info);
		}
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
		if (V) Log.v(TAG_CONNECT,"err="+err);
		Toast.makeText(getActivity(), err, Toast.LENGTH_LONG).show();
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		mDlg=null;
	}
	protected final ConnectActivity getConnectActivity()
	{
		return (ConnectActivity)getActivity();
	}
	
	@Override
	public Object doTryConnect(ProgressJobs<?,?> progressJobs,
			ConnectDialogFragment fragment,
			String[] uris,
			int flags,
			Bundle param)
	{
		long[] estimations=new long[uris.length];
		Arrays.fill(estimations, ESTIMATION_CONNEXION_3G);
		progressJobs.setEstimations(estimations);
		progressJobs.resetCurrentStep();
		return ConnectDialogFragment.tryAllUris(progressJobs,uris,flags,this);
	}

	@Override
	public Object onTryConnect(String uri,int flags) throws IOException, RemoteException
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
				binder=(AbstractProtoBufRemoteAndroid)driver.factoryBinder(RAApplication.sAppContext,RAApplication.getManager(),uuri);
				if (binder.connect(Type.CONNECT_FOR_BROADCAST, 0,0,ETHERNET_TRY_TIMEOUT))
					return ProgressJobs.OK; // Hack, simulate normal connection
				else
					throw new IOException("Connection impossible");
			}
			finally
			{
				if (binder!=null)
					binder.close();
			}
		}
		else
		{
			Pair<RemoteAndroidInfoImpl,Long> msg=RAApplication.getManager().askMsgCookie(Uri.parse(uri),flags);
			if (msg==null || msg.second==0)
				throw new SecurityException();
			RemoteAndroidInfoImpl remoteInfo=msg.first;
			final long cookie=msg.second;
			if (cookie!=COOKIE_NO && cookie!=COOKIE_EXCEPTION && cookie!=COOKIE_SECURITY)
				RAApplication.sDiscover.addCookie(remoteInfo,cookie);
			return msg.first;
		}
	}
}
