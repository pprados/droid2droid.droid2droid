package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.CONNECT_FORCE_FRAGMENTS;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;

import org.remoteandroid.Application;
import org.remoteandroid.AsyncTaskWithException;
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
import android.view.Window;

public class ConnectActivity extends StyleFragmentActivity 
implements TechnologiesFragment.Listener
{
	private static final String STACKNAME="stack";
	static final int DIALOG_TRY_CONNECTION=1;
	enum State { MERGE,TECHNOLOGIES,BODY};
	private State mState;
	private static TryConnection sTryHandler;
	
	TechnologiesFragment mTechnologiesFragment;
	AbstractBodyFragment mBodyFragment;
	FragmentManager mFragmentManager;

	Technology[] mTechnologies;
	Technology mTechnology=Technology.sDefault;
	
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
		mFragmentManager.findFragmentById(android.R.id.content); // FIXME
		mTechnologies=Technology.initTechnologies(this);
		
		boolean merge=getResources().getBoolean(R.bool.connect_merge);
		mState=State.MERGE;
		
		if (savedInstanceState != null) 
		{
            mState= State.values()[savedInstanceState.getInt("state", State.TECHNOLOGIES.ordinal())];
            Technology.Type type=Technology.Type.values()[savedInstanceState.getInt("technology", 0)];
            for (int i=0;i<mTechnologies.length;++i)
            {
            	if (mTechnologies[i].mId==type)
            	{
            		mTechnology=mTechnologies[i];
            		break;
            	}
            }
        }
		// FIXME: Hook for simulate large screen
		if (CONNECT_FORCE_FRAGMENTS)
		{
			if (getResources().getConfiguration().orientation  == Configuration.ORIENTATION_LANDSCAPE)
				merge=true;
		}
		
		if (merge)
		{
			// If change orientation in second or more page
			//http://stackoverflow.com/questions/7431516/how-to-change-fragments-class-dynamically
			
			FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

			setContentView(R.layout.connect_frames);
			Technology technology=(mTechnology==null ) ? Technology.sDefault : mTechnology;
			mBodyFragment=technology.makeFragment();
			fragmentTransaction.replace(R.id.body, mBodyFragment);
			mFragmentManager.popBackStack(STACKNAME,FragmentManager.POP_BACK_STACK_INCLUSIVE);
			
			
			mTechnologiesFragment=(TechnologiesFragment)mFragmentManager.findFragmentById(R.id.technologies);
			mTechnologiesFragment.setTechnologies(mTechnologies);
			mTechnologiesFragment.enabledPersistentSelection();
			
			mBodyFragment=(AbstractBodyFragment)mFragmentManager.findFragmentById(R.id.body);
			if (mBodyFragment==null)
			{
				mBodyFragment=mTechnology.makeFragment();
				fragmentTransaction.replace(R.id.body, mBodyFragment);
			}
			Fragment f=mFragmentManager.findFragmentById(android.R.id.content);
			if (f!=null)
				fragmentTransaction.remove(f);
			fragmentTransaction.commit();
			mState=State.MERGE;
		}
		else
		{
			if ((mState==State.MERGE) || (mState==State.TECHNOLOGIES)) 
			{
				// Restore state after changed the orientation
				mState=State.TECHNOLOGIES;
				mFragmentManager.popBackStack(STACKNAME,FragmentManager.POP_BACK_STACK_INCLUSIVE);
				mFragmentManager.beginTransaction()
					.add(android.R.id.content, mTechnologiesFragment=new TechnologiesFragment()).commit();
				mTechnologiesFragment.setTechnologies(mTechnologies);
				//mBodyFragment.setTechnology(mTechnology);
			}
		}
		// Reconnect background thread after rotation
		TryConnection tryHandler=sTryHandler;
		if (tryHandler!=null)
		{
			tryHandler.mProgressDialog=(ConnectDialogFragment)getSupportFragmentManager().findFragmentByTag("dialog");
		}

	}
	
	@Override
    public void onSaveInstanceState(Bundle outState) 
	{
        super.onSaveInstanceState(outState);
        outState.putInt("state", mState.ordinal());
        outState.putInt("technology", mTechnology.mId.ordinal());
    }
	
	@Override
	public void onTechnologieSelected(Technology technology)
	{
		FragmentTransaction transaction;
		if (mTechnology==technology)
			return;
		mTechnology=technology;
		switch (mState)
		{
			case MERGE:
				transaction=mFragmentManager.beginTransaction();
				mBodyFragment=mTechnology.makeFragment();
				transaction.replace(R.id.body, mBodyFragment);
				transaction.commit();
				break;
				
			case TECHNOLOGIES:
				mBodyFragment=mTechnology.makeFragment();
				mState=State.BODY;
				Fragment f=mFragmentManager.findFragmentById(android.R.id.content);
				Log.d("TTT","f="+f);
				transaction=mFragmentManager.beginTransaction();
				transaction.replace(android.R.id.content, mBodyFragment);
				transaction.addToBackStack(STACKNAME);
				transaction.commit();
				break;
		}
		mFragmentManager.executePendingTransactions();
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
		TryConnection tryHandler=sTryHandler;
		if (tryHandler!=null)
		{
			tryHandler.mProgressDialog=null;
		}
	}

	@Override
	public void onBackPressed()
	{
		super.onBackPressed();
		switch (mState)
		{
//			case BODY:
//				mState=State.TECHNOLOGIES;
//				break;
			case BODY:
				mState=State.TECHNOLOGIES;
				break;
		}
	}
	public void tryConnect(final Runnable firstStep)
	{
		if (sTryHandler==null)
		{
			Log.d("TTT","create try handler");
			final ConnectDialogFragment dlg=ConnectDialogFragment.newInstance();
			dlg.show(getSupportFragmentManager(), "dialog");
			sTryHandler=new TryConnection();
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
			
			TryConnection tryHandler=sTryHandler;
			if (tryHandler!=null)
			{
				tryHandler.init(firstStep,ConnectionCandidats.make(ConnectActivity.this,candidates));
				tryHandler.mProgressDialog = dlg;
				tryHandler.execute();
			}
		}
		else
		{
			Log.d("TTT","allready created");
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
			TryConnection tryHandler=sTryHandler;
			if (tryHandler!=null)
			{
				sTryHandler=null;
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
	public static class TryConnection extends AsyncTask<Void, Integer, Boolean>
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
			sTryHandler=null;
		}
		@Override
		protected void onCancelled()
		{
			super.onCancelled();
			Log.d("TTT","onCancelled");
		}
	}
	
}
