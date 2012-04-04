package org.remoteandroid;

import static org.remoteandroid.Constants.*;

import android.preference.Preference;
import android.provider.ContactsContract.Contacts;
import static org.remoteandroid.Constants.NFC;
import static org.remoteandroid.Constants.PREFERENCES_ACTIVE;
import static org.remoteandroid.Constants.PREFERENCES_BACKNAME;
import static org.remoteandroid.Constants.PREFERENCES_NAME;
import static org.remoteandroid.Constants.PREFERENCES_PRIVATE_KEY;
import static org.remoteandroid.Constants.PREFERENCES_PUBLIC_KEY;
import static org.remoteandroid.Constants.PREFERENCES_UUID;
import static org.remoteandroid.Constants.QRCODE;
import static org.remoteandroid.Constants.SMS;
import static org.remoteandroid.Constants.STRICT_MODE;
import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.RemoteAndroidInfo.*;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_CAMERA;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_HP;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_MICROPHONE;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NFC;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_TELEPHONY;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_WIFI;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.ETHERNET;
import static org.remoteandroid.internal.Constants.ETHERNET_ONLY_IPV4;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.SHARED_LIB;
import static org.remoteandroid.internal.Constants.USE_SHAREDLIB;
import static org.remoteandroid.internal.Constants.V;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.remoteandroid.discovery.ip.IPDiscoverAndroids;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.Login;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.RemoteAndroidManagerImpl;
import org.remoteandroid.login.LoginImpl;
import org.remoteandroid.service.RemoteAndroidBackup;
import org.remoteandroid.service.RemoteAndroidManagerStub;
import org.remoteandroid.service.RemoteAndroidService;
import org.remoteandroid.ui.connect.qrcode.old.CameraManager;
import org.remoteandroid.ui.contacts.AbstractSMSFragment;


import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.StrictMode;
import android.provider.ContactsContract;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;

// La stratégie de gestion des paramètres est la suivante:
// Un fichier preferences identifié par deviceid. 
// Ainsi, il est possible d'avoir des backups par device, et ainsi partager le même id sur différents device.
// C'est utils pour les paramètres vitaux d'identification du terminal, les pairings, etc.
// TODO: possible de récupérer les pairings via le backup ?
// Il est possible d'avoir des paramètres partagés entre les devices, en utilisant un property différent.

// TODO: Bug lors de l'installation en mode burst
// TODO: Verifier si pas bug lors de l'activation/desactivation du wifi, pour la déclaration mDNS
// TODO: fermer le service en cas de perte d'énergie

//@ReportsCrashes(formKey = "dDg0Wkx6MS1wLXV4QlFxMXJON2c0SHc6MQ")
public final class Application extends android.app.Application
{

	static String			TAG			= "Application";

	public static Context	sAppContext;

	private static UUID		sUuid;
	
	private static KeyPair	sKeyPair;

	static String			sName;

	static String			sBackName	= "Unknown";

	public static String	sDeviceId;

	public static String	sPackageName;

	
	private static RemoteAndroidManagerImpl sManager;
	
	public static RemoteAndroidManagerStub sDiscover;
	
	// FIXME: Bug if change language
	private static final String				sGroupingSeparator	= ", ";
	private static CharSequence				sUnknown;
	private static CharSequence				sIPName;
	private static CharSequence				sBTName;
	private static CharSequence				sPaired;
	private static CharSequence				sNotPaired;
	
	public static final ScheduledThreadPoolExecutor sScheduledPool=new ScheduledThreadPoolExecutor(5);
	public static final ExecutorService sThreadPool=Executors.newCachedThreadPool();
	public static final ExecutorService sSingleThread=Executors.newSingleThreadExecutor();
	public static Handler sHandler=new Handler();

	
	private static Cookies	sCookies=new Cookies();
	private static boolean sAsyncInitDone=false; 
	static
	{
		sBackName = Build.MODEL;
	}
    private static SecureRandom sRandom;
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

    public static synchronized long getCookie(String uri)
    {
    	return sCookies.getCookie(uri);
    }
    public static synchronized void removeCookie(String uri)
    {
    	sCookies.removeCookie(uri);
    }
    public static synchronized void addCookie(String uri,long cookie)
    {
    	sCookies.addCookie(uri, cookie);
    }
    public static synchronized void clearCookies()
    {
    	sCookies.clear();
    }
	public static final UUID getUUID()
	{
		waitInit();
		return sUuid;
	}
	public static RemoteAndroidManagerImpl getManager()
	{
		return sManager;
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
	
	private static void waitInit() // FIXME: Remove this method
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
				if (name==null)
					getPreferences().edit().remove(PREFERENCES_NAME).commit();
				else
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
		if (info.isConnectableWithIP())
			buf.append(sIPName).append(sGroupingSeparator);
		if (buf.length() > 0)
		{
			buf.setLength(buf.length() - sGroupingSeparator.length());
		}

		if (paired)
			buf.insert(0, (info.isBonded ? sPaired : sNotPaired)+" ");
		if (buf.length()==0)
		{
			buf.append(sUnknown);
		}
		return buf;
	}

	@Override
	public void onCreate()
	{
		sAppContext=this;
		if (V) Log.v(TAG,PREFIX_LOG+"Application onCreate");
		AndroidLogHandler.initLoggerHandler();
		RemoteAndroidManagerImpl.initAppInfo(this);
		
		super.onCreate();

		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.ECLAIR)
		{
			BluetoothAdapter.getDefaultAdapter();
		}

		enableStrictMode();
		enableHttpResponseCache();
		disableConnectionReuseIfNecessary();
		
		sUnknown = getResources().getText(R.string.network_unknown);
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

		
		initFeature();
		
		if (V) Log.v(TAG, PREFIX_LOG+'[' + Compatibility.MANUFACTURER + "] Application.onCreate...");
		sPackageName = getPackageName();
		sDeviceId = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
		if (sDeviceId == null)
			sDeviceId = "UnknownDevice";
		// Manage the discover service
		sDiscover=new RemoteAndroidManagerStub(this);
		sManager=new RemoteAndroidManagerImpl(this,sDiscover);
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
	public static long sFeature;
	private void initFeature()
	{
		long f=0;
		f|=FEATURE_SCREEN;
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.ECLAIR)
		{
			for (FeatureInfo feature:getPackageManager().getSystemAvailableFeatures())
			{
				if (QRCODE && "android.hardware.camera".equals(feature.name))					f|=FEATURE_CAMERA;
				else if (SOUND && "android.hardware.microphone".equals(feature.name))			f|=FEATURE_MICROPHONE|FEATURE_HP;
				else if (NFC && "android.hardware.nfc".equals(feature.name))					f|=FEATURE_NFC;
				else if (SMS && "android.hardware.telephony".equals(feature.name))				f|=FEATURE_TELEPHONY;
				else if (ETHERNET && "android.hardware.wifi".equals(feature.name))				f|=FEATURE_WIFI|FEATURE_NET;
				else if (WIFI_DIRECT && "android.hardware.wifi.direct".equals(feature.name)) 	f|=FEATURE_WIFI_DIRECT;
				else if (BUMP && "android.hardware.location.gps".equals(feature.name)) 			f|=FEATURE_LOCATION;
				else if (BUMP && "android.hardware.sensor.accelerometer".equals(feature.name)) 	f|=FEATURE_ACCELEROMETER;
				else if (BT && "android.hardware.bluetooth".equals(feature.name)) 				f|=FEATURE_BLUETOOTH;
				else if ("android.hardware.microphone".equals(feature.name)) 					f|=FEATURE_MICROPHONE;
			}
		}
		else
		{
			if (CameraManager.get()!=null)								f|=FEATURE_CAMERA;
			if (getSystemService(Context.AUDIO_SERVICE)!=null)			f|=FEATURE_MICROPHONE|FEATURE_HP;
			if (getSystemService(Context.TELEPHONY_SERVICE)!=null)		f|=FEATURE_TELEPHONY;
			if (getSystemService(Context.WIFI_SERVICE)!=null)			f|=FEATURE_WIFI|FEATURE_NET;
		}
		sFeature=f;
		
	}
	public static long getActiveFeature()
	{
		long f=Application.sFeature & FEATURE_SCREEN|FEATURE_HP|FEATURE_MICROPHONE|FEATURE_CAMERA;
		int netStatus=NetworkTools.getActiveNetwork(Application.sAppContext);
		if ((netStatus & NetworkTools.ACTIVE_NOAIRPLANE)!=0)
		{
			if ((netStatus & NetworkTools.ACTIVE_BLUETOOTH)!=0)
			{
				f|=FEATURE_BT;
			}
			if ((netStatus & NetworkTools.ACTIVE_PHONE_DATA|NetworkTools.ACTIVE_LOCAL_NETWORK)!=0)
			{
				f|=FEATURE_NET;
			}
			if ((netStatus & NetworkTools.ACTIVE_NFC)!=0)
			{
				f|=FEATURE_NFC;
			}
		}
		if ((netStatus & NetworkTools.ACTIVE_PHONE_SIM)!=0)
		{
			f|=FEATURE_TELEPHONY;
		}
		return f;
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
			String jarname=SHARED_LIB+".jar";
			{
				final String packageName="org.remoteandroid";
				PackageInfo info;
				try
				{
					info = sAppContext.getPackageManager().getPackageInfo(packageName, 0/*PackageManager.GET_CONFIGURATIONS*/);
					String jar=info.applicationInfo.dataDir+"/files/"+jarname;
					File old=new File(info.applicationInfo.dataDir+"/files",SHARED_LIB+".jar"+".old");
					if (old.exists())
						old.delete();
					if (new File(jar).exists())
					{
						new File(jar).renameTo(old);
					}
				}
				catch (NameNotFoundException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			InputStream in=null;
			OutputStream out=null;
			try
			{// TODO: Eviter de refaire si c'est déjà correct
				in=getAssets().open(jarname);
				out=openFileOutput(jarname, Context.MODE_WORLD_READABLE);
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

	private static final String[] sProjection =
		{ 
			Contacts.DISPLAY_NAME 
		};
	private static String getUserName()
	{
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.ICE_CREAM_SANDWICH)
		{
			ContentResolver cr = sAppContext.getContentResolver();
			Cursor cur = null;
			try
			{
				cur=cr.query(ContactsContract.Profile.CONTENT_URI,new String[]{ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY}, null, null, null);
				if (cur.moveToNext())
					return cur.getString(0);
			}
			finally
			{
				if (cur!=null) cur.close();
			}
		}
		return null;
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
			BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
			if (adapter!=null)
				adapterName=adapter.getName();
			String userName=getUserName();
			sPreferences=sAppContext.getSharedPreferences(Application.sDeviceId, Context.MODE_PRIVATE);
			final SharedPreferences preferences = sPreferences;
			Editor editor = null;
			sName = preferences.getString(PREFERENCES_NAME, null);
			if (sName == null)
			{
				if (userName!=null)
				{
					sBackName=userName;
				}
				else if (adapterName != null)
				{
					sBackName = adapterName;
				}
				if (sBackName!=null)
				{
					if (editor == null)
						editor = preferences.edit();
					editor.putString(PREFERENCES_BACKNAME, sBackName).commit();
				}
			}
			String strUuid = preferences.getString(PREFERENCES_UUID, null);
			if (strUuid == null)
			{
				if (HACK_UUID)
				{
					String finger=Build.MODEL;
					if ("Xoom".equals(finger))
						sUuid=new UUID(0,1);
					else if ("Galaxy Nexus".equals(finger))
						sUuid=new UUID(0,2);
					else if ("Desire HD".equals(finger))
						sUuid=new UUID(0,3);
					else if ("Milestone".equals(finger))
						sUuid=new UUID(0,4);
					else if ("HTC Magic".equals(finger))
						sUuid=new UUID(0,5);
					else if ("I7500".equals(finger))
						sUuid=new UUID(0,6);
					else
						sUuid = UUID.randomUUID();
				}
				else
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
			sAsyncInitDone=true; // FIXME:
			
			
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
		if (D && STRICT_MODE && Build.VERSION.SDK_INT>=Build.VERSION_CODES.GINGERBREAD)
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
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.FROYO)
		{
			RemoteAndroidBackup.dataChanged();
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

	static public void hideSoftKeyboard(Activity context)
	{
		InputMethodManager imm = (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(context.findViewById(android.R.id.content).getWindowToken(), 0);
				
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
	}
	@Override
	public void onTrimMemory(int level)
	{
		super.onTrimMemory(level);
		AbstractSMSFragment.onTrimMemory(level);
	}

	public static void startService()
	{
		new AsyncTask<Void, Void, Void>()
		{

			@Override
			protected Void doInBackground(Void... params)
			{
				Application.getPreferences();
				return null;
			}
			protected void onPostExecute(Void result) 
			{
				final Context context=sAppContext;
				SharedPreferences preferences=getPreferences();
				final boolean active=preferences.getBoolean(PREFERENCES_ACTIVE, false);
				final Intent intentRemoteContext=new Intent(context,RemoteAndroidService.class);
				final ActivityManager am=(ActivityManager)context.getSystemService(ACTIVITY_SERVICE);
				final List<ActivityManager.RunningServiceInfo> services=am.getRunningServices(100);
				final ComponentName name=new ComponentName(context,RemoteAndroidService.class);
				if (active)
				{
					boolean isStarted=false;
					for (ActivityManager.RunningServiceInfo rs:services)
					{
						if (rs.service.equals(name))
						{
							isStarted=rs.started;
							break;
						}
					}
					if (!isStarted)
					{
						if (context.startService(intentRemoteContext)==null)
						{
							if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"Impossible to start the service");
							// TODO
						}
					}
				}
			}
		}.execute();
	}
	public static synchronized long randomNextLong()
	{
		return sRandom.nextLong();
	}
	public static synchronized void randomNextBytes(byte[] bytes)
	{
		sRandom.nextBytes(bytes);
	}
}
