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
package org.droid2droid;

import static org.droid2droid.Constants.BT;
import static org.droid2droid.Constants.BUMP;
import static org.droid2droid.Constants.HACK_UUID;
import static org.droid2droid.Constants.NFC;
import static org.droid2droid.Constants.PREFERENCES_ACTIVE;
import static org.droid2droid.Constants.PREFERENCES_BACKNAME;
import static org.droid2droid.Constants.PREFERENCES_NAME;
import static org.droid2droid.Constants.PREFERENCES_PRIVATE_KEY;
import static org.droid2droid.Constants.PREFERENCES_PUBLIC_KEY;
import static org.droid2droid.Constants.PREFERENCES_UUID;
import static org.droid2droid.Constants.QRCODE;
import static org.droid2droid.Constants.SMS;
import static org.droid2droid.Constants.SOUND;
import static org.droid2droid.Constants.STRICT_MODE;
import static org.droid2droid.Constants.TAG_DISCOVERY;
import static org.droid2droid.Constants.WIFI_DIRECT;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_ACCELEROMETER;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_BLUETOOTH;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_BT;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_CAMERA;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_HP;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_LOCATION;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_MICROPHONE;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_NET;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_NFC;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_TELEPHONY;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_WIFI;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_WIFI_DIRECT;
import static org.droid2droid.internal.Constants.D;
import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.ETHERNET;
import static org.droid2droid.internal.Constants.ETHERNET_ONLY_IPV4;
import static org.droid2droid.internal.Constants.KEYPAIR_ALGORITHM;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.SECURE_RANDOM_ALGORITHM;
import static org.droid2droid.internal.Constants.SHARED_LIB;
import static org.droid2droid.internal.Constants.SIGNATURE_ALGORITHM;
import static org.droid2droid.internal.Constants.USE_SHAREDLIB;
import static org.droid2droid.internal.Constants.V;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import javax.net.ssl.KeyManager;
import javax.net.ssl.X509KeyManager;
import javax.security.auth.x500.X500Principal;

import org.bouncycastle.x509.X509V1CertificateGenerator;
import org.droid2droid.discovery.ip.IPDiscoverAndroids;
import org.droid2droid.internal.Droid2DroidManagerImpl;
import org.droid2droid.internal.Login;
import org.droid2droid.internal.NetworkTools;
import org.droid2droid.internal.Pairing;
import org.droid2droid.internal.RemoteAndroidInfoImpl;
import org.droid2droid.internal.socket.ip.NetworkSocketBossSender;
import org.droid2droid.login.LoginImpl;
import org.droid2droid.pairing.PairingImpl;
import org.droid2droid.service.RemoteAndroidBackup;
import org.droid2droid.service.RemoteAndroidManagerStub;
import org.droid2droid.service.RemoteAndroidService;
import org.droid2droid.ui.contacts.AbstractSMSFragment;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.StrictMode;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
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
public final class RAApplication extends android.app.Application
{

	private static String			TAG			= "Application";

	public static Context	sAppContext;

	private static UUID	sUuid;
	
	private static KeyPair	sKeyPair;

	private static String			sName;

	private static String			sBackName	= "Unknown";

	public static String	sDeviceId;

	public static String	sPackageName;

	
	private static Droid2DroidManagerImpl sManager;
	
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
			sRandom=SecureRandom.getInstance(SECURE_RANDOM_ALGORITHM);
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new InternalError(e.getMessage());
		}
    }
    public static SecureRandom getSecureRandom()
    {
    	return sRandom;
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
	public static final UUID getUUID_()
	{
		return sUuid;
	}
	public static Droid2DroidManagerImpl getManager()
	{
		return sManager;
	}
	public static String getName()
	{
		waitInit();
		if (sName != null)
			return sName;
		return sBackName;
	}
	public static KeyPair getKeyPair()
	{
		waitInit();
		return sKeyPair;
	}
	private static KeyManager[] sKeyManager;
	private static final String[] sServerAlias={"server"};
	private static final String[] sClientAlias={"client"};
	public static synchronized KeyManager[] getKeyManager()
	{
		if (sKeyManager==null)
		{
			try
			{
				final X509Certificate cert=generateX509V1Certificate(sKeyPair,sRandom);
				sKeyManager=new KeyManager[]
				{
					new X509KeyManager()
					{
						
						@Override
						public String[] getServerAliases(String keyType, Principal[] issuers)
						{
							return sServerAlias;
						}
						@Override
						public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket)
						{
							return sServerAlias[0];
						}
						
						@Override
						public String[] getClientAliases(String keyType, Principal[] issuers)
						{
							return sClientAlias;
						}
						@Override
						public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket)
						{
							return sClientAlias[0];
						}
						
						@Override
						public X509Certificate[] getCertificateChain(String alias)
						{
							return new X509Certificate[]{cert};
						}
						@Override
						public PrivateKey getPrivateKey(String alias)
						{
							return sKeyPair.getPrivate();
						}
					}
				};
				NetworkSocketBossSender.setKeyManagers(sKeyManager);
			}
			catch (Exception e)
			{
				throw new Error(e);
			}
		}
		return sKeyManager;
	}
	@SuppressWarnings("deprecation")
	private static X509Certificate generateX509V1Certificate(KeyPair pair, SecureRandom sr)
	{
		try
		{
			String dn="CN="+sUuid.toString();
	
			final Calendar calendar = Calendar.getInstance();
			calendar.add(Calendar.HOUR, -1);
			final Date startDate = new Date(calendar.getTimeInMillis());
			calendar.add(Calendar.YEAR, 1);
			final Date expiryDate = new Date(calendar.getTimeInMillis());
			final BigInteger serialNumber = BigInteger.valueOf(Math.abs(System.currentTimeMillis()));
	
			X509V1CertificateGenerator certGen = new X509V1CertificateGenerator();
			X500Principal dnName = new X500Principal(dn);
			certGen.setSerialNumber(serialNumber);
			certGen.setIssuerDN(dnName);
			certGen.setNotBefore(startDate);
			certGen.setNotAfter(expiryDate);
			certGen.setSubjectDN(dnName); // note: same as issuer
			certGen.setPublicKey(pair.getPublic());
			certGen.setSignatureAlgorithm(SIGNATURE_ALGORITHM);
	
			// FIXME: This method is deprecated, but Android Eclair does not provide the
			// generate() methods.
			if (VERSION.SDK_INT<VERSION_CODES.GINGERBREAD)
				return certGen.generateX509Certificate(pair.getPrivate(), "BC");
			else
				return  certGen.generate(pair.getPrivate(), sr);
		}
		catch (Exception e)
		{
			throw new Error(e);
		}
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
			@Override
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
			@Override
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
		Droid2DroidManagerImpl.initAppInfo(this);
		
		super.onCreate();

		if (VERSION.SDK_INT>=VERSION_CODES.ECLAIR)
		{
			Droid2DroidManagerImpl.getBluetoothAdapter();
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
		
		if (V) Log.v(TAG, PREFIX_LOG+'[' + Build.MANUFACTURER + "] Application.onCreate...");
		sPackageName = getPackageName();
		sDeviceId = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
		if (sDeviceId == null)
			sDeviceId = "UnknownDevice";
		// Manage the discover service
		sDiscover=new RemoteAndroidManagerStub(this);
		sManager=new Droid2DroidManagerImpl(this,sDiscover);
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
		long f=FEATURE_SCREEN|FEATURE_NET;
		if (VERSION.SDK_INT>=VERSION_CODES.ECLAIR)
		{
			// Emulator v10 with x86 return null
			if (getPackageManager().getSystemAvailableFeatures()!=null)
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
		}
		else
		{
			if (sAppContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA))	f|=FEATURE_CAMERA;
			if (getSystemService(Context.AUDIO_SERVICE)!=null)									f|=FEATURE_MICROPHONE|FEATURE_HP;
			if (getSystemService(Context.TELEPHONY_SERVICE)!=null)								f|=FEATURE_TELEPHONY;
			if (getSystemService(Context.WIFI_SERVICE)!=null)									f|=FEATURE_WIFI|FEATURE_NET;
		}
		sFeature=f;
		
	}
	public static long getActiveFeature()
	{
		long f=RAApplication.sFeature & FEATURE_SCREEN|FEATURE_HP|FEATURE_MICROPHONE|FEATURE_CAMERA;
		int netStatus=NetworkTools.getActiveNetwork(RAApplication.sAppContext);
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
	    if (VERSION.SDK_INT < VERSION_CODES.FROYO) 
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
		final long packageLastModified=new File(Droid2DroidManagerImpl.sAppInfo.publicSourceDir).lastModified();
		if (packageLastModified>lastCopied)
		{
			String jarname=SHARED_LIB+".jar";
			{
				final String packageName="org.droid2droid";
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
	@TargetApi(14)
	private static String getUserName()
	{
		if (VERSION.SDK_INT>=VERSION_CODES.ICE_CREAM_SANDWICH)
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
			Login.sLogin=new LoginImpl(null);
			Pairing.sPairing=new PairingImpl();
			String adapterName=null;
			BluetoothAdapter adapter=Droid2DroidManagerImpl.getBluetoothAdapter();
			if (adapter!=null)
				adapterName=adapter.getName();
			String userName=getUserName();
			sPreferences=sAppContext.getSharedPreferences(RAApplication.sDeviceId, Context.MODE_PRIVATE);
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
				sKeyPair = KeyPairGenerator.getInstance(KEYPAIR_ALGORITHM).generateKeyPair();
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
				KeyFactory rsaFactory = KeyFactory.getInstance(KEYPAIR_ALGORITHM);

				byte[] pubBytes=hexStringToBytes(preferences.getString(PREFERENCES_PUBLIC_KEY, ""));
				X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(pubBytes);
				PublicKey publicKey = rsaFactory.generatePublic(pubKeySpec);

				byte[] privBytes=hexStringToBytes(preferences.getString(PREFERENCES_PRIVATE_KEY, ""));
				PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(privBytes);
				PrivateKey privateKey = rsaFactory.generatePrivate(privKeySpec);
				sKeyPair=new KeyPair(publicKey, privateKey);
				if (V) Log.v(TAG,PREFIX_LOG+"Key pair done.");
			}
			if (V) Log.v(TAG,PREFIX_LOG+"Init key managers..."); 
			getKeyManager();
			if (V) Log.v(TAG,PREFIX_LOG+"Key managers done."); 
			if (editor != null)
			{
				editor.commit();
				dataChanged();
			}

			if (V)
				Log.v(TAG, PREFIX_LOG+'[' + Build.MANUFACTURER + "] deviceId=" + sDeviceId + " name="
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


	@SuppressWarnings("unused")
	public static void enableStrictMode()
	{
		if (D && STRICT_MODE && VERSION.SDK_INT>=VERSION_CODES.GINGERBREAD)
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
		if (VERSION.SDK_INT>=VERSION_CODES.FROYO)
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
    		@Override
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
//			final int v=VERSION.SDK_INT;
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
//			Log.d("INFO","Version.codename:"+VERSION.CODENAME); // v4
//			Log.d("INFO","Version.incremental:"+VERSION.INCREMENTAL);
//			Log.d("INFO","Version.release:"+VERSION.RELEASE);
//			Log.d("INFO","Version.sdk int:"+VERSION.SDK_INT);
//			Log.d("INFO","--------------------------");
//		}		
	}
	@TargetApi(14)
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
				RAApplication.getPreferences();
				return null;
			}
			@Override
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
