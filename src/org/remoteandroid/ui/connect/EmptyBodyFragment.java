package org.remoteandroid.ui.connect;

import org.remoteandroid.R;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class EmptyBodyFragment extends AbstractBodyFragment
{
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.connect_empty, container, false);
	}
}
