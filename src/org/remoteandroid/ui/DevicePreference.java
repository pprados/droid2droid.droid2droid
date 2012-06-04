package org.remoteandroid.ui;

import static org.remoteandroid.Constants.ETHERNET_TRY_TIMEOUT;
import static org.remoteandroid.internal.Constants.COOKIE_EXCEPTION;
import static org.remoteandroid.internal.Constants.COOKIE_NO;
import static org.remoteandroid.internal.Constants.COOKIE_SECURITY;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.ETHERNET;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.SCHEME_TCP;
import static org.remoteandroid.internal.Constants.TAG_CLIENT_BIND;
import static org.remoteandroid.internal.Constants.TAG_PREFERENCE;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.internal.AbstractRemoteAndroidImpl;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.Driver;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.RemoteAndroidManagerImpl;
import org.remoteandroid.pairing.Trusted;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
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


	
	private static int					sDimAlpha			= Integer.MIN_VALUE;

	private CharSequence				mPairing;

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

		if (Compatibility.VERSION_SDK_INT>=Compatibility.VERSION_HONEYCOMB)
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

		StringBuilder buf = Application.getTechnologies(mInfo,true);
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
	    						Application.sDiscover.getCookie(RemoteAndroidManager.FLAG_PROPOSE_PAIRING,uri,Type.CONNECT_FOR_COOKIE);
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
				Trusted.unregisterDevice(Application.sAppContext,mInfo);
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
							Driver driver=RemoteAndroidManagerImpl.sDrivers.get(uri.getScheme());
							if (driver==null)
								throw new MalformedURLException("Unknown "+uri);
							binder=driver.factoryBinder(Application.sAppContext,Application.getManager(),uri);
							binder.connect(Type.CONNECT_FOR_COOKIE, RemoteAndroidManager.FLAG_PROPOSE_PAIRING,-1l,ETHERNET_TRY_TIMEOUT);
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
