package org.remoteandroid.ui;

import org.remoteandroid.R;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public final class MainFragment extends Fragment
{
	interface CallBack
	{
		void onExpose();
		void onConnect();
	}
	
	private CallBack mCallBack;
	
	void setCallBack(CallBack callBack)
	{
		mCallBack=callBack;
	}
	Drawable mDrawable;
	View mViewer;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer =inflater.inflate(R.layout.main_expose_connect, container, false);
		
		((Button)mViewer.findViewById(R.id.main_expose)).setOnClickListener(new Button.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mCallBack.onExpose();
			}
		});
		((Button)mViewer.findViewById(R.id.main_connect)).setOnClickListener(new Button.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mCallBack.onConnect();
			}
		});
		return mViewer;
	}	
}
