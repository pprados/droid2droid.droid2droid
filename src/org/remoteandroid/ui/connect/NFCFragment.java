package org.remoteandroid.ui.connect;

import org.remoteandroid.R;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class NFCFragment extends AbstractBodyFragment
{
	View mViewer;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer =inflater.inflate(R.layout.connect_nfc, container, false);
		return mViewer;
	}
}
