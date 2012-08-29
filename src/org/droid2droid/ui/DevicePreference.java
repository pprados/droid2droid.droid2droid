/******************************************************************************
 *
 * droid2droid - Distributed Android Framework
 * ==========================================
 *
 * Copyright (C) 2012 by Atos (http://www.http://atos.net)
 * http://www.droid2droid.org
 *
 ******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
******************************************************************************/
package org.droid2droid.ui;

import static org.droid2droid.Constants.ETHERNET_TRY_TIMEOUT;
import static org.droid2droid.Droid2DroidManager.FLAG_FORCE_PAIRING;
import static org.droid2droid.Droid2DroidManager.FLAG_REMOVE_PAIRING;
import static org.droid2droid.internal.Constants.COOKIE_EXCEPTION;
import static org.droid2droid.internal.Constants.COOKIE_NO;
import static org.droid2droid.internal.Constants.COOKIE_SECURITY;
import static org.droid2droid.internal.Constants.D;
import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.ETHERNET;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.SCHEME_TCP;
import static org.droid2droid.internal.Constants.TAG_CLIENT_BIND;
import static org.droid2droid.internal.Constants.TAG_PREFERENCE;
import static org.droid2droid.internal.Constants.W;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;

import org.droid2droid.R;
import org.droid2droid.RAApplication;
import org.droid2droid.internal.AbstractRemoteAndroidImpl;
import org.droid2droid.internal.Driver;
import org.droid2droid.internal.Droid2DroidManagerImpl;
import org.droid2droid.internal.Messages.Type;
import org.droid2droid.internal.RemoteAndroidInfoImpl;
import org.droid2droid.pairing.Trusted;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.preference.Preference;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * List of discovered device
 * 
 * @author Philippe PRADOS
 */
public final class DevicePreference extends Preference
{
	private static final String KEY_BUSY="ra.busy";
	private static final String KEY_INFO="ra.info";

	/**
	 * Cached local copy of whether the device is busy. This is only updated from
	 * {@link #onDeviceAttributesChanged(CachedBluetoothDevice)}.
	 */
	private volatile boolean			mIsBusy;
	/* package */RemoteAndroidInfoImpl	mInfo;


	
	private static int				sDimAlpha			= Integer.MIN_VALUE;

	private final CharSequence			mPairing;

	public DevicePreference(Context context)
	{
		super(context);
		mPairing = context.getResources().getText(R.string.device_pairing_summary);
		// Copy from BluetoothDevicePreference
		if (sDimAlpha == Integer.MIN_VALUE)
		{
			TypedValue outValue = new TypedValue();
			context.getTheme().resolveAttribute(android.R.attr.disabledAlpha, outValue, true);
			sDimAlpha = (int) (outValue.getFloat() * 255);
		}

		if (VERSION.SDK_INT>=VERSION_CODES.HONEYCOMB)
			setLayoutResource(R.layout.discover_holo);
		else
			setLayoutResource(R.layout.discover);
	}
	public DevicePreference(Context context, RemoteAndroidInfoImpl info)
	{
		this(context);
		mInfo = info;
		onDeviceAttributesChanges();
	}

	@Override
	protected void onPrepareForRemoval()
	{
	}

	@Override
	public boolean isEnabled()
	{
		return super.isEnabled() && !mIsBusy;
	}
	@Override
	public void saveHierarchyState(Bundle container)
	{
		super.saveHierarchyState(container);
		
		container.putBoolean(KEY_BUSY, mIsBusy);
		container.putParcelable(KEY_INFO, mInfo);
	}
	@Override
	public void restoreHierarchyState(Bundle container)
	{
		super.restoreHierarchyState(container);
		mIsBusy=container.getBoolean(KEY_BUSY);
		mInfo=container.getParcelable(KEY_INFO);
	}
	@Override
	protected void onBindView(View view)
	{
		super.onBindView(view);

		TextView text = (TextView) ((ViewGroup) view).findViewById(android.R.id.title);
		text.setTextColor((mInfo.isDiscover() && !mIsBusy) ? 
				getContext().getResources().getColor(android.R.color.primary_text_dark_nodisable) 
				: getContext().getResources().getColor(android.R.color.tertiary_text_dark));
		// TODO: Ajout d'une icone de classe de device ?
		// ImageView btClass = (ImageView) view.findViewById(R.id.btClass);
		// btClass.setImageResource(mCachedDevice.getBtClassDrawable());
		// btClass.setAlpha(isEnabled() ? 255 : sDimAlpha);

	}

	public final void onDeviceAttributesChanges()
	{
		setTitle(mInfo.getName());

		StringBuilder buf = RAApplication.getTechnologies(mInfo,true);
		if (buf.length()!=0)
		{
			buf.insert(0, " ( ");
			buf.insert(buf.length()," )");
		}
		setSummary(buf);

		// TODO: Icon différente suivant connectivité ?

		// Data has changed
		notifyChanged();

		// This could affect ordering, so notify that also
		notifyHierarchyChanged();
	}

	@Override
	public int compareTo(Preference another)
	{
		if (!(another instanceof DevicePreference))
		{
			// Put other preference types above us
			return 1;
		}

		DevicePreference anotherPref = (DevicePreference) another;
		return mInfo.getName().compareTo(anotherPref.mInfo.getName());
	}

	public void onCreateContextMenu(MenuInflater inflater, ContextMenu menu)
	{
		inflater.inflate(mInfo.isBonded ? R.menu.context_device_bonded
				: R.menu.context_device_unbonded, menu);
		menu.setHeaderTitle(mInfo.getName());
	}

	public boolean onContextItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.context_pair:
				pair();
				break;
			case R.id.context_unpair:
				unpair();
				break;

		}
		return false;
	}

	public void pair()
	{
		if (mIsBusy)
		{
			if (D)
				Log.d(TAG_PREFERENCE, PREFIX_LOG + "Alreading in pairing process");
			return;
		}
		mIsBusy = true;
		setEnabled(false);
		setSummary(mPairing);
		new AsyncTask<Void, Void, Void>()
		{
			@Override
			protected Void doInBackground(Void... params)
			{
				try
				{
					for (int i=0;i<mInfo.uris.size();++i)
					{
						String uri=mInfo.uris.get(i);
	    				long cookie=
	    						RAApplication.sDiscover.getCookie(FLAG_FORCE_PAIRING,uri,Type.CONNECT_FOR_COOKIE);
	    				if (cookie!=COOKIE_EXCEPTION && cookie!=COOKIE_NO && cookie!=COOKIE_SECURITY)
	    					break;
					}
				}
				finally
				{
					mIsBusy = false;
				}
				return null;
			}
			@Override
			protected void onPostExecute(Void result) 
			{
				setEnabled(true);
				onDeviceAttributesChanges();
			}

		}.execute();
	}

	private void unpair()
	{
		if (mIsBusy)
		{
			if (D) Log.d(TAG_PREFERENCE, PREFIX_LOG + "Alreading in pairing process");
			return;
		}
		setEnabled(false);
		mInfo.isBonded=false;
		new AsyncTask<Void, Void, Void>()
		{
			@Override
			protected Void doInBackground(Void... params)
			{
				Trusted.unregisterDevice(RAApplication.sAppContext,mInfo);
				for (int i=0;i<mInfo.uris.size();++i)
				{
					final Uri uri=Uri.parse(mInfo.uris.get(i));
					try
					{
						
						AbstractRemoteAndroidImpl binder=null;
						try
						{
							String scheme=uri.getScheme();
							if (!ETHERNET && scheme.equals(SCHEME_TCP))
								return null;
							Driver driver=Droid2DroidManagerImpl.sDrivers.get(uri.getScheme());
							if (driver==null)
								throw new MalformedURLException("Unknown "+uri);
							binder=driver.factoryBinder(RAApplication.sAppContext,RAApplication.getManager(),uri);
							binder.connect(Type.CONNECT_FOR_COOKIE, FLAG_REMOVE_PAIRING,-1l,ETHERNET_TRY_TIMEOUT);
							return null;
						}
						catch (SecurityException e)
						{
							if (W && !D) Log.w(TAG_CLIENT_BIND,"Remote device refuse anonymous connection.");
							if (D) Log.d(TAG_CLIENT_BIND,"Remote device refuse anonymous connection.",e);
							return null;
						}
						catch (SocketException e)
						{
							if (E && !D) Log.e(TAG_CLIENT_BIND,"Connection impossible for ask cookie. Imcompatible with ipv6? ("+e.getMessage()+")");
							if (D) Log.d(TAG_CLIENT_BIND,"Connection impossible for ask cookie. Imcompatible with ipv6?",e);
						}
						catch (IOException e)
						{
							if (E && !D) Log.e(TAG_CLIENT_BIND,"Connection impossible for ask cookie ("+e.getMessage()+")");
							if (D) Log.d(TAG_CLIENT_BIND,"Connection impossible for ask cookie.",e);
						}
						catch (Exception e)
						{
							if (E && !D) Log.e(TAG_CLIENT_BIND,"Connection impossible for ask cookie ("+e.getMessage()+")");
							if (D) Log.d(TAG_CLIENT_BIND,"Connection impossible for ask cookie.",e);
						}
						finally
						{
							if (binder!=null)
								binder.close();
						}
					}
					finally
					{
						mIsBusy = false;
					}
				}
				return null;
			}
			@Override
			protected void onPostExecute(Void result) 
			{
				mIsBusy = false;
				setEnabled(true);
				onDeviceAttributesChanges();
			}

		}.execute();
	}
}
