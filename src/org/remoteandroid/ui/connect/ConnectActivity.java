package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.CONNECT_FORCE_FRAGMENTS;

import org.remoteandroid.R;
import org.remoteandroid.ui.StyleFragmentActivity;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.view.Window;

public class ConnectActivity extends StyleFragmentActivity 
implements TechnologiesFragment.Listener
{
	private static final String STACKNAME="stack";
	enum State { MERGE,TECHNOLOGIES,BODY};
	private State mState;
	
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
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		mFragmentManager=getSupportFragmentManager();
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
				Fragment currentFragment=mFragmentManager.findFragmentById(android.R.id.content);
				mBodyFragment=mTechnology.makeFragment();
				mState=State.BODY;
				transaction=mFragmentManager.beginTransaction();
				transaction.replace(android.R.id.content, mBodyFragment);
				transaction.addToBackStack(STACKNAME);
				transaction.commit();
				break;
		}
		mFragmentManager.executePendingTransactions();
	}
}
