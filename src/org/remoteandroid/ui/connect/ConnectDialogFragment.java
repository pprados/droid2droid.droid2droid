package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;

import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

// FIXME: la boite de dialogue n'est pas conforme ICS
public final class ConnectDialogFragment extends DialogFragment
{
	interface OnConnected
	{
		Object doTryConnect(ProgressJobs<?,?> progressJobs,ConnectDialogFragment fragment,String[] uris,int flags,Bundle param);
		public Object onTryConnect(String uri,int flags) throws IOException, RemoteException;
		void onConnected(RemoteAndroidInfoImpl uri);
		void onCancel();
		void onFailed(int err);
	}
	
	private static final String KEY_URIS="uris";
	private static final String KEY_BUNDLE="bundle";
	private static final String KEY_FLAGS="flags";

	private View mViewer;
	private TextView mStep;
	private ProgressBar mProgressBar;
	private TextView mPercentProgress;
	private Button mCancel;
	
	private OnConnected mOnEvent;
	private String[] mUris;
	private Bundle mParams;
	private int mFlags;

	private TryConnection mTryConnections;
	
	public static final ConnectDialogFragment newTryConnectFragment(int flags,String[] uris,Bundle params)
	{
		ConnectDialogFragment fragment=new ConnectDialogFragment();
		Bundle bundle=new Bundle();
		bundle.putStringArray(KEY_URIS, uris);
		bundle.putBundle(KEY_BUNDLE, params);
		bundle.putInt(KEY_FLAGS, flags);
		fragment.setArguments(bundle);
		return fragment;
	}
	
	private ConnectDialogFragment()
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
		mParams=getArguments().getBundle(KEY_BUNDLE);
		mFlags=getArguments().getInt(KEY_FLAGS);
	}

	public class TryConnection extends ProgressJobs<Void, Object>
	{
		@Override
		protected Object doInBackground(Void...params)
		{
			return mOnEvent.doTryConnect(this,ConnectDialogFragment.this,mUris,mFlags,mParams);
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
			if (result==ProgressJobs.CANCEL) // cancel
			{
				if (mOnEvent!=null)
					mOnEvent.onCancel();
			}
			else if (result==ProgressJobs.OK)
			{
				if (mOnEvent!=null)
					mOnEvent.onConnected(null);
				return;
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
	public static Object tryAllUris(ProgressJobs<?,?> progressJobs,String[] uris,int flags,OnConnected onEvent)
	{
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
					return onEvent.onTryConnect(uri,flags);
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
			catch (RemoteException e)
			{
				if (W) Log.w(TAG_CONNECT,PREFIX_LOG+"Send broadcast impossible");
			}
			catch (SecurityException e)
			{
				if (W) Log.w(TAG_CONNECT,PREFIX_LOG+"Remote device refuse anonymous.");
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
		mPercentProgress=(TextView)mViewer.findViewById(R.id.progress_percent);
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
	public void onStart()
	{
		super.onStart();
		mTryConnections=new TryConnection();
		mTryConnections.execute();
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
	}
	
	public void publishInDialog(int id,int progress)
	{
		if (id!=0)
			mStep.setText(id);
		mProgressBar.setProgress(progress);
		mPercentProgress.setText((progress/10)+"%");
		
	}
}
