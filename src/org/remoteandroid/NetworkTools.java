package org.remoteandroid;

import org.remoteandroid.internal.Compatibility;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.nfc.NfcManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;

public class NetworkTools
{
	public static final int ACTIVE_NOAIRPLANE		=1<<0;
	public static final int ACTIVE_LOCAL_NETWORK	=1<<1;
	public static final int ACTIVE_BLUETOOTH		=1<<2;
	public static final int ACTIVE_PHONE_DATA		=1<<3;
	public static final int ACTIVE_PHONE_SIM		=1<<4;
	public static final int ACTIVE_NFC				=1<<5;

	public static int getActiveNetwork(Context context)
	{
		int activeNetwork=0;
		ConnectivityManager conn=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (conn!=null && conn.getActiveNetworkInfo()!=null)
		{
			int type=conn.getActiveNetworkInfo().getType();
			switch (type)
			{
				case ConnectivityManager.TYPE_MOBILE:
				case ConnectivityManager.TYPE_MOBILE_DUN:
				case ConnectivityManager.TYPE_MOBILE_HIPRI:
				case ConnectivityManager.TYPE_MOBILE_MMS:
				case ConnectivityManager.TYPE_MOBILE_SUPL:
				case ConnectivityManager.TYPE_WIMAX:
					activeNetwork&=~ACTIVE_LOCAL_NETWORK;
					break;
				case ConnectivityManager.TYPE_BLUETOOTH:
				case ConnectivityManager.TYPE_ETHERNET:
				case ConnectivityManager.TYPE_WIFI:
					activeNetwork|=ACTIVE_LOCAL_NETWORK;
					break;
	        }
		}
		
		WifiManager wifi=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);
		TelephonyManager telephone=(TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
		if (telephone.getDataState()==TelephonyManager.DATA_CONNECTED)
			activeNetwork|=ACTIVE_PHONE_DATA; 
		else
			activeNetwork&=~ACTIVE_PHONE_DATA;

		if (telephone.getSimState()==TelephonyManager.SIM_STATE_READY)
			activeNetwork|=ACTIVE_PHONE_SIM;
		else
			activeNetwork&=~ACTIVE_PHONE_SIM;
		
		BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
		if (adapter==null || !adapter.isEnabled())
			activeNetwork&=~ACTIVE_BLUETOOTH;
		else
			activeNetwork|=ACTIVE_BLUETOOTH;
		
		
		
		if (Compatibility.VERSION_SDK_INT>=Compatibility.VERSION_GINGERBREAD_MR1)
		{
			NfcManager nfc=(NfcManager)context.getSystemService(Context.NFC_SERVICE);
			if (nfc==null || nfc.getDefaultAdapter()==null || !nfc.getDefaultAdapter().isEnabled())
				activeNetwork&=~ACTIVE_NFC;
			else
				activeNetwork|=ACTIVE_NFC;
		}
		
		boolean airplane=Settings.System.getInt(context.getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
		if (airplane)
			activeNetwork&=~ACTIVE_NOAIRPLANE;
		else
			activeNetwork|=ACTIVE_NOAIRPLANE;
		return activeNetwork;
	}
	

}
