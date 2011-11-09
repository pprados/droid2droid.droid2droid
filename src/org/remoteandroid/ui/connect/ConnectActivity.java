package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.CONNECT_FORCE_FRAGMENTS;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.ui.StyleFragmentActivity;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
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
		
		mFragmentManager=getSupportFragmentManager();
		mTechnologies=Technology.initTechnologies(this);
		
		mMerge=getResources().getBoolean(R.bool.connect_merge);
		
		// Hack to simulate merger in landscape, and not merged in portrait
		if (CONNECT_FORCE_FRAGMENTS)
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
			}
			else
			{
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
	
	@Override
	protected void onPause()
	{
		super.onPause();
		TryConnection tryHandler=sTryConnections;
		if (tryHandler!=null)
		{
			tryHandler.mProgressDialog=null;
		}
	}

	
	public void tryConnect(final Runnable firstStep)
	{
		if (sTryConnections==null)
		{
			final ConnectDialogFragment dlg=ConnectDialogFragment.newInstance();
			dlg.show(mFragmentManager, "dialog");
			sTryConnections=new TryConnection();
			ConnectMessages.Candidates candidates=null;
			try
			{
				candidates=ConnectionCandidats.getConnectMessage(ConnectActivity.this);
			}
			catch (UnknownHostException e1)
			{
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			catch (SocketException e1)
			{
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			TryConnection tryHandler=sTryConnections;
			if (tryHandler!=null)
			{
				tryHandler.init(firstStep,ConnectionCandidats.make(ConnectActivity.this,candidates));
				tryHandler.mProgressDialog = dlg;
				tryHandler.execute();
			}
		}
	}

	public static class ConnectDialogFragment extends DialogFragment
	{

		public static ConnectDialogFragment newInstance()
		{
			ConnectDialogFragment frag = new ConnectDialogFragment();
//			Bundle args = new Bundle();
//			frag.setArguments(args);
			return frag;
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
	private static class TryConnection extends AsyncTask<Void, Integer, Boolean>
	{
		private Runnable mFirstStep;
		public ConnectDialogFragment mProgressDialog=null;
		private ArrayList<CharSequence> mUrls;

		TryConnection()
		{
			
		}
		void init(Runnable runnable,ArrayList<CharSequence> urls)
		{
			mFirstStep=runnable;
			mUrls=urls;
		}
		@Override
		protected Boolean doInBackground(Void...params)
		{
			int firststep=0;
			if (mFirstStep!=null)
			{
				mFirstStep.run();
				firststep=1;
			}
			for (int i=0;i<mUrls.size();++i)
			{
				if (isCancelled())
					return false;
				CharSequence url=mUrls.get(i);
				publishProgress(i+firststep);
				// TODO: try to connect
				try  { Thread.sleep(2000); } catch (Exception e) {}
				Log.d(TAG_CONNECT,PREFIX_LOG+"try "+url+"...");
			}
			return false;
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
					d.setProgress(values[0]*100/(mUrls.size()+firststep));
				}
			}
		}
		
		@Override
		protected void onPostExecute(Boolean result)
		{
			final DialogFragment dlg=mProgressDialog;
			if (dlg!=null)
			{
				ProgressDialog d=(ProgressDialog)dlg.getDialog();
				d.setProgress(100);
			}
			Application.sHandler.postDelayed(new Runnable()
			{
				@Override
				public void run()
				{
					if (dlg!=null && dlg.getFragmentManager()!=null)
					{
						dlg.dismiss();
					}
				}
			}, 1000);
			Log.d("TTT","tryhandler=null cause onPostExecute");
			sTryConnections=null;
		}
		@Override
		protected void onCancelled()
		{
			super.onCancelled();
			Log.d("TTT","onCancelled");
		}
	}
	
}
