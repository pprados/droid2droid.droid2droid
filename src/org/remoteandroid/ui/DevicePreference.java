package org.remoteandroid.ui;

import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_PREFERENCE;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * List of discovered device
 * 
 * @author Philippe PRADOS
 */
public class DevicePreference extends Preference
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

	private Handler						mHandler			= new Handler();

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

		TextView text = (TextView) ((LinearLayout) view).findViewById(android.R.id.title);
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
					new Trusted(getContext().getApplicationContext(), mHandler)
							.pairWith(mInfo);
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
			if (D)
				Log.d(TAG_PREFERENCE, PREFIX_LOG + "Alreading in pairing process");
			return;
		}
		setEnabled(false);
		new AsyncTask<Void, Void, Void>()
		{
			@Override
			protected Void doInBackground(Void... params)
			{
				try
				{
					new Trusted(getContext().getApplicationContext(), mHandler)
							.unpairWith(mInfo);
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
				setEnabled(false);
			}

		}.execute();
	}
}