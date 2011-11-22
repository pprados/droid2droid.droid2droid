package org.remoteandroid.pairing;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.PAIR_PERSISTENT;
import static org.remoteandroid.Constants.PREFERENCES_ANO_ACTIVE;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.Constants.TIMEOUT_PAIR;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.remoteandroid.Application;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.binder.AbstractSrvRemoteAndroid.ConnectionContext;
import org.remoteandroid.internal.AbstractProtoBufRemoteAndroid;
import org.remoteandroid.internal.AbstractRemoteAndroidImpl;
import org.remoteandroid.internal.Base64;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.Tools;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import com.google.protobuf.ByteString;

// TODO: réorganiser le code et les classes de pairing
public class Trusted
{
	private static final String TAG_PAIRING="Pairing";

	private static final String PAIRING_PREFIX="pairing.";
	private static final String KEY_ID=".id";
	private static final String KEY_PUBLICKEY=".publickey";
	private static final String KEY_NAME=".name";
	
	private static List<RemoteAndroidInfoImpl> sCachedBonded;

	private Context mAppContext;
	private Handler mHandler; // FIXME: AppAhandler ?
	public Trusted(Context appContext,Handler handler)
	{
		mAppContext=appContext;
		mHandler=handler;
	}
	
	public static RemoteAndroidInfoImpl getInfo(Context context,ConnectionType type/*FIXME: parametre toujours utils ?*/)
	{
		final RemoteAndroidInfoImpl info=new RemoteAndroidInfoImpl();
		info.uuid=Application.getUUID();
		info.name=Application.getName();
		info.publicKey=Application.getKeyPair().getPublic();
		info.version=Compatibility.VERSION_SDK_INT;
		final SharedPreferences preferences=Application.getPreferences();
		final boolean acceptAnonymous=preferences.getBoolean(PREFERENCES_ANO_ACTIVE, false); //TODO: et pour BT ? Cf BT_DISCOVER_ANONYMOUS

		try
		{
			Messages.Candidates candidates = getConnectMessage(context);
			info.uris=ProtobufConvs.toUris(candidates);
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
			Editor editor=Application.getPreferences().edit();
			editor
				.putBoolean(baseKey+KEY_ID, true)
				.putString(baseKey+KEY_PUBLICKEY, Base64.encodeToString(info.publicKey.getEncoded(),Base64.DEFAULT))
				.putString(baseKey+KEY_NAME, info.name)
				.commit();
		}
		Application.dataChanged();
		getBonded().add(info);

		if (type==ConnectionType.ETHERNET)
			info.isDiscoverEthernet=true;
		if (type==ConnectionType.BT)
			info.isDiscoverBT=true;
		Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
		intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
		intent.putExtra(RemoteAndroidManager.EXTRA_UPDATE,true);
		context.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
	}
	
	public static synchronized void unregisterDevice(Context context,RemoteAndroidInfo info)
	{
		if (I) Log.i(TAG_PAIRING,PREFIX_LOG + "Unregister device "+info.getName());
		RemoteAndroidInfoImpl infoImpl=(RemoteAndroidInfoImpl)info;
		infoImpl.isBonded=false;
		String baseKey=PAIRING_PREFIX+infoImpl.uuid.toString();
		Editor editor=Application.getPreferences().edit();
		editor
			.remove(baseKey+KEY_ID)
			.remove(baseKey+KEY_NAME)
			.remove(baseKey+KEY_PUBLICKEY)
			.commit();
		Application.dataChanged();
		if (!getBonded().remove(info))
		{
			if (E) Log.e(TAG_PAIRING,PREFIX_LOG+"Impossible to remove "+info);
		}
		
		Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
		intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
		intent.putExtra(RemoteAndroidManager.EXTRA_UPDATE,true);
		context.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
	}
	
	
	public static List<RemoteAndroidInfoImpl> getBonded()
	{
		try
		{
			if (sCachedBonded==null)
			{
				List<RemoteAndroidInfoImpl> result=Collections.synchronizedList(new ArrayList<RemoteAndroidInfoImpl>());
				SharedPreferences prefs=Application.getPreferences();
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
						info.publicKey = KeyFactory.getInstance("RSA").generatePublic(pubKeySpec);
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
					for (int j=0;j<addr.length;++j)
					{
						final InetAddress add=addr[j];
						if (add instanceof Inet4Address)
						{
							info.addUris(SCHEME_TCP+"://"+add.getHostAddress()+":"+RemoteAndroidManager.DEFAULT_PORT);
						}
						else
						{
							info.addUris(SCHEME_TCP+"://["+add.getHostAddress()+"]:"+RemoteAndroidManager.DEFAULT_PORT);
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
								info.addUris(SCHEME_TCP+"://"+add.getHostAddress()+":"+RemoteAndroidManager.DEFAULT_PORT);
							}
							else
							{
								info.addUris(SCHEME_TCP+"://["+add.getHostAddress()+"]:"+RemoteAndroidManager.DEFAULT_PORT);
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
	public static Messages.Candidates getConnectMessage(Context context) throws UnknownHostException, SocketException
	{
		final WifiManager wifi=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);
		Messages.Candidates.Builder builder=Messages.Candidates.newBuilder();

		if (ETHERNET)
		{
			final int port=RemoteAndroidManager.DEFAULT_PORT; // TODO: variable port
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
							ipv4.add(Tools.byteArrayToInt(add.getAddress()));
		
						}
						else
						{
							if (!ETHERNET_ONLY_IPV4)
							{
								all.add(add);
								ipv6.add( ByteString.copyFrom(add.getAddress()));
							}
						}
					}
				}
			}
			all.clear();
			
			if (port!=RemoteAndroidManager.DEFAULT_PORT)
				builder.setPort(port);
			builder.addAllInternetIpv4(ipv4);
			builder.addAllInternetIpv6(ipv6);
			if (wifi!=null && wifi.isWifiEnabled())
			{
				WifiInfo info=wifi.getConnectionInfo();
				builder.setBssid(ByteString.copyFrom(mactoByteArray(info.getBSSID())));
			}
		}
		if (BLUETOOTH)
		{
			BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
			if (adapter!=null && adapter.isEnabled())
			{
				if (BT_LISTEN_ANONYMOUS && Build.VERSION.SDK_INT>=Compatibility.VERSION_GINGERBREAD_MR1)
				{
					builder.setBluetoothAnonmymous(true);
				}
				builder.setBluetoothMac(Tools.byteArrayToLong(mactoByteArray(adapter.getAddress())));
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
	
	// ---------------------------------
	private AbstractRemoteAndroidImpl mRemoteAndroid;
	public RemoteAndroidInfoImpl pairWith(ArrayList<String> uris)
	{
		try
		{
			String uri=null;
			for (int i=0;i<uris.size();++i)
			{
				uri=uris.get(i);
				Intent intent=new Intent(Intent.ACTION_MAIN,Uri.parse(uri));
				intent.putExtra(AbstractRemoteAndroidImpl.EXTRA_FOR_PAIRING, true);
				Application.sManager.bindRemoteAndroid(
						intent, 
						new  ServiceConnection()
						{
	
							@Override
							public void onServiceConnected(ComponentName name, IBinder service)
							{
								mRemoteAndroid=(AbstractRemoteAndroidImpl)service;
								synchronized (Trusted.this)
								{
									Trusted.this.notify();
								}
							}
	
							@Override
							public void onServiceDisconnected(ComponentName name)
							{
								mRemoteAndroid=null;
								synchronized (Trusted.this)
								{
									Trusted.this.notify();
								}
							}
						}, 0);
				synchronized (this)
				{
					try
					{
						wait(TIMEOUT_PAIR);
					}
					catch (InterruptedException e)
					{
						// Ignore
						if (D) Log.d(TAG_PAIRING,PREFIX_LOG + "Error when wait connect to remote device for pair");
					}
				}
				if (mRemoteAndroid!=null) break;
			}
			if (mRemoteAndroid==null)
			{
				if (E) Log.e(TAG_PAIRING,PREFIX_LOG + "Pairing process aborted");
				mHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						Toast.makeText(mAppContext, R.string.err_pairing_aborted, Toast.LENGTH_LONG).show();
					}
				});
				return null;
			}
	
			// Now i'm connected to the remote device
			try
			{
				final ConnectionType type=urlToType(uri);
				if (pairing(mRemoteAndroid,type,uri,TIMEOUT_PAIRING_ASK_CHALENGE))
				{
					registerDevice(mAppContext,(RemoteAndroidInfoImpl)mRemoteAndroid.getInfos(),type);
				}
				return mRemoteAndroid.mInfo;
			}
			catch (RemoteException e)
			{
				if (E) Log.e(TAG_PAIRING,PREFIX_LOG + "Pairing process aborted",e);
				mHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						Toast.makeText(mAppContext, R.string.err_pairing_aborted, Toast.LENGTH_LONG).show();
					}
				});
				return null;
			}
		}
		finally
		{
			if (mRemoteAndroid!=null)
				mRemoteAndroid.close();
		}
	}
	
	public void unpairWith(RemoteAndroidInfo info)
	{
		try
		{
			String[] uris=info.getUris();
			String uri=null;
			for (int i=0;i<uris.length;++i)
			{
				uri=uris[i];
				Intent intent=new Intent(Intent.ACTION_MAIN,Uri.parse(uri));
				intent.putExtra(AbstractRemoteAndroidImpl.EXTRA_FOR_PAIRING, true);
				Application.sManager.bindRemoteAndroid(
						intent, 
						new  ServiceConnection()
						{
	
							@Override
							public void onServiceConnected(ComponentName name, IBinder service)
							{
								mRemoteAndroid=(AbstractRemoteAndroidImpl)service;
								synchronized (Trusted.this)
								{
									Trusted.this.notify();
								}
							}
	
							@Override
							public void onServiceDisconnected(ComponentName name)
							{
								mRemoteAndroid=null;
								synchronized (Trusted.this)
								{
									Trusted.this.notify();
								}
							}
						}, 0);
				synchronized (this)
				{
					try
					{
						wait(TIMEOUT_PAIR);
					}
					catch (InterruptedException e)
					{
						// Ignore
						if (D) Log.d(TAG_PAIRING,PREFIX_LOG + "Error when wait connect to remote device for pair");
					}
				}
				if (mRemoteAndroid!=null) break;
			}
			if (mRemoteAndroid!=null)
			{
				final ConnectionType type=urlToType(uri);
				unpairing(mRemoteAndroid,type,TIMEOUT_PAIRING_ASK_CHALENGE);
			}
			Trusted.unregisterDevice(mAppContext.getApplicationContext(), info);
		}
		finally
		{
			if (mRemoteAndroid!=null)
				mRemoteAndroid.close();
		}
	}
	
	//-----------------------------

    public boolean pairing(AbstractRemoteAndroidImpl remoteandroid,ConnectionType type,String uri,long timeout) throws RemoteException
	{
		SimplePairing pairing=new SimplePairing(
				mAppContext,mHandler,remoteandroid.mManager.getInfos(),remoteandroid.getInfos(),type);
		//mInitiatingPairingContext=...
		return pairing.client((AbstractProtoBufRemoteAndroid)remoteandroid,uri,timeout);
	}
    public void unpairing(AbstractRemoteAndroidImpl remoteandroid,ConnectionType type,long timeout)
    {
		SimplePairing pairing=new SimplePairing(mAppContext,mHandler,
				remoteandroid.mManager.getInfos(),remoteandroid.getInfos(),type);
		//mInitiatingPairingContext=...
		pairing.unpairing((AbstractProtoBufRemoteAndroid)remoteandroid,timeout);
    }
	
    private static ConnectionType urlToType(String uri)
    {
		if (uri.startsWith("tcp"))
			return ConnectionType.ETHERNET;
		if (uri.startsWith("bt"))
			return ConnectionType.BT;
    	return null;
    }
}
