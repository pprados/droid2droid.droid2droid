package org.remoteandroid.ui;

import org.remoteandroid.R;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
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
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
	}
	
	View mViewer;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer =inflater.inflate(R.layout.main_expose_connect, container, false);
		
		final Button btnExpose=((Button)mViewer.findViewById(R.id.main_expose));
		final PackageManager pm=getActivity().getPackageManager();
		boolean fakeTouch=false;
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.HONEYCOMB)
			fakeTouch=pm.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH);
		if (
				!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) &&
				!fakeTouch)
		{
			btnExpose.requestFocus();
		}
		btnExpose.setOnClickListener(new Button.OnClickListener()
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
