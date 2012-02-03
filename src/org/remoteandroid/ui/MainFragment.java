package org.remoteandroid.ui;

import org.remoteandroid.R;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

public class MainFragment extends Fragment
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
//		SVG svg = SVGParser.getSVGFromResource(getResources(), R.raw.android_body);
//	    Picture picture = svg.getPicture();
//	    mDrawable = svg.createPictureDrawable();		
	}
	
	View mViewer;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer =inflater.inflate(R.layout.expose_connect, container, false);
//		((ImageView)mViewer.findViewById(R.id.img)).setImageDrawable(mDrawable);
		
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
