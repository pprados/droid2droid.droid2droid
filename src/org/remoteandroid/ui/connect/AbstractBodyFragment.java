package org.remoteandroid.ui.connect;

import android.os.Bundle;
import android.support.v4.app.Fragment;
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
	
	@Override
	public abstract View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);
	
	
}
