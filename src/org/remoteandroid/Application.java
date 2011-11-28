package org.remoteandroid;

import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.*;
import static org.remoteandroid.Constants.PREFERENCES_NAME;
import static org.remoteandroid.Constants.PREFERENCES_PRIVATE_KEY;
import static org.remoteandroid.Constants.PREFERENCES_PUBLIC_KEY;
import static org.remoteandroid.Constants.PREFERENCES_UUID;
import static org.remoteandroid.Constants.STRICT_MODE;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PrivilegedAction;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import javax.jmdns.impl.DNSRecord.IPv6Address;

import org.remoteandroid.discovery.ip.IPDiscoverAndroids;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.Constants;
import org.remoteandroid.internal.Login;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.RemoteAndroidManagerImpl;
import org.remoteandroid.login.LoginImpl;
import org.remoteandroid.service.RemoteAndroidBackup;
import org.remoteandroid.service.RemoteAndroidManagerStub;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Handler;
import android.os.StrictMode;
import android.provider.Settings.Secure;
import android.util.Log;

// La stratégie de gestion des paramètres est la suivante:
// Un fichier preferences identifié par deviceid. 
// Ainsi, il est possible d'avoir des backups par device, et ainsi partager le même id sur différents device.
// C'est utils pour les paramètres vitaux d'identification du terminal, les pairings, etc.
// TODO: possible de récupérer les pairings via le backup ?
// Il est possible d'avoir des paramètres partagés entre les devices, en utilisant un property différent.

// TODO: Bug lors de l'installation en mode burst
// TODO: Verifier si pas bug lors de l'activation/desactivation du wifi, pour la déclaration mDNS

//@ReportsCrashes(formKey = "dDg0Wkx6MS1wLXV4QlFxMXJON2c0SHc6MQ")
public class Application extends android.app.Application
{

	static String			TAG			= "Application";

	public static Context	sAppContext;

	private static UUID		sUuid;
	
	private static KeyPair	sKeyPair;

	static String			sName;

	static String			sBackName	= "Unknown";

	public static String	sDeviceId;

	public static String	sPackageName;

	
	public static RemoteAndroidManagerImpl sManager;
	
	public static RemoteAndroidManagerStub sDiscover;
	
	// FIXME: Bug if change language
	private static final String				sGroupingSeparator	= ", ";
	private static CharSequence				sIPName;
	private static CharSequence				sBTName;
	private static CharSequence				sPaired;
	private static CharSequence				sNotPaired;
	
	public static final ScheduledThreadPoolExecutor sScheduledPool=new ScheduledThreadPoolExecutor(5);
	public static final ExecutorService sThreadPool=Executors.newCachedThreadPool();
	public static Handler sHandler=new Handler();

	
	private static Cookies	sCookies=new Cookies();
	private static boolean sAsyncInitDone=false; 
	static
	{
		sBackName = Build.MODEL;
	}
    public static SecureRandom sRandom; //FIXME: Multi-thread ?
    static
    {
    	try
		{
			sRandom=SecureRandom.getInstance("SHA1PRNG");
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new InternalError(e.getMessage());
		}
    }

    public static long getCookie(String uri)
    {
    	return sCookies.getCookie(uri);
    }
    public static void addCookie(String uri,long cookie)
    {
    	sCookies.addCookie(uri, cookie);
    }
    public static void clearCookie()
    {
    	sCookies.clear();
    }
	public static final UUID getUUID()
	{
		waitInit();
		return sUuid;
	}

	public static String getName()
	{
		waitInit();
		// Settings.Secure.getString(context.getContentResolver(), "android_id");
		if (sName != null)
			return sName;
		return sBackName;
	}
	public static KeyPair getKeyPair()
	{
		waitInit();
		return sKeyPair;
	}
	
	private static void waitInit()
	{
		while (!sAsyncInitDone)
		{
			try
			{
				Thread.sleep(100);
			}
			catch (InterruptedException e)
			{
				// Ignore
			}
		}
	}
	private static SharedPreferences sPreferences;
	public static SharedPreferences getPreferences()
	{
		waitInit();
		return sPreferences;
	}
	public static SharedPreferences getPreferences(String name,int mode)
	{
		return sAppContext.getSharedPreferences(name, mode);
	}

	public static final void setName(final String name)
	{
		sName = name;
		new Thread()
		{
			public void run()
			{
				getPreferences().edit().putString(PREFERENCES_NAME, name).commit();
			}
		}.start();
	}

	public static void setBackName(final String backname)
	{
		sBackName = backname;
		new Thread()
		{
			public void run()
			{
				getPreferences().edit().putString(PREFERENCES_BACKNAME, backname).commit();
			}
		}.start();
	}
	

	public static StringBuilder getTechnologies(RemoteAndroidInfoImpl info,boolean paired)
	{
		StringBuilder buf = new StringBuilder();
		// Show networks technologies
		if (info.isConnectableWithBluetooth())
			buf.append(sBTName).append(sGroupingSeparator);
		if (info.isConnectableWithIP())
			buf.append(sIPName).append(sGroupingSeparator);
		if (buf.length() > 0)
		{
			buf.setLength(buf.length() - sGroupingSeparator.length());
		}

		if (paired)
			buf.insert(0, (info.isBonded ? sPaired : sNotPaired)+" ");
		return buf;
	}

	@Override
	public void onCreate()
	{
		if (V) Log.v(TAG,PREFIX_LOG+"Application onCreate");
		AndroidLogHandler.initLoggerHandler();
		RemoteAndroidManagerImpl.initAppInfo(this);
		
		super.onCreate();

		if (Compatibility.VERSION_SDK_INT>Compatibility.VERSION_DONUT)
		{
			// VerifyWrapper
			new Runnable()
			{

				@Override
				public void run()
				{
					BluetoothAdapter.getDefaultAdapter();
				}
			}.run();
		}

		enableStrictMode();
		enableHttpResponseCache();
		disableConnectionReuseIfNecessary();
		
		sIPName = getResources().getText(R.string.network_ip);
		sBTName = getResources().getText(R.string.network_bt);
		sPaired = getResources().getText(R.string.device_paired);
		sNotPaired = getResources().getText(R.string.device_not_paired);

		if (ETHERNET_ONLY_IPV4)
		{
			java.lang.System.setProperty("java.net.preferIPv4Stack", "true");
			java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
		}
		// Init crash dump
		//TODO /*if (!D)*/ ACRA.init(this);
		
		sAppContext = this;
		if (V) Log.v(TAG, PREFIX_LOG+'[' + Compatibility.MANUFACTURER + "] Application.onCreate...");
		sPackageName = getPackageName();
		sDeviceId = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
		if (sDeviceId == null)
			sDeviceId = "UnknownDevice";
		// Manage the discover service
		sDiscover=new RemoteAndroidManagerStub(this);
		RemoteAndroidManagerImpl.setManager(sDiscover);
		sManager=new RemoteAndroidManagerImpl(sAppContext);
		IPDiscoverAndroids.initIPDiscover(this);
		new Thread()
		{
			@Override
			public void run()
			{
				asyncInit();
			}

		}.start();

		// Expose shared library
		if (USE_SHAREDLIB)
		{
			// Copy a public version of shared library
			new Thread("Copy shared library")
			{
				@Override
				public void run() 
				{
					initSharedLibrary();
				}

			}.start();
			
		}
		
	}
	
	private void disableConnectionReuseIfNecessary() 
	{
	    // HTTP connection reuse which was buggy pre-froyo
	    if (Compatibility.VERSION_SDK_INT < Compatibility.VERSION_FROYO) 
	    {
	        System.setProperty("http.keepAlive", "false");
	    }
	}
	private void enableHttpResponseCache() 
	{
	    try 
	    {
	        long httpCacheSize = 10 * 1024 * 1024; // 10 MiB
	        File httpCacheDir = new File(getCacheDir(), "http");
	        Class.forName("android.net.http.HttpResponseCache")
	            .getMethod("install", File.class, long.class)
	            .invoke(null, httpCacheDir, httpCacheSize);
	    } catch (Exception httpResponseCacheNotAvailable) 
	    {
	    	if (V) Log.v(TAG,PREFIX_LOG+"Failed to enable http cache");
	    }
	}	
	// TODO: invoquer dans on boot ?
	private void initSharedLibrary()
	{
		final SharedPreferences prefs=getSharedPreferences("sharedlib",Context.MODE_PRIVATE);
		final long lastCopied=prefs.getLong("copy", -1);
		final long packageLastModified=new File(RemoteAndroidManagerImpl.sAppInfo.publicSourceDir).lastModified();
		if (packageLastModified>lastCopied)
		{
			InputStream in=null;
			OutputStream out=null;
			try
			{// TODO: Eviter de refaire si c'est déjà correct
				in=getAssets().open(SHARED_LIB);
				out=openFileOutput(SHARED_LIB, Context.MODE_WORLD_READABLE);
				byte[] buf=new byte[1024*4];
				for (;;)
				{
					int s=in.read(buf);
					if (s<1) break;
					out.write(buf,0,s);
				}
				prefs.edit().putLong("copy",packageLastModified).commit();
			}
			catch (IOException e)
			{
				if (E) Log.e(TAG,PREFIX_LOG+"Impossible to copy shared library",e);
			}
			finally
			{
				if (in!=null)
				{
					try
					{
						in.close();
					} 
					catch (IOException e)
					{
						if (E) Log.e(TAG,PREFIX_LOG+"Impossible to close input stream",e);
					}
					try
					{
						out.close();
					} 
					catch (IOException e)
					{
						if (E) Log.e(TAG,PREFIX_LOG+"Impossible to close input stream",e);
					}
				}
			}
		}
	}
	
	private void asyncInit() throws Error
	{
		try
		{
			// TODO http://stackoverflow.com/questions/785973/what-is-the-most-appropriate-way-to-store-user-settings-in-android-application
			// Initialise the UUID, keys and name

			if (V) Log.v(TAG, PREFIX_LOG+"Application init preferences.");
			Login.sLogin=new LoginImpl();
			String adapterName=null;
			if (Compatibility.VERSION_SDK_INT>Compatibility.VERSION_DONUT)
			{
				// VerifyWrapper
				adapterName=new PrivilegedAction<String>()
				{

					@Override
					public String run()
					{
						BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
						
						if (adapter!=null)
						{
							return adapter.getName();
						}
						return null;
					}
				}.run();
			}
			sPreferences=sAppContext.getSharedPreferences(Application.sDeviceId, Context.MODE_PRIVATE);
			final SharedPreferences preferences = sPreferences;
			Editor editor = null;
			sName = preferences.getString(PREFERENCES_NAME, null);
			if (sName == null)
			{
				if (adapterName != null)
				{
					sBackName = adapterName;
					if (editor == null)
						editor = preferences.edit();
					editor.putString(PREFERENCES_BACKNAME, sBackName).commit();
				}
			}
			String strUuid = preferences.getString(PREFERENCES_UUID, null);
			if (strUuid == null)
			{
				sUuid = UUID.randomUUID();
				if (V) Log.v(TAG,PREFIX_LOG+"Generate key pair..."); // FIXME: Ca prend du temps lors du premier lancement. Ajouter boite d'attente.
				sKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
				if (editor == null)
					editor = preferences.edit();
				editor.putString(PREFERENCES_UUID, sUuid.toString());
				editor.putString(PREFERENCES_PUBLIC_KEY,bytesToHexString(sKeyPair.getPublic().getEncoded()));
				editor.putString(PREFERENCES_PRIVATE_KEY,bytesToHexString(sKeyPair.getPrivate().getEncoded()));
				if (V) Log.v(TAG,PREFIX_LOG+"Key pair done.");
				assert("X.509".equals(sKeyPair.getPublic().getFormat()));
				assert("PKCS#8".equals(sKeyPair.getPrivate().getFormat()));
			}
			else
			{
				if (V) Log.v(TAG,PREFIX_LOG+"Load key pair...");
				sUuid = UUID.fromString(strUuid);
				KeyFactory rsaFactory = KeyFactory.getInstance("RSA");

				byte[] pubBytes=hexStringToBytes(preferences.getString(PREFERENCES_PUBLIC_KEY, ""));
				X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubBytes);
				PublicKey publicKey = rsaFactory.generatePublic(pubKeySpec);

				byte[] privBytes=hexStringToBytes(preferences.getString(PREFERENCES_PRIVATE_KEY, ""));
				PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(privBytes);
				PrivateKey privateKey = rsaFactory.generatePrivate(privKeySpec);
				sKeyPair=new KeyPair(publicKey, privateKey);
				if (V) Log.v(TAG,PREFIX_LOG+"Key pair done.");
			}
			if (editor != null)
			{
				editor.commit();
				dataChanged();
			}

			if (V)
				Log.v(TAG, PREFIX_LOG+'[' + Compatibility.MANUFACTURER + "] deviceId=" + sDeviceId + " name="
						+ sName + " backName=" + sBackName + " uuid=" + sUuid);
			sAsyncInitDone=true;
			
			
		}
		catch (NoSuchAlgorithmException e)
		{
			if (E) Log.e(TAG,"Init error",e);
			throw new Error(e);
		}
		catch (InvalidKeySpecException e)
		{
			// TODO Auto-generated catch block
			if (E) Log.e(TAG,"Init error",e);
			throw new Error(e);
		}
	}


	public static void enableStrictMode()
	{
		if (D && STRICT_MODE && Compatibility.VERSION_SDK_INT>=Compatibility.VERSION_GINGERBREAD)
		{
	         StrictMode.setThreadPolicy(
	        		 new StrictMode.ThreadPolicy.Builder()
	                 .detectAll()
	                 .penaltyLog()
	                 .penaltyDialog()
	                 .build());
	         StrictMode.setVmPolicy(
	        		 new StrictMode.VmPolicy.Builder()
	                 .detectAll()
	                 .penaltyDropBox()
	                 .build());
	     }
	}
	public static void dataChanged()
	{
		if (Compatibility.VERSION_SDK_INT>=Compatibility.VERSION_FROYO)
		{
			new Runnable()
			{
				
				@Override
				public void run()
				{
					RemoteAndroidBackup.dataChanged();
				}
			};
		}		
	}

	@Override
	public void onTerminate()
	{
		super.onTerminate();
		if (V) Log.v(TAG,PREFIX_LOG+"Application onTerminate");
	}

	/**
	 * Converts an array of bytes to a string of hexadecimal characters. Leading null bytes are
	 * preserved in the output.
	 * <p>
	 * The input byte stream is assumed to be a positive, two's complement representation of an
	 * integer. The return value is the hexadecimal string representation of this value.
	 * 
	 * @param bytes
	 *            the bytes to convert
	 * @return the string representation
	 */
	private static String bytesToHexString(byte[] bytes)
	{
		if (bytes == null || bytes.length == 0)
		{
			return "";
		}
		BigInteger bigint = new BigInteger(1, bytes);
		int formatLen = bytes.length * 2;
		return String.format("%0" + formatLen + "x", bigint);
	}

	/**
	 * Converts a string of hex characters to a byte array.
	 * 
	 * @param hexstr
	 *            the string of hex characters
	 * @return a byte array representation
	 */
	private static byte[] hexStringToBytes(String hexstr)
	{
		if (hexstr == null || hexstr.length() == 0 || (hexstr.length() % 2) != 0)
		{
			throw new IllegalArgumentException("Bad input string.");
		}

		byte[] result = new byte[hexstr.length() / 2];
		for (int i = 0; i < result.length; i++)
		{
			result[i] = (byte) Integer.parseInt(hexstr.substring(2 * i, 2 * (i + 1)), 16);
		}
		return result;
	}
	
	public static void propertyCommit(final Editor editor)
	{
    	new Thread()
    	{
    		public void run() 
    		{
    			editor.commit();
    		}
    	}.start();
	}
	
	static public Object hackNullException(Throwable e)
	{
		return e;
	}

	static
	{
		setDeviceParameter();
	}
	// Custom parameter for model
	private static final void setDeviceParameter()
	{
//		if (D)
//		{
//			final int v=Build.VERSION.SDK_INT;
//			Log.d("INFO","Board:"+Build.BOARD);
//			if (v>=8) Log.d("INFO","Bootloader:"+Build.BOOTLOADER); // v8
//			Log.d("INFO","Brand:"+Build.BRAND);
//			Log.d("INFO","CPU Abi:"+Build.CPU_ABI); // v4
//			if (v>=8) Log.d("INFO","CPU Abi2:"+Build.CPU_ABI2); // v8
//			Log.d("INFO","Device:"+Build.DEVICE);
//			if (v>=3) Log.d("INFO","Display:"+Build.DISPLAY); // v3
//			Log.d("INFO","Fingerprint:"+Build.FINGERPRINT);
//			if (v>=8) Log.d("INFO","Hardware:"+Build.HARDWARE); // v8
//			Log.d("INFO","Host:"+Build.HOST);
//			Log.d("INFO","Id:"+Build.ID);
//			Log.d("INFO","Manufacturer:"+Build.MANUFACTURER); // v4
//			Log.d("INFO","Model:"+Build.MODEL);
//			Log.d("INFO","Product:"+Build.PRODUCT);
//			if (v>=8) Log.d("INFO","Radio:"+Build.RADIO); // v8
//			if (v>=9) Log.d("INFO","Serial:"+Build.SERIAL); // v9
//			Log.d("INFO","Tags:"+Build.TAGS);
//			Log.d("INFO","Time:"+Build.TIME);
//			Log.d("INFO","Type:"+Build.TYPE);
//			Log.d("INFO","Unknown:"+Build.UNKNOWN);
//			Log.d("INFO","User:"+Build.USER);
//			Log.d("INFO","Version.codename:"+Build.VERSION.CODENAME); // v4
//			Log.d("INFO","Version.incremental:"+Build.VERSION.INCREMENTAL);
//			Log.d("INFO","Version.release:"+Build.VERSION.RELEASE);
//			Log.d("INFO","Version.sdk int:"+Build.VERSION.SDK_INT);
//			Log.d("INFO","--------------------------");
//		}		
		// TODO: Samsung7
		if (Build.FINGERPRINT.equals("htc_wwe/htc_ace/ace:2.3.3/GRI40/87995:user/release-keys"))
		{
			BLUETOOTH=false;
			BT_DISCOVERY_IN_PARALLEL=false;
			BT_DISCOVER_ANONYMOUS_IN_PARALLELE=false;
			BT_INFORM_PRESENCE_IN_PARALLEL=false;
			BT_HACK_DELAY_STARTUP=300L;
			// http://code.google.com/p/android/issues/detail?id=8407
			ETHERNET_CAN_RECEIVE_MULTICAST=false;
		}
	}
	
}
