package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.internal.Constants;
import org.remoteandroid.internal.Pair;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.connect.old.ConnectActivity.FirstStep;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.SpannedString;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class TryConnectFragment extends DialogFragment
{
	public static final long ESTIMATION_CONNEXION_3G=TIMEOUT_CONNECT_WIFI;
	
	interface OnConnected
	{
		Object executePrejobs(ProgressJobs<?,?> progressJobs,TryConnectFragment fragment);
		public RemoteAndroidInfoImpl onConnect(String uri) throws SecurityException, IOException;
		void onConnected(RemoteAndroidInfoImpl uri);
		void onCancel();
		void onFailed(int err);
	}
	
	private static final String KEY_URIS="uris";

	private View mViewer;
	private TextView mStep;
	private ProgressBar mProgressBar;
	private Button mCancel;
	
	private OnConnected mOnEvent;
	private String[] mUris;

	private TryConnection mTryConnections;
	
	public static final TryConnectFragment newTryConnectFragment(boolean acceptAnonymous,String[] uris)
	{
		TryConnectFragment fragment=new TryConnectFragment();
		Bundle bundle=new Bundle();
		bundle.putStringArray(KEY_URIS, uris);
		fragment.setArguments(bundle);
		return fragment;
	}
	
	private TryConnectFragment()
	{
	}
	public void setOnConnected(OnConnected callback)
	{
		mOnEvent=callback;
	}
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mUris=getArguments().getStringArray(KEY_URIS);		
	}

	public class TryConnection extends ProgressJobs<Void, Object>
	{
		@Override
		protected Object doInBackground(Void...params)
		{
			
			Object rc=mOnEvent.executePrejobs(this,TryConnectFragment.this);
			if (rc!=null) return rc;
			long[] estimations=new long[mUris.length];
			Arrays.fill(estimations, ESTIMATION_CONNEXION_3G);
			setEstimations(estimations);
			resetCurrentStep();
			return tryAllUris(this,mUris,mOnEvent);
		}
		@Override
		protected void onProgressUpdate(Integer... values)
		{
			int msg=values[0];
			int step=values[1];
			publishInDialog(msg, step);
		}
		
		/**
		 * @param result Integer with message or RemoteAndroidInfo
		 */
		@Override
		protected void onPostExecute(final Object result)
		{
			stop();
			if (result==null) // cancel
			{
				if (mOnEvent!=null)
					mOnEvent.onCancel();
			}
			if (result instanceof RemoteAndroidInfoImpl)
			{
				RemoteAndroidInfoImpl info=(RemoteAndroidInfoImpl)result;
				if (mOnEvent!=null)
					mOnEvent.onConnected(info);
			}
			else if (result instanceof Integer)
			{
				if (mOnEvent!=null)
					mOnEvent.onFailed((Integer)result);
			}
		}
		
	}
	public static Object tryAllUris(ProgressJobs<?,?> progressJobs,String[] uris,OnConnected onEvent)
	{
		//progressJobs.publishProgress(R.string.connect_try);
		for (int i=0;i<uris.length;++i)
		{
			progressJobs.incCurrentStep();
			final String uri=uris[i];
			try
			{
				if (progressJobs.isCancelled())
					return null;
				if (D) Log.d(TAG_CONNECT,PREFIX_LOG+"Try "+uri+"...");
				if (onEvent!=null)
				{
					return onEvent.onConnect(uri);
				}
				else
					return ProgressJobs.CANCEL;
			}
			catch (final IOException e)
			{
				if (W) Log.w(TAG_CONNECT,PREFIX_LOG+"Connection for cookie impossible ("+e.getMessage()+")");
				if (D)
				{
					Application.sHandler.post(new Runnable()
					{
						@Override
						public void run()
						{
							Toast.makeText(Application.sAppContext, e.getMessage(), Toast.LENGTH_LONG).show();
						}
					});
				}
			}
			catch (SecurityException e)
			{
				// Accept only bounded device.
				RemoteAndroidInfoImpl info=new Trusted(Application.sAppContext, Application.sHandler).pairWith(uris);
				if (info==null)
				{
					if (W) Log.w(TAG_CONNECT,PREFIX_LOG+"Pairing impossible");
					return R.string.connect_alert_pairing_impossible;
				}
				if (I) Log.i(TAG_CONNECT,PREFIX_LOG+"Pairing successfull");
			}
		}
		return R.string.connect_alert_connection_impossible;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		getDialog().setTitle(R.string.connect_try);
		mViewer = (View) inflater.inflate(R.layout.try_connect, container, false);
		mStep=(TextView)mViewer.findViewById(R.id.step);
		mProgressBar=(ProgressBar)mViewer.findViewById(R.id.progress);
		mProgressBar.setMax(1000);
		mCancel=(Button)mViewer.findViewById(R.id.cancel);
		mCancel.setOnClickListener(new Button.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				// TODO Auto-generated method stub
				
			}
		});
		return mViewer;
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		boolean acceptAnonymous=true;
		mTryConnections=new TryConnection();
		mTryConnections.execute();
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		mTryConnections.cancel(true); // FIXME: true ?
	}
	
	public void publishInDialog(int id,int progress)
	{
		if (id!=0)
			mStep.setText(id);
		mProgressBar.setProgress(progress);
	}
}
