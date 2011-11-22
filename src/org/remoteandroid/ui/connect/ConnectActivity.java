package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.*;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import org.remoteandroid.Application;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.discovery.bluetooth.BluetoothDiscoverAndroids;
import org.remoteandroid.discovery.ip.IPDiscoverAndroids;
import org.remoteandroid.internal.AbstractRemoteAndroidImpl;
import org.remoteandroid.internal.Messages.Identity;
import org.remoteandroid.internal.Pair;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.StyleFragmentActivity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;

public class ConnectActivity extends StyleFragmentActivity 
implements TechnologiesFragment.Listener
{
	static final int DIALOG_TRY_CONNECTION=1;
	private static TryConnection sTryConnections;
	
	FragmentManager mFragmentManager;
	TechnologiesFragment mTechnologiesFragment;
	AbstractBodyFragment mBodyFragment;

	Technology[] mTechnologies;
	
	private boolean mMerge;
	
	private boolean mAcceptAnonymous;
	
	/*
	 * Use two strategie:
	 * * for use one fragment at a time, use the android.R.id.content
	 * * for use merged fragment, use layout and R.id.technology, R.id.help and R.id.body.
	 * @see org.remoteandroid.ui.connect.StyleFragmentActivity#onCreate(android.os.Bundle)
	 */
	// FIXME: FLAG_ACTIVITY_FORWARD_RESULT
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// TODO: patch de l'icone
		//setTheme(android.R.style.Theme_Light_NoTitleBar);
		// TODO: placer tous les styles dans des wrappers de style pour pouvoir les adapter
		super.onCreate(savedInstanceState);
		
		mAcceptAnonymous=getIntent().getBooleanExtra(RemoteAndroidManager.EXTRA_ACCEPT_ANONYMOUS, false);
		mFragmentManager=getSupportFragmentManager();
		mTechnologies=Technology.initTechnologies(this);
		
		mMerge=getResources().getBoolean(R.bool.connect_merge);
		
		// Hack to simulate merger in landscape, and not merged in portrait
		if (HACK_CONNECT_FORCE_FRAGMENTS)
		{
			if (getResources().getConfiguration().orientation  == Configuration.ORIENTATION_LANDSCAPE)
				mMerge=true;
		}

		FragmentTransaction transaction=mFragmentManager.beginTransaction();
		mTechnologiesFragment=(TechnologiesFragment)mFragmentManager.findFragmentById(R.id.technologies);
		Fragment f=(Fragment)mFragmentManager.findFragmentById(R.id.body);
		if (mTechnologiesFragment!=null) transaction.remove(mTechnologiesFragment);
		if (f!=null) transaction.remove(f);
		if (f instanceof AbstractBodyFragment)
			mBodyFragment=(AbstractBodyFragment)f;
		else
			mTechnologiesFragment=(TechnologiesFragment)f;
		transaction.commit(); // remove all fragments
		mFragmentManager.executePendingTransactions();
		
		transaction=mFragmentManager.beginTransaction();
		if (mMerge)
		{
			setContentView(R.layout.connect_frames);
			if (mBodyFragment==null) mBodyFragment=new EmptyBodyFragment();
			if (mTechnologiesFragment==null) mTechnologiesFragment=new TechnologiesFragment();
			transaction.replace(R.id.technologies, mTechnologiesFragment);
			transaction.replace(R.id.body, mBodyFragment);
			transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
		}
		else
		{
			setContentView(R.layout.connect_noframes);
			if (mTechnologiesFragment==null)
			{
				mTechnologiesFragment=new TechnologiesFragment();
				// Restore state after changed the orientation
				if (mBodyFragment==null || mBodyFragment instanceof EmptyBodyFragment)
				{
					//Log.d("TTT","add in body, techno "+mTechnologiesFragment.mIndex);
					transaction.replace(R.id.body, mTechnologiesFragment);
				}
				else
					transaction.replace(R.id.body, mBodyFragment);
			}
			else
			{
				if (mBodyFragment==null) mBodyFragment=new EmptyBodyFragment();
				if (mBodyFragment instanceof EmptyBodyFragment)
					transaction.replace(R.id.body, mTechnologiesFragment);
				else
					transaction.replace(R.id.body, mBodyFragment);
			}
				
			transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
		}
		transaction.commit();
		
		if (mTechnologiesFragment!=null)
			mTechnologiesFragment.setTechnologies(mTechnologies); // FIXME
		// Reconnect background thread after rotation
		TryConnection tryConnections=sTryConnections;
		if (tryConnections!=null)
		{
			tryConnections.mActivity=new WeakReference<ConnectActivity>(this);
			tryConnections.mProgressDialog=(ConnectDialogFragment)mFragmentManager.findFragmentByTag("dialog");
		}
	}
	
	@Override
	public void onBackPressed()
	{
		if (!mMerge)
		{
			Fragment f=mFragmentManager.findFragmentById(R.id.body);
			if (!(f instanceof TechnologiesFragment))
			{
				FragmentTransaction transaction=mFragmentManager.beginTransaction();
				transaction.replace(R.id.body, mTechnologiesFragment);
				transaction.setTransitionStyle(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
				transaction.commit();
			}
			else
				super.onBackPressed();
		}
		else
			super.onBackPressed();
	}
	
	@Override
	public void onTechnologieSelected(Technology technology)
	{
		InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
		FragmentTransaction transaction;
		transaction=mFragmentManager.beginTransaction();
		mBodyFragment=technology.makeFragment();
		transaction.replace(R.id.body, mBodyFragment);
		if (!mMerge)
			transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
		else
			transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		transaction.commit();
		mFragmentManager.executePendingTransactions();
	}
	
//	@Override
//	protected void onPause()
//	{
//		super.onPause();
//		TryConnection tryHandler=sTryConnections;
//		if (tryHandler!=null)
//		{
//			tryHandler.mProgressDialog=null;
//		}
//	}

	
	public void tryConnect(final FirstStep firstStep,ArrayList<String> uris,boolean acceptAnonymous)
	{
		InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
		if (sTryConnections==null)
		{
//			if (urls==null)
//			{
//				 DialogFragment alert = AlertDialogFragment.newInstance(android.R.drawable.ic_dialog_alert, 
//					 R.string.connect_alert_title, 
//					 R.string.connect_alert_connection_impossible);
//				 alert.show(mFragmentManager, "dialog");
//				 return;
//			}
			final ConnectDialogFragment dlg=ConnectDialogFragment.newInstance();
			dlg.show(mFragmentManager, "dialog");
			sTryConnections=new TryConnection(acceptAnonymous);
			TryConnection tryHandler=sTryConnections;
			if (tryHandler!=null)
			{
				tryHandler.mActivity=new WeakReference<ConnectActivity>(this);
				tryHandler.init(firstStep,uris);
				tryHandler.mProgressDialog = dlg;
				tryHandler.execute();
			}
		}
	}
	
	private void finishWithOk(RemoteAndroidInfoImpl info)
	{
		Intent result=new Intent();
		result.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
		setResult(RESULT_OK,result);
		finish();
	}
	
	public static class AlertDialogFragment extends DialogFragment 
	{
		public static AlertDialogFragment newInstance(int icon,int title,int message)
		{
			AlertDialogFragment frag=new AlertDialogFragment();
			Bundle args = new Bundle();
	        if (icon!=-1) args.putInt("icon", icon);
	        if (title!=-1) args.putInt("title", title);
	        if (message!=-1)
	        	args.putInt("message",message);
	        frag.setArguments(args);
	        return frag;
		}
	    @Override
	    public Dialog onCreateDialog(Bundle savedInstanceState) 
	    {
	    	int icon=getArguments().getInt("icon");
	    	int title=getArguments().getInt("title");
	    	int message=getArguments().getInt("message");
	    	
	        AlertDialog.Builder builder=new AlertDialog.Builder(getActivity());
	        if (icon!=-1) builder.setIcon(icon);
	        if (title!=-1) builder.setTitle(title);
	        if (message!=-1) builder.setMessage(message);
	        builder.setPositiveButton(android.R.string.ok,
		            new DialogInterface.OnClickListener() 
		        	{
		                public void onClick(DialogInterface dialog, int whichButton) 
		                {
		                    //((FragmentAlertDialog)getActivity()).doPositiveClick();
		                }
		            }
		        );
		     return builder.create();
	    }
	};
	public static class ConnectDialogFragment extends DialogFragment
	{

		public static ConnectDialogFragment newInstance()
		{
			return new ConnectDialogFragment();
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			super.onCancel(dialog);
			TryConnection tryHandler=sTryConnections;
			if (tryHandler!=null)
			{
				sTryConnections=null;
				if (tryHandler.mProgressDialog!=null && tryHandler.mProgressDialog.isVisible())
				{
					tryHandler.mProgressDialog.dismiss();
					tryHandler.mProgressDialog=null;
				}
				tryHandler.cancel(false);
				Log.d("TTT","tryHandler=null cause onCancel");
			}
		}
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{

			ProgressDialog progressDialog = new ProgressDialog(getActivity());
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	        progressDialog.setTitle(R.string.connect_try);
			progressDialog.setMessage(getResources().getText(R.string.connect_try));
            progressDialog.setCancelable(true);
            return progressDialog;
		}

	}	
	public interface FirstStep
	{
		int run(TryConnection connection);
	}
	public static class TryConnection extends AsyncTask<Void, Integer, Object>
	{
		private FirstStep mFirstStep;
		public ConnectDialogFragment mProgressDialog=null;
		private ArrayList<String> mUris;
		private WeakReference<ConnectActivity> mActivity=new WeakReference<ConnectActivity>(null);
		private boolean mAcceptAnonymous;
		
		TryConnection(boolean acceptAnonymous)
		{
			mAcceptAnonymous=acceptAnonymous;
		}
		void init(FirstStep firstStep,ArrayList<String> uris)
		{
			mFirstStep=firstStep;
			mUris=uris;
		}
		void setUris(ArrayList<String> uris)
		{
			mUris=uris;
		}

		@Override
		protected Object doInBackground(Void...params)
		{
			int firststep=0;
			if (mFirstStep!=null)
			{
				int msg=mFirstStep.run(this);
				if (msg!=0) // Error
				{
					return msg;
				}
				firststep=1;
			}
			for (int i=0;i<mUris.size();++i)
			{
				if (isCancelled())
					return null;
				String uri=mUris.get(i);
				publishProgress(i+firststep);
				if (D) Log.d(TAG_CONNECT,PREFIX_LOG+"Try "+uri+"...");
				RemoteAndroidInfoImpl info=null;
				
				try
				{
					info=tryConnectForCookie(uri);
				}
				catch (IOException e)
				{
					if (W) Log.w(TAG_CONNECT,PREFIX_LOG+"Connection for cookie impossible ("+e.getMessage()+")");
				}
				catch (SecurityException e)
				{
					// Accept only bounded device.
					info=new Trusted(Application.sAppContext, Application.sHandler).pairWith(mUris);
					if (info==null)
					{
						if (W) Log.w(TAG_CONNECT,PREFIX_LOG+"Pairing impossible");
						return R.string.connect_alert_pairing_impossible;
					}
					if (I) Log.i(TAG_CONNECT,PREFIX_LOG+"Pairing successfull");
				}
				if (info!=null) // Cool
				{
					return info;
				}
				
			}
			return R.string.connect_alert_connection_impossible;
		}
		
		@Override
		protected void onProgressUpdate(Integer... values)
		{
			DialogFragment dlg=mProgressDialog;
			if (dlg!=null)
			{
				ProgressDialog d=(ProgressDialog)dlg.getDialog();
				if (d!=null)
				{
					int firststep=(mFirstStep==null) ? 0 : 1;
					d.setProgress(values[0]*100/(mUris.size()+firststep));
				}
			}
		}
		
		/**
		 * @param result Integer with message or RemoteAndroidInfo
		 */
		@Override
		protected void onPostExecute(final Object result)
		{
			final ConnectActivity activity=mActivity.get();
			if (activity==null)
				return;
			final DialogFragment dlg=mProgressDialog;
			if (result instanceof RemoteAndroidInfoImpl)
			{
				final RemoteAndroidInfoImpl info=(RemoteAndroidInfoImpl)result;
				if (result!=null)
				{
					Application.sHandler.postDelayed(new Runnable()
					{
						@Override
						public void run()
						{
							if (dlg!=null && dlg.getFragmentManager()!=null)
							{
								dlg.dismiss();
							}
							activity.finishWithOk(info);
						}
					}, 500);
				}
				if (dlg!=null && result!=null)
				{
					ProgressDialog d=(ProgressDialog)dlg.getDialog();
					d.setProgress(100);
				}
			}
			else
			{
				if (dlg!=null)
					dlg.dismiss();
				AlertDialogFragment.newInstance(android.R.drawable.ic_dialog_alert, 
					R.string.connect_alert_title, (Integer)result)
					.show(activity.mFragmentManager, "dialog");;
			}
			sTryConnections=null;
		}
		@Override
		protected void onCancelled()
		{
			super.onCancelled();
			final DialogFragment dlg=mProgressDialog;
			if (dlg!=null)
				dlg.dismiss();
			sTryConnections=null;
		}
	}
	
	protected boolean isAcceptAnonymous()
	{
		return mAcceptAnonymous;
	}
	public static RemoteAndroidInfoImpl tryConnectForCookie(String uri) throws SecurityException, IOException
	{
		Pair<RemoteAndroidInfoImpl,Long> msg=Application.sManager.askMsgCookie(Uri.parse(uri));
		if (msg==null || msg.second==0)
			throw new SecurityException();
		return msg.first;
		
	}
	
}
