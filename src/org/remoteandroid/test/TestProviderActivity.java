package org.remoteandroid.test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.service.RemoteAndroidProvider;


import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.ImageButton;

public class TestProviderActivity extends FragmentActivity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		ContentResolver cr=getContentResolver();
		String s=cr.getType(RemoteAndroidManager.QRCODE_URI);
		try
		{
			AssetFileDescriptor afd=cr.openTypedAssetFileDescriptor(RemoteAndroidManager.QRCODE_URI, s, null);
			InputStream in=afd.createInputStream();
			Bitmap bitmap=BitmapFactory.decodeStream(in);
			in.close();
			Bitmap scaBitmap=Bitmap.createScaledBitmap(bitmap, 300, 300, false);
			bitmap.recycle();
			ImageButton ib=new ImageButton(this);
			ib.setImageBitmap(scaBitmap);
			setContentView(ib);
		}
		catch (FileNotFoundException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Log.d("TOTO","s="+s);
	}
}
