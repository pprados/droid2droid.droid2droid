package org.remoteandroid.ui.connect;

import java.io.IOException;
import java.util.List;

import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.ui.AbstractNetworkEventFragment;
import org.remoteandroid.ui.connect.old.AbstractBodyFragment;
import org.remoteandroid.ui.connect.old.ConnectActivity.FirstStep;

import android.support.v4.app.Fragment;

public class AbstractConnectFragment extends AbstractBodyFragment
{
	protected void setProgressBarIndeterminateVisibility(boolean value)
	{
		// TODO: Boolean
		ConnectActivity activity=(ConnectActivity)getActivity();
		if (activity!=null)
			activity.setProgressBarIndeterminateVisibility(value ? Boolean.TRUE : Boolean.FALSE);
	}
	
	protected  static RemoteAndroidInfoImpl tryConnectForCookie(String uri) throws SecurityException, IOException
	{
		return null;
	}
	public void tryConnect(final FirstStep firstStep,List<String> uris,boolean acceptAnonymous)
	{
		
	}
	public int getActiveNetwork()
	{
		return 0; // TODO
	}
}
