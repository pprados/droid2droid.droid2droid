package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;

import java.util.ArrayList;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public abstract class AbstractBodyFragment extends Fragment
{
	Technology mTechnology;
	
	protected AbstractBodyFragment()
	{
	}
	public void setTechnology(Technology technology)
	{
		mTechnology=technology;
	}
	
//	@Override
//	public void onAttach(Activity activity)
//	{
//		try
//		{
//			super.onAttach(activity);
//			mListener = (Listener) activity;
//		}
//		catch (ClassCastException e)
//		{
//			throw new ClassCastException(activity.toString()
//					+ " must implement OnTechnologieListener");
//		}
//	}
//
//	@Override
//	public void onSaveInstanceState(Bundle outState)
//	{
//		super.onSaveInstanceState(outState);
//		outState.putInt("curChoice", mCurCheckPosition);
//	}
//
//	@Override
//	public void onDetach()
//	{
//		super.onDetach();
//		mListener = null;
//	}

	
	@Override
	public abstract View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);
	
	
}
