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
package org.droid2droid.ui.connect;

import static org.droid2droid.Constants.DELAY_SHOW_TERMINATE;
import static org.droid2droid.Constants.ETHERNET_TRY_TIMEOUT;
import static org.droid2droid.Constants.TAG_CONNECT;
import static org.droid2droid.internal.Constants.COOKIE_EXCEPTION;
import static org.droid2droid.internal.Constants.COOKIE_NO;
import static org.droid2droid.internal.Constants.COOKIE_SECURITY;
import static org.droid2droid.internal.Constants.TIMEOUT_CONNECT_WIFI;
import static org.droid2droid.internal.Constants.V;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;

import org.droid2droid.R;
import org.droid2droid.RAApplication;
import org.droid2droid.internal.AbstractProtoBufRemoteAndroid;
import org.droid2droid.internal.Driver;
import org.droid2droid.internal.Droid2DroidManagerImpl;
import org.droid2droid.internal.Messages.Type;
import org.droid2droid.internal.Pair;
import org.droid2droid.internal.RemoteAndroidInfoImpl;
import org.droid2droid.ui.AbstractBodyFragment;

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
//		dismissDialog();
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
					if (getConnectActivity()!=null)
						getConnectActivity().onConnected(info);
				}
			}, DELAY_SHOW_TERMINATE);
		}
		else
		{
			if (getConnectActivity()!=null)
				getConnectActivity().onConnected(info);
		}
	}
	@Override
	public void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		mDlg=null;
	}
	@Override
	public void onCancel()
	{
		dismissDialog();
	}

	@Override
	public void onFailed(int err)
	{
		dismissDialog(); // TODO: Wait ok ?
		if (V) Log.v(TAG_CONNECT,"err="+err);
		if (getActivity()!=null)
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
		if (getConnectActivity()==null) return null;
		if (getConnectActivity().isBroadcast())
		{
			AbstractProtoBufRemoteAndroid binder=null;
			try
			{
				final Uri uuri=Uri.parse(uri);
				Driver driver=Droid2DroidManagerImpl.sDrivers.get(uuri.getScheme());
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
