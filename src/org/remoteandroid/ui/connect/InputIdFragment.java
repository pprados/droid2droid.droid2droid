package org.remoteandroid.ui.connect;

import org.remoteandroid.R;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class InputIdFragment extends AbstractBodyFragment
{
	View mViewer;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer =inflater.inflate(R.layout.connect_inputid, container, false);
		return mViewer;
	}
}
