package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.SCHEME_BT;
import static org.remoteandroid.internal.Constants.SCHEME_BTS;
import static org.remoteandroid.internal.Constants.SCHEME_TCP4;
import static org.remoteandroid.internal.Constants.SCHEME_TCP6;
import static org.remoteandroid.internal.Constants.V;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;

import org.remoteandroid.internal.Compatibility;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import com.google.protobuf.ByteString;

public class ConnectionCandidats
{
	// Note: With device <Honey_comb, only one data network is on. Wifi OR mobile.
	// In honeycomb, it's possible to have Widi AND Mobile.
	public static ConnectMessages.Candidates getConnectMessage(Context context) throws UnknownHostException, SocketException
	{
		final WifiManager wifi=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);

		ArrayList<InetAddress> all=new ArrayList<InetAddress>(4);
		ArrayList<ByteString> ipv6=new ArrayList<ByteString>(2);
		ArrayList<Integer> ipv4=new ArrayList<Integer>(2);

		for (Enumeration<NetworkInterface> networks=NetworkInterface.getNetworkInterfaces();networks.hasMoreElements();)
		{
			NetworkInterface network=networks.nextElement();
			for (Enumeration<InetAddress> addrs=network.getInetAddresses();addrs.hasMoreElements();)
			{
				InetAddress add=(InetAddress)addrs.nextElement();
				if (D) Log.d(TAG_CONNECT,PREFIX_LOG+"Analyse "+network.getName()+" "+add);
				if (network.getName().startsWith("sit")) // vpn ?
					continue;
				if (network.getName().startsWith("dummy")) // ipv6 in ipv4
					continue;
				if (add.isLoopbackAddress())
					continue;
				if (!all.contains(add))
				{
					if (add instanceof Inet4Address)
					{
						// Exclude RFC 3330. Auto configure ip
						if (add.getAddress()[0]==(byte)169 && add.getAddress()[1]==(byte)254)
							continue;
						all.add(add);
						ipv4.add(byteArrayToInt(add.getAddress()));
	
					}
					else
					{
						all.add(add);
						ipv6.add( ByteString.copyFrom(add.getAddress()));
					}
				}
			}
		}
		all.clear();
		
		ConnectMessages.Candidates.Builder builder=ConnectMessages.Candidates.newBuilder();
		builder.addAllInternetIpv4(ipv4);
		builder.addAllInternetIpv6(ipv6);
		if (wifi!=null && wifi.isWifiEnabled())
		{
			WifiInfo info=wifi.getConnectionInfo();
			builder.setBssid(ByteString.copyFrom(mactoByteArray(info.getBSSID())));
		}
		BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
		if (adapter!=null && adapter.isEnabled())
		{
			if (BT_LISTEN_ANONYMOUS && Build.VERSION.SDK_INT>=Compatibility.VERSION_GINGERBREAD_MR1)
			{
				builder.setBluetoothAnonmymous(true);
			}
			builder.setBluetoothMac(byteArrayToInt(mactoByteArray(adapter.getAddress())));
		}
		return builder.build();
	}
	public static ArrayList<CharSequence> make(Context context,ConnectMessages.Candidates candidates) 
	{
		ArrayList<CharSequence> results=new ArrayList<CharSequence>();
		ConnectivityManager conn=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (conn==null || conn.getActiveNetworkInfo()==null)
		{
			return null;
		}
		boolean localNetwork;
		switch (conn.getActiveNetworkInfo().getType())
		{
			case ConnectivityManager.TYPE_MOBILE:
			case ConnectivityManager.TYPE_MOBILE_DUN:
			case ConnectivityManager.TYPE_MOBILE_HIPRI:
				localNetwork=false;
				break;
			default:
				localNetwork=true;
				break;
		}
		int[] priority; // odd: IPV6, even: IPV4;
		
		if (ETHERNET_IPV4_FIRST)
		{
			priority=new int[]{1,0};
		}
		else
			priority=new int[]{0,1};
		
		for (int prio:priority)
		{
			switch (prio)
			{
				case 0:
					tryIpv6(candidates, results, localNetwork);
					break;
				case 1:
					tryIpv4(candidates, results, localNetwork);
					break;
			}
		}
		
		
		if (candidates.hasBluetoothMac())
		{
			int i=candidates.getBluetoothMac();
			String btmac=Integer.toHexString(i);
			if (candidates.hasBluetoothAnonmymous())
				results.add(SCHEME_BT+btmac);
			else
				results.add(SCHEME_BTS+btmac);
		}
// FIXME: gérer le cas du results vide !
		return results;
	}
	private static void tryIpv4(ConnectMessages.Candidates candidates, ArrayList<CharSequence> results,
			boolean localNetwork)
	{
		for (int i=candidates.getInternetIpv4Count()-1;i>=0;--i)
		{
			try
			{
				InetAddress add=Inet4Address.getByAddress(intToByteArray(candidates.getInternetIpv4(i)));
				if (add.isLoopbackAddress())
					continue;
				if (add.isLinkLocalAddress() && !localNetwork) 
					continue;
				results.add(SCHEME_TCP4+add.getHostAddress());
			}
			catch (UnknownHostException e)
			{
				if (V) Log.v(TAG_CONNECT,PREFIX_LOG+"Invalide ipv4. Ignore.");
			}
		}
	}
	private static void tryIpv6(ConnectMessages.Candidates candidates, ArrayList<CharSequence> results,
			boolean localNetwork)
	{
		for (int i=candidates.getInternetIpv6Count()-1;i>=0;--i)
		{
			try
			{
				InetAddress add=Inet6Address.getByAddress(candidates.getInternetIpv6(i).toByteArray());
				if (add.isLoopbackAddress())
					continue;
				if (add.isLinkLocalAddress() && !localNetwork) 
					continue;
				results.add(SCHEME_TCP6+'['+add.getHostAddress()+']');
			}
			catch (UnknownHostException e)
			{
				if (V) Log.v(TAG_CONNECT,PREFIX_LOG+"Invalide ipv4. Ignore.");
			}
		}
	}
	
	private static byte[] mactoByteArray(String bssid)
	{
		byte[] result=new byte[bssid.length()/3+1];
		for (int i=0;i<bssid.length();i+=3)
		{
			result[i/3]=(byte)("0123456789ABCDEF".indexOf(bssid.charAt(i)) << 4 |
						"0123456789ABCDEF".indexOf(bssid.charAt(i+1)));
		}
		return result;
			
	}
	private static final byte[] intToByteArray(int value) 
	{
        return new byte[] {
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)value};
	}
	private static final int byteArrayToInt(byte [] b) {
        return (b[0] << 24)
                + ((b[1] & 0xFF) << 16)
                + ((b[2] & 0xFF) << 8)
                + (b[3] & 0xFF);
	}
	
}
