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
package org.droid2droid.pairing;

import static org.droid2droid.Constants.ETHERNET_REFUSE_LOCAL_IPV6;
import static org.droid2droid.Constants.PAIR_PERSISTENT;
import static org.droid2droid.Constants.TAG_CONNECT;
import static org.droid2droid.Constants.TAG_EXPOSE;
import static org.droid2droid.Droid2DroidManager.DEFAULT_PORT;
import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.ETHERNET;
import static org.droid2droid.internal.Constants.ETHERNET_ONLY_IPV4;
import static org.droid2droid.internal.Constants.I;
import static org.droid2droid.internal.Constants.KEYPAIR_ALGORITHM;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.SCHEME_TCP;
import static org.droid2droid.internal.Constants.V;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.droid2droid.ConnectionType;
import org.droid2droid.RAApplication;
import org.droid2droid.RemoteAndroidInfo;
import org.droid2droid.binder.AbstractSrvRemoteAndroid.ConnectionContext;
import org.droid2droid.discovery.Discover;
import org.droid2droid.internal.Base64;
import org.droid2droid.internal.Messages;
import org.droid2droid.internal.ProtobufConvs;
import org.droid2droid.internal.RemoteAndroidInfoImpl;
import org.droid2droid.internal.Tools;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;

import com.google.protobuf.ByteString;

// TODO: réorganiser le code et les classes de pairing
public final class Trusted
{
	private static final String TAG_PAIRING="Pairing";

	private static final String PAIRING_PREFIX="pairing.";
	private static final String KEY_ID=".id";
	private static final String KEY_PUBLICKEY=".publickey";
	private static final String KEY_NAME=".name";
	
	private static List<RemoteAndroidInfoImpl> sCachedBonded;

	public static RemoteAndroidInfoImpl getInfo(Context context)
	{
		final RemoteAndroidInfoImpl info=new RemoteAndroidInfoImpl();
		info.uuid=RAApplication.getUUID();
		info.name=RAApplication.getName();
		info.publicKey=RAApplication.getKeyPair().getPublic();
		info.version=VERSION.SDK_INT;
		info.feature=RAApplication.sFeature;
		try
		{
			Messages.Candidates candidates = getConnectMessage(context);
			info.uris=ProtobufConvs.toUris(RAApplication.sAppContext,candidates);
		}
		catch (UnknownHostException e)
		{
			if (E) Log.e(TAG_CONNECT,PREFIX_LOG+"Error when get local ip",e);
		}
		catch (SocketException e)
		{
			if (E) Log.e(TAG_CONNECT,PREFIX_LOG+"Error when get local ip",e);
		}
		return info;
	}
	
	public static boolean isBonded(RemoteAndroidInfo info)
	{
		getBonded();
		synchronized(sCachedBonded)
		{
			for (RemoteAndroidInfo i:sCachedBonded)
			{
				if (i.getUuid().equals(info.getUuid()))
					return true;
			}
		}
		return false;
	}
	
	public static boolean isBonded(PublicKey pubKey)
	{
		getBonded();
		synchronized(sCachedBonded)
		{
			for (RemoteAndroidInfo i:sCachedBonded)
			{
				if (i.getPublicKey().equals(pubKey))
					return true;
			}
		}
		return false;
	}
	
	public synchronized boolean isBonded(UUID uuid)
	{
		getBonded();
		synchronized(sCachedBonded)
		{
			for (RemoteAndroidInfo i:sCachedBonded)
			{
				if (i.getUuid().equals(uuid))
					return true;
			}
		}
		return false;
	}

	public static synchronized void registerDevice(Context context,ConnectionContext conContext)
	{
		registerDevice(context,conContext.mClientInfo,conContext.mType);
	}
	
	public static synchronized void registerDevice(Context context,RemoteAndroidInfoImpl info,ConnectionType type)
	{ 
		if (I) Log.i(TAG_PAIRING,PREFIX_LOG + "Register device "+info);
		HashSet<RemoteAndroidInfoImpl> duplicates=new HashSet<RemoteAndroidInfoImpl>();
		for (RemoteAndroidInfoImpl inf:getBonded())
		{
			if (inf.uuid.equals(info.uuid))
			{
				duplicates.add(inf);
			}
		}
		
		for (RemoteAndroidInfoImpl inf:duplicates)
			unregisterDevice(context, inf);
		
		info.isBonded=true;
		String baseKey=PAIRING_PREFIX+info.uuid.toString();
		if (PAIR_PERSISTENT)
		{
			Editor editor=RAApplication.getPreferences().edit();
			editor
				.putBoolean(baseKey+KEY_ID, true)
				.putString(baseKey+KEY_PUBLICKEY, Base64.encodeToString(info.publicKey.getEncoded(),Base64.DEFAULT))
				.putString(baseKey+KEY_NAME, info.name)
				.commit();
		}
		RAApplication.dataChanged();
		getBonded().add(info);

		if (type==ConnectionType.ETHERNET)
			info.isDiscoverByEthernet=true;
		if (type==ConnectionType.BT)
			info.isDiscoverByBT=true;
		Discover.getDiscover().discover(info);
	}
	
	public static synchronized void unregisterDevice(Context context,RemoteAndroidInfo info)
	{
		if (I) Log.i(TAG_PAIRING,PREFIX_LOG + "Unregister device "+info.getName());
		RAApplication.clearCookies();
		RemoteAndroidInfoImpl infoImpl=(RemoteAndroidInfoImpl)info;
		infoImpl.isBonded=false;
		String baseKey=PAIRING_PREFIX+infoImpl.uuid.toString();
		Editor editor=RAApplication.getPreferences().edit();
		editor
			.remove(baseKey+KEY_ID)
			.remove(baseKey+KEY_NAME)
			.remove(baseKey+KEY_PUBLICKEY)
			.commit();
		RAApplication.dataChanged();
		if (!getBonded().remove(info))
		{
			if (E) Log.e(TAG_PAIRING,PREFIX_LOG+"Impossible to remove "+info);
		}
		Discover.getDiscover().discover(infoImpl);
	}
	
	
	public static List<RemoteAndroidInfoImpl> getBonded()
	{
		try
		{
			if (sCachedBonded==null)
			{
				List<RemoteAndroidInfoImpl> result=Collections.synchronizedList(new ArrayList<RemoteAndroidInfoImpl>());
				SharedPreferences prefs=RAApplication.getPreferences();
				Map<String,?> alls=prefs.getAll();
				for (Map.Entry<String,?> entry:alls.entrySet())
				{
					String basekey=entry.getKey();
					if (basekey.startsWith(PAIRING_PREFIX) && basekey.endsWith(KEY_ID))
					{
						String uuid=basekey.substring(PAIRING_PREFIX.length(),basekey.lastIndexOf('.'));
						if (V) Log.v(TAG_PAIRING,"uuid="+uuid);
						basekey=PAIRING_PREFIX+uuid;
						RemoteAndroidInfoImpl info=new RemoteAndroidInfoImpl();
						info.uuid=UUID.fromString(uuid);
						info.name=prefs.getString(basekey+KEY_NAME,"unknown"/*FIXME: NLS*/);
						byte[] pubBytes=Base64.decode(prefs.getString(basekey+KEY_PUBLICKEY, null),Base64.DEFAULT);
						X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubBytes);
						info.publicKey = KeyFactory.getInstance(KEYPAIR_ALGORITHM).generatePublic(pubKeySpec);
						info.isBonded=true;
						result.add(info);
					}
				}
				sCachedBonded=result;
			}
			if (V) Log.v(TAG_PAIRING,PREFIX_LOG+"know "+sCachedBonded.size()+" bonded devices");
			return sCachedBonded;
		}
		catch (InvalidKeySpecException e)
		{
			throw new Error(e);
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new Error(e);
		}
	}
	public static RemoteAndroidInfoImpl getBonded(String uuid)
	{
		if (sCachedBonded==null)
			getBonded();
		UUID id=UUID.fromString(uuid);
		for (RemoteAndroidInfoImpl info:sCachedBonded)
		{
			if (info.uuid.equals(id))
			{
				return info;
			}
		}
		return null;
	}
	/**
	 * Update bonded device from uuid.
	 * 
	 * @param uuid
	 * @param addr
	 * @return
	 */
	public static RemoteAndroidInfoImpl update(UUID uuid,InetAddress[] addr)
	{
		getBonded();
		synchronized(sCachedBonded)
		{
			for (RemoteAndroidInfo i:sCachedBonded)
			{
				if (i.getUuid().equals(uuid))
				{
					RemoteAndroidInfoImpl info=(RemoteAndroidInfoImpl)i;
					info.removeUrisWithScheme(SCHEME_TCP);
					if (addr==null)
					{
						info.isDiscoverByEthernet=false;
						info.removeUrisWithScheme(SCHEME_TCP);
					}
					else
					{
						for (int j=0;j<addr.length;++j)
						{
							final InetAddress add=addr[j];
							if (add instanceof Inet4Address)
							{
								info.addUris(SCHEME_TCP+"://"+add.getHostAddress()+":"+DEFAULT_PORT);
							}
							else
							{
								info.addUris(SCHEME_TCP+"://["+add.getHostAddress()+"]:"+DEFAULT_PORT);
							}
						}
					}
					return info;
				}
			}
		}
		return null;
	}
	/**
	 * Update bonded device from name.
	 * 
	 * @param name
	 * @param addr
	 * @return
	 */
	public static RemoteAndroidInfoImpl update(String name,InetAddress[] addr)
	{
		getBonded();
		synchronized(sCachedBonded)
		{
			for (RemoteAndroidInfo i:sCachedBonded)
			{
				if (i.getName().equals(name))
				{
					RemoteAndroidInfoImpl info=(RemoteAndroidInfoImpl)i;
					info.removeUrisWithScheme(SCHEME_TCP);
					if (addr!=null)
					{
						for (int j=0;j<addr.length;++j)
						{
							final InetAddress add=addr[j];
							if (add instanceof Inet4Address)
							{
								info.addUris(SCHEME_TCP+"://"+add.getHostAddress()+":"+DEFAULT_PORT);
							}
							else
							{
								info.addUris(SCHEME_TCP+"://["+add.getHostAddress()+"]:"+DEFAULT_PORT);
							}
						}
					}
					return info;
				}
			}
		}
		return null;
	}
	// Note: With device <Honey_comb, only one data network is on. Wifi OR mobile.
	// In honeycomb, it's possible to have Widi AND Mobile.
	@TargetApi(9)
	public static Messages.Candidates getConnectMessage(Context context) throws UnknownHostException, SocketException
	{
		final WifiManager wifi=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);
		Messages.Candidates.Builder builder=Messages.Candidates.newBuilder();

		if (ETHERNET)
		{
			final int port=DEFAULT_PORT; // TODO: variable port
			ArrayList<InetAddress> all=new ArrayList<InetAddress>(4);
			ArrayList<ByteString> ipv6=new ArrayList<ByteString>(2);
			ArrayList<Integer> ipv4=new ArrayList<Integer>(2);
	
			for (Enumeration<NetworkInterface> networks=NetworkInterface.getNetworkInterfaces();networks.hasMoreElements();)
			{
				NetworkInterface network=networks.nextElement();
				for (Enumeration<InetAddress> addrs=network.getInetAddresses();addrs.hasMoreElements();)
				{
					InetAddress add=addrs.nextElement();
					if (V) Log.v(TAG_CONNECT,PREFIX_LOG+"Analyse "+network.getName()+" "+add);
					if (VERSION.SDK_INT>=VERSION_CODES.GINGERBREAD)
					{
						if (network.isLoopback() || network.isVirtual() || !network.isUp())
							continue;
					}
					else
					{
						if (network.getName().startsWith("sit")) // vpn ?
							continue;
						if (network.getName().startsWith("dummy")) // ipv6 in ipv4
							continue;
						if (network.getName().startsWith("lo"))
							continue;
					}
					if (!all.contains(add))
					{
						if (add instanceof Inet4Address)
						{
							// Exclude RFC 3330. Auto configure ip
							if (add.getAddress()[0]==(byte)169 && add.getAddress()[1]==(byte)254)
								continue;
							all.add(add);
							ipv4.add(Tools.byteArrayToInt(add.getAddress()));
		
						}
						else
						{
							if (!ETHERNET_ONLY_IPV4)
							{
								if (ETHERNET_REFUSE_LOCAL_IPV6 && add.isLinkLocalAddress())
									continue;
								all.add(add);
								ipv6.add( ByteString.copyFrom(add.getAddress()));
							}
						}
					}
				}
			}
			all.clear();
			
			if (port!=DEFAULT_PORT)
				builder.setPort(port);
			builder.addAllInternetIpv4(ipv4);
			builder.addAllInternetIpv6(ipv6);
			if (wifi!=null && wifi.isWifiEnabled())
			{
				WifiInfo info=wifi.getConnectionInfo();
				if (V) Log.v(TAG_EXPOSE,"bssid="+info.getBSSID());
				if (info.getBSSID()!=null)
					builder.setBssid(ByteString.copyFrom(mactoByteArray(info.getBSSID())));
			}
		}
		return builder.build();
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
}
