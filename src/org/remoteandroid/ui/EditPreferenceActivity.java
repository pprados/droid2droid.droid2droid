package org.remoteandroid.ui;

import static org.remoteandroid.Constants.NFC;
import static org.remoteandroid.Constants.PREFERENCES_ACTIVE;
import static org.remoteandroid.Constants.PREFERENCES_ANO_WIFI_LIST;
import static org.remoteandroid.Constants.PREFERENCES_EXPOSE;
import static org.remoteandroid.Constants.PREFERENCES_NAME;
import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.Constants.TAG_SERVER_BIND;
import static org.remoteandroid.Constants.TIME_TO_DISCOVER;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.ETHERNET;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.NDEF_MIME_TYPE;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.SCHEME_TCP;
import static org.remoteandroid.internal.Constants.TAG_NFC;
import static org.remoteandroid.internal.Constants.TAG_PREFERENCE;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.internal.Constants.W;
import static org.remoteandroid.internal.Constants.WAN;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.remoteandroid.Application;
import org.remoteandroid.NfcUtils;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.discovery.Discover;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.service.RemoteAndroidService;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcEvent;
import android.nfc.NfcManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.SystemClock;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.google.protobuf.InvalidProtocolBufferException;

// TODO: Sur xoom, enlever le menu contextuel
public final class EditPreferenceActivity extends PreferenceActivity 
implements Discover.Listener
{
	private CharSequence[] 	mExposeValues;
	private Boolean[]		mExposeActive;
	
	// No persistante preference
	private static final String PREFERENCES_ANO					="ano";
	private static final String PREFERENCES_SHARE_PROXIMITY		="ano.active";
	private static final String PREFERENCES_SHARE_WIFI			="ano.select_wifi";
//	private static final String PREFERENCES_SHARE_QRCODE		="ano.qrcode";
//	private static final String PREFERENCES_SHARE_NFC			="ano.nfc";
	private static final String PREFERENCES_KNOWN				="known";

	private static final String PREFERENCE_DEVICE_LIST			="lan.list";
	private static final String PREFERENCE_SCAN 				= "scan";

	private static final String ALL_WIFI="#ALL#";

	private SharedPreferences mPreferences;
	private Preference mActive;
	private Preference mExpose;
	private Preference mName;
	private Preference mScan;
	
	private NfcAdapter mNfcAdapter;

	private MultiSelectListPreference mListEthernet;
	private BroadcastReceiver mNetworkStateReceiver=new BroadcastReceiver() 
    {
		
        @Override
        public void onReceive(Context context, Intent intent) 
        {
            if (V) Log.v(TAG_PREFERENCE, PREFIX_LOG+"IP network changed "+intent);
            // deprecated
            // NetworkInfo ni=(NetworkInfo)intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
			if (!ETHERNET) return;
			updateDiscoverExposeButton();
        	ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni=cm.getActiveNetworkInfo();
            if (ni==null)
            {
        		clearEthernet();
            }
            else
            {
	            switch (ni.getType())
	            {
	            	case ConnectivityManager.TYPE_MOBILE:
	            	case ConnectivityManager.TYPE_MOBILE_DUN:
	            	case ConnectivityManager.TYPE_MOBILE_HIPRI:
	            	case ConnectivityManager.TYPE_MOBILE_MMS:
	            	case ConnectivityManager.TYPE_MOBILE_SUPL:
	            	case ConnectivityManager.TYPE_WIMAX:
	            		clearEthernet();
		            	break;
	            	case ConnectivityManager.TYPE_BLUETOOTH:
	            	case ConnectivityManager.TYPE_WIFI:
	            	case ConnectivityManager.TYPE_ETHERNET:
		            	if (ni.getState()==NetworkInfo.State.CONNECTED)
		                {
		            		// TODO: En cas de connexion sur un nouveau WIFI, il y a des restes de la découverte du précédant
		                	// Inform presence if network status change
							if (mListEthernet!=null)
								mListEthernet.setEnabled(false);
					        WifiManager wifiManager=(WifiManager)getSystemService(Context.WIFI_SERVICE);
					        if (wifiManager!=null)
					        {
					        	List<WifiConfiguration> confs=wifiManager.getConfiguredNetworks();
					        	if (confs.size()!=0)
					        	{
						        	ArrayList<CharSequence> names=new ArrayList<CharSequence>();
						        	ArrayList<CharSequence> namesKey=new ArrayList<CharSequence>();
						        	names.add(getResources().getString(R.string.all_key));
						        	namesKey.add(ALL_WIFI);
						        	for (WifiConfiguration conf:confs)
						        	{
						        		String name=conf.SSID;
						        		if (conf.SSID.charAt(0)=='\"')
						        			name=name.substring(1,name.length()-1);
						        		names.add(name);
						        		namesKey.add(name);
						        	}
						        	if (names.size()!=0)
						        	{
							            CharSequence[] entriesArray = new CharSequence[names.size()];
							            names.toArray(entriesArray);
							            CharSequence[] valuesArray = new CharSequence[namesKey.size()];
							            namesKey.toArray(valuesArray);
							            mListEthernet.setEntries(names.toArray(entriesArray));
							        	mListEthernet.setEntryValues(valuesArray);
							        	// FIXME: default value not activated !
							        	mListEthernet.setDefaultValue(Application.getPreferences().getString(PREFERENCE_DEVICE_LIST, ALL_WIFI));
							        	if (mLastValue!=null) 
							        		mListEthernet.setValue(mLastValue);
							        	mListEthernet.setEnabled(true);
						        	}
						        	else
						        		mListEthernet.setEnabled(false); // FIXME: Et si ajout à chaud d'un réseau Wifi alors qu'il n'y en a pas ?
					        	}
					        	else
					        	{
					        		mListEthernet.setEnabled(false);
					        	}
					        }
					        else
					        	mListEthernet.setEnabled(false);
		                }
		            	else
		            	{
		            		clearEthernet();
		            	}
		            	break;
		            default:
		            	if (W) Log.w(TAG_DISCOVERY,PREFIX_LOG+"Unknown network type "+ni.getType());
		            	break;
	            }
            }
        }
    };
    
    private BroadcastReceiver mBluetoothReceiver=new BroadcastReceiver()
    {
		@Override
		public void onReceive(Context context, Intent intent)
		{
            if (W) Log.w(TAG_PREFERENCE, PREFIX_LOG+"BT Type Changed "+intent);
			if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))
			{
		        updateDiscoverExposeButton();
			}
			
		}
    };
	private BroadcastReceiver mNfcReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
            if (W) Log.w(TAG_PREFERENCE, PREFIX_LOG+"NFC Changed "+intent);
	        updateDiscoverExposeButton();
		}
	};
    
	private BroadcastReceiver mAirPlane = new BroadcastReceiver() 
	{
	      @Override
	      public void onReceive(Context context, Intent intent) 
	      {
	            if (D) Log.d(TAG_PREFERENCE, PREFIX_LOG+"Airplane mode changed");
		        updateDiscoverExposeButton();
	      }
	};

	private BroadcastReceiver mRemoteAndroidReceiver=new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			final String action=intent.getAction();
			//if (intent.hasExtra(RemoteAndroidManager.EXTRA_UPDATE))
			{
				
				if (RemoteAndroidManager.ACTION_DISCOVER_ANDROID.equals(action))
				{
					RemoteAndroidInfoImpl info=(RemoteAndroidInfoImpl)intent.getParcelableExtra(RemoteAndroidManager.EXTRA_DISCOVER);
					DevicePreference preference=mDevicePreferenceMap.get(info.getUuid());
					if (preference!=null)
					{
						preference.mInfo.isBonded=info.isBonded;
						if (!info.isRemovable())
						{
							preference.onDeviceAttributesChanges();
						}
						else
						{
							removeInfo(info);
						}
					}
					else if (info.isBonded)
					{
						addInfo(info).onDeviceAttributesChanges();
					}
				}
			}
		}

	};
	
	private Preference mPreferenceScan;
	
    private ProgressGroup mDeviceList;
	private List<RemoteAndroidInfoImpl> mDiscovered;
	
	private HashMap<UUID, DevicePreference> mDevicePreferenceMap = new HashMap<UUID, DevicePreference>();
	
	static class Cache
	{
		String mValue;
		CharSequence[] mEntries;
		CharSequence[] mEntryValues;
		HashMap<UUID,Bundle> mSaved=new HashMap<UUID,Bundle>();
	}
	private String mLastValue;

	private Handler mHandler=new Handler();
	
    void clearEthernet()
    {
		if (mListEthernet!=null)
		{
			ArrayList<DevicePreference> toRemove=new ArrayList<DevicePreference>();
			mListEthernet.setEnabled(false);
			for (DevicePreference preference:mDevicePreferenceMap.values())
			{
				preference.mInfo.isDiscoverEthernet=false;
				preference.mInfo.removeUrisWithScheme(SCHEME_TCP);
				if (!preference.mInfo.isBonded)
				{
					if (!preference.mInfo.isDiscover())
					{
						toRemove.add(preference);
					}
				}
				else
				{
					preference.mInfo.isDiscoverEthernet=false;
					preference.mInfo.removeUrisWithScheme(SCHEME_TCP);
					preference.onDeviceAttributesChanges();
				}
			}
			for (DevicePreference preference:toRemove)
			{
				mDevicePreferenceMap.remove(preference.mInfo.uuid);
				mDeviceList.removePreference(preference);
			}
		}
    }
    
	public Object onRetainNonConfigurationInstance() 
	{
		Cache cache=new Cache();
		if (ETHERNET)
		{
			cache.mValue=mListEthernet.getValue();
			cache.mEntries=mListEthernet.getEntries();
			cache.mEntryValues=mListEthernet.getEntryValues();
		}
		for (UUID uuid:mDevicePreferenceMap.keySet())
		{
			DevicePreference pref=mDevicePreferenceMap.get(uuid);
			Bundle b=new Bundle();
			pref.saveHierarchyState(b);
			cache.mSaved.put(uuid, b);
		}
		return cache;
	}
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setTitle(R.string.app_name);
		
		getPreferenceManager().setSharedPreferencesName(Application.sDeviceId);
		final Intent intent=getIntent();
		initSync(intent);
		
		nfcExpose();
        // Register callback
		
        onDiscoverStart();
		new Thread()
		{
			public void run() 
			{
				initAsync(intent);
			}
		}.start();
	}
	
	private void nfcExpose()
	{
		// Check for available NFC Adapter
		if (NFC && Build.VERSION.SDK_INT>=Build.VERSION_CODES.ICE_CREAM_SANDWICH)
		{
			mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
	        if (mNfcAdapter != null) 
	        {
	        	mNfcAdapter.setNdefPushMessageCallback(new CreateNdefMessageCallback()
	        	{

					@Override
					public NdefMessage createNdefMessage(NfcEvent event)
					{
						return NfcUtils.createNdefMessage(
							EditPreferenceActivity.this,Trusted.getInfo(EditPreferenceActivity.this));
					}
	        		
	        	}, this);
	        }
		}
	}
    private void nfcUnregister()
    {
    	if (NFC && mNfcAdapter!=null)
    	{
    		mNfcAdapter.disableForegroundDispatch(this);
    	}
    }
	
    @Override
    public void onNewIntent(Intent intent) 
    {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }
	// Initialisation synchrone
	private void initSync(Intent intent)
	{
        // Main
		addPreferencesFromResource(R.xml.all_preferences);
		Discover.getDiscover().registerListener(this);

		final Intent intentRemoteContext=new Intent(this,RemoteAndroidService.class);
		mExpose=findPreference(PREFERENCES_EXPOSE);
		
		mActive=findPreference(PREFERENCES_ACTIVE);
		mActive.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
		{
			
			@Override
			public boolean onPreferenceChange(Preference preference, final Object newValue)
			{
				Application.clearCookies();
				if (((Boolean)newValue).booleanValue())
				{
					startService(intentRemoteContext);
					delayEnableActive();
				}
				else
				{
					if (!stopService(intentRemoteContext))
						if (E) Log.e(TAG_SERVER_BIND,PREFIX_LOG+"Impossible to stop service");
					delayEnableActive(); 
				}
				mPreferences.edit().putBoolean(PREFERENCES_ACTIVE, ((Boolean)newValue).booleanValue()).commit();
				Application.dataChanged();
				return true;
			}
			// Bug with BT with version GINGERBREAD_MR1
			// if you push quickly on/off
			private void delayEnableActive()
			{
				mActive.setEnabled(false);
				mHandler.postAtTime(new Runnable()
				{
					@Override
					public void run()
					{
						mActive.setEnabled(true);
					}
				}, SystemClock.uptimeMillis()+2000);
			}
		});

		OnPreferenceChangeListener clearCookies=new OnPreferenceChangeListener()
		{
			@Override
			public boolean onPreferenceChange(Preference preference, final Object newValue)
			{
				Application.clearCookies();
				return true;
			}
			
		};
		findPreference(PREFERENCES_SHARE_PROXIMITY).setOnPreferenceChangeListener(clearCookies);
		findPreference(PREFERENCES_SHARE_WIFI).setOnPreferenceChangeListener(clearCookies);
//		findPreference(PREFERENCES_SHARE_QRCODE).setOnPreferenceChangeListener(clearCookies);
//		findPreference(PREFERENCES_SHARE_NFC).setOnPreferenceChangeListener(clearCookies);
		
		// Device name
		mName=findPreference(PREFERENCES_NAME);
		mName.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
		{
			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue)
			{
				String newVal=(String)newValue;
				newVal=newVal.trim();
				if (newVal.length()==0)
					Application.setName(null);
				else
					Application.setName(newVal);
				mName.setSummary(Application.getName());
				Application.dataChanged();
				return true;
			}
		});


		// Scan
		mPreferenceScan=findPreference(PREFERENCE_SCAN);
        mDeviceList = (ProgressGroup) findPreference(PREFERENCE_DEVICE_LIST);
        mScan=findPreference(PREFERENCE_SCAN);

        // Level 11: MultiSelectListPreference
        mListEthernet=(MultiSelectListPreference)findPreference(PREFERENCES_ANO_WIFI_LIST);
        if (ETHERNET)
        {
	        CharSequence[] empty=new CharSequence[0];
	        mListEthernet.setEntries(empty);
	        mListEthernet.setEntryValues(empty);
	        mListEthernet.setEnabled(false);
	        registerForContextMenu(getListView());
        }
        else
        {
        	((PreferenceGroup)findPreference(PREFERENCES_ANO)).removePreference(mListEthernet);
        	mListEthernet=null;
        }

        if (!WAN)
        {
        	Preference mobile=findPreference(PREFERENCES_KNOWN);
        	if (mobile!=null)
        		getPreferenceScreen().removePreference(mobile);
        }
        
        mDeviceList = (ProgressGroup) findPreference(PREFERENCE_DEVICE_LIST);

	}
	// Initialisation asynchrone
	private void initAsync(Intent intent) 
	{
		mPreferences=Application.getPreferences();
		mName.setSummary(Application.getName());
		Application.startService();
		// Device name
		// Scan
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				updateDiscoverExposeButton();
			}
		});


        // Level 11: MultiSelectListPreference
        if (ETHERNET)
        {
	        String curPref=mPreferences.getString(PREFERENCE_DEVICE_LIST, null);
	        if (curPref==null)
	        {
	        	Application.propertyCommit(mPreferences.edit().putString(PREFERENCE_DEVICE_LIST, ALL_WIFI));
	        }
        }

        runOnUiThread(new Runnable()
        {
        	@Override
        	public void run()
        	{
           		finishAsyncInit((Cache)getLastNonConfigurationInstance());
        	}
        });
	}
	private void finishAsyncInit(Cache cache)
	{
		if (cache==null) 
		{
			mDiscovered=new ArrayList<RemoteAndroidInfoImpl>();
	        initBonded();
	        mDeviceList.setProgress(Discover.getDiscover().isDiscovering());
			Discover.getDiscover().registerListener(EditPreferenceActivity.this);
			return;
		}
		mLastValue=cache.mValue;
		if (ETHERNET)
		{
			mListEthernet.setValue(cache.mValue);
			mListEthernet.setEntries(cache.mEntries);
			mListEthernet.setEntryValues(cache.mEntryValues);
		}
		for (UUID uuid:cache.mSaved.keySet())
		{
			
			DevicePreference preference=new DevicePreference(this);
			preference.restoreHierarchyState(cache.mSaved.get(uuid));
			mDevicePreferenceMap.put(preference.mInfo.getUuid(),preference);
			mDeviceList.addPreference(preference);
			mDiscovered.add(preference.mInfo);
			preference.onDeviceAttributesChanges();
		}
	}


	private void initBonded()
    {
    	if (mDeviceList!=null)
    	{
			mDeviceList.removeAll();
			mDiscovered.clear();
			mDevicePreferenceMap.clear();
			for (RemoteAndroidInfo inf:Trusted.getBonded())
			{
				RemoteAndroidInfoImpl info=(RemoteAndroidInfoImpl)inf;
				info.clearDiscover();
				addInfo(info);
			}
    	}
    }
	
   @Override
    protected void onResume() 
    {
       super.onResume();
	   if (V) Log.v(TAG_PREFERENCE,PREFIX_LOG+"onResume()");
        
		// Register receiver
    	if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.ECLAIR)
    	{
	        registerReceiver(mNetworkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
			registerReceiver(mAirPlane,new IntentFilter("android.intent.action.SERVICE_STATE"));
			registerReceiver(mNfcReceiver, new IntentFilter("android.nfc.action.ADAPTER_STATE_CHANGED"));
			IntentFilter filter=new IntentFilter();
			filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
			filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
			registerReceiver(mBluetoothReceiver, filter);
			
			filter=new IntentFilter(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
	//		filter.addAction(RemoteAndroidManager.ACTION_STOP_DISCOVER_ANDROID);
			registerReceiver(
				mRemoteAndroidReceiver, 
				filter,
				RemoteAndroidManager.PERMISSION_DISCOVER_SEND,null
				);
    	}
    	
    	nfcCheckDiscovered();
    }
	private void nfcCheckDiscovered()
	{
		if (Build.VERSION.SDK_INT<Build.VERSION_CODES.HONEYCOMB) return;
		NfcManager nfcManager=(NfcManager)getSystemService(NFC_SERVICE);
		if (NFC && nfcManager!=null)
		{
			mNfcAdapter=nfcManager.getDefaultAdapter();
			if (mNfcAdapter!=null)
			{
				Intent intent=getIntent();
				if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) 
				{
					// Check the caller. Refuse spoof events
					checkCallingPermission("com.android.nfc.permission.NFCEE_ADMIN");
	
					Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			        if (rawMsgs != null) 
			        {
			        	for (int i = 0; i < rawMsgs.length; i++) 
			            {
			        		NdefMessage msg = (NdefMessage) rawMsgs[i];
			        		for (NdefRecord record:msg.getRecords())
			        		{
			        			if ((record.getTnf()==NdefRecord.TNF_MIME_MEDIA) 
			        					&& Arrays.equals(NDEF_MIME_TYPE, record.getType()))
			        			{
			        				try
									{
				        				Messages.BroadcastMsg bmsg=Messages.BroadcastMsg.newBuilder().mergeFrom(record.getPayload()).build();
				        				//if (bmsg.getType()==Messages.BroadcastMsg.Type.CONNECT)
				        				{
											RemoteAndroidInfoImpl info=ProtobufConvs.toRemoteAndroidInfo(this,bmsg.getIdentity());
											info.isDiscoverNFC=true;
											info.isBonded=Trusted.isBonded(info);
											onDiscover(info);
				        				}
//				        				else
//											if (W) Log.d(TAG_NFC,PREFIX_LOG+"Connect tag. Ignore.");
									}
									catch (InvalidProtocolBufferException e) // $codepro.audit.disable logExceptions
									{
										if (W) Log.d(TAG_NFC,PREFIX_LOG+"Invalide data");
									}
			        			}
			        		}
			            }
			        }
			    }
	    		PendingIntent pendingIntent = 
	    				PendingIntent.getActivity(this, 0, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
	    		mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
			}
		}
		
	}

    @Override
    protected void onPause() 
    {
    	super.onPause();
 	   if (V) Log.v(TAG_PREFERENCE,PREFIX_LOG+"onPause()");

		// Unregister the discovery receiver
   		unregisterReceiver(mNetworkStateReceiver);
		unregisterReceiver(mRemoteAndroidReceiver); 
		unregisterReceiver(mBluetoothReceiver); 
		unregisterReceiver(mNfcReceiver);
		unregisterReceiver(mAirPlane); 
		nfcUnregister();
    }
    @Override
    protected void onDestroy()
    {
    	super.onDestroy();
    	if (mDiscovered!=null)
    	{
			Discover.getDiscover().unregisterListener(this);
	    	mDiscovered.clear();
    	}
    }
    @Override
    protected void onUserLeaveHint() 
    {
        super.onUserLeaveHint();
        if (Discover.getDiscover().isDiscovering())
        	Discover.getDiscover().cancelDiscover();
    }
    
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,Preference preference) 
    {

        if (PREFERENCE_SCAN.equals(preference.getKey())) 
        {
        	if (!Discover.getDiscover().isDiscovering())
        	{
        		scan(RemoteAndroidManager.FLAG_ACCEPT_ANONYMOUS);
        	}
            return true;
        }
        else if (preference instanceof DevicePreference) 
        {
            DevicePreference devPreference = (DevicePreference)preference;
            if (!devPreference.mInfo.isBonded) 
            	devPreference.pair();
            return true;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

	@Override
	public void onContentChanged()
	{
		super.onContentChanged();
		if (Compatibility.VERSION_SDK_INT>=Compatibility.VERSION_FROYO) 
		{
			Application.dataChanged();
		}
	}

	@Override
	public void onDiscoverStart() 
	{
		boolean isDiscovering=Discover.getDiscover().isDiscovering();
		mPreferenceScan.setEnabled(!isDiscovering);
		mDeviceList.setProgress(isDiscovering);
	}
	
	@Override
	public void onDiscoverStop() 
	{
		boolean airPlane=Settings.System.getInt(getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
		boolean isDiscovering=!airPlane && Discover.getDiscover().isDiscovering();
		mPreferenceScan.setEnabled(!isDiscovering);
		mDeviceList.setProgress(isDiscovering);
	}
	
	@Override
	public void onDiscover(RemoteAndroidInfoImpl remoteAndroidInfo)
	{
		if (I) Log.i(TAG_PREFERENCE,PREFIX_LOG+"onDiscover "+remoteAndroidInfo.getName());
		DevicePreference preference=mDevicePreferenceMap.get(remoteAndroidInfo.getUuid());
		if (preference==null)
			addInfo(remoteAndroidInfo);
		else
		{
			preference.mInfo.merge(remoteAndroidInfo);
			preference.onDeviceAttributesChanges();
		}
	}

    @Override
    public boolean onContextItemSelected(MenuItem item) 
    {
    	ContextMenuInfo menuInfo=item.getMenuInfo();
        if ((menuInfo == null) || !(menuInfo instanceof AdapterContextMenuInfo)) {
            return false;
        }
        AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
        Preference preference = (Preference) getPreferenceScreen().getRootAdapter().getItem(adapterMenuInfo.position);
        if (preference==null) return false;
        if (preference instanceof DevicePreference)
        {
            return ((DevicePreference)preference).onContextItemSelected(item);
        }
        else
        { // TODO: ajouter message "Associer..."
        	switch (item.getItemId())
        	{
        		case R.id.context_scan_all:
        			scan(0);
        			break;
        		case R.id.context_scan_bt:
        			scan(RemoteAndroidManager.FLAG_NO_ETHERNET);
        			break;
        		case R.id.context_scan_wifi:
        			scan(RemoteAndroidManager.FLAG_NO_BLUETOOTH);
        			break;
        	}
        }
        return false;
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,ContextMenuInfo menuInfo) 
    {
        //For device picker, disable Context Menu
        if ((menuInfo == null) || !(menuInfo instanceof AdapterContextMenuInfo)) 
        {
            return;
        }
        MenuInflater inflater=getMenuInflater();
        AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
        Preference preference = (Preference) getPreferenceScreen().getRootAdapter().getItem(adapterMenuInfo.position);
        if (preference==null) return;
        if (preference instanceof DevicePreference)
        {
            ((DevicePreference)preference).onCreateContextMenu(inflater,menu);
        }
        else
        {
        	if (preference==mScan)
        	{
        		inflater.inflate(R.menu.context_scan, menu);    
        		menu.setHeaderTitle(R.string.scan_context_title);
        	}
        	
        }
    }

	private DevicePreference addInfo(RemoteAndroidInfoImpl info)
	{
		DevicePreference preference=new DevicePreference(this,info);
		mDevicePreferenceMap.put(info.getUuid(),preference);
		mDeviceList.addPreference(preference);
		return preference;
	}
	private void removeInfo(RemoteAndroidInfoImpl info)
	{
		DevicePreference preference=mDevicePreferenceMap.get(info.uuid);
		mDevicePreferenceMap.remove(info.getUuid());
		mDeviceList.removePreference(preference);
	}
	
	private void updateDiscoverExposeButton()
	{
//		new AsyncTask<Void, Void, Boolean>()
//		{
//			@Override
//			protected Boolean doInBackground(Void... params)
//			{
//				ArrayList<CharSequence> a=new ArrayList<CharSequence>();
//				ArrayList<Expose> e=new ArrayList<Expose>();
//				ArrayList<Boolean> act=new ArrayList<Boolean>();
//				final long activeFeature=Application.getActiveFeature();
//				for (int i=0;i<Expose.sExpose.length;++i)
//				{
//					if ((Expose.sExpose[i].mFeature & Application.sFeature) == Expose.sExpose[i].mFeature)
//					{
//						a.add(getString(Expose.sExpose[i].mValue));
//						e.add(Expose.sExpose[i]);
//						act.add((Expose.sExpose[i].mFeature & activeFeature) ==Expose.sExpose[i].mFeature);
//					}
//				}
//				mExposeValues=a.toArray(new CharSequence[a.size()]);
//				mExposeModel=e.toArray(new Expose[e.size()]);
//				mExposeActive=act.toArray(new Boolean[act.size()]);
//
//				int netStatus=NetworkTools.getActiveNetwork(Application.sAppContext);
//				return (netStatus & (ACTIVE_LOCAL_NETWORK|ACTIVE_BLUETOOTH|ACTIVE_NFC|ACTIVE_GLOBAL_NETWORK))!=0;
//			}
//			@Override
//			protected void onPostExecute(Boolean result)
//			{
//				mPreferenceScan.setEnabled(
//					result
//					&& !Discover.getDiscover().isDiscovering());
//				mExpose.setEnabled(result);
//			}
//		}.execute();
	}
	
	private void scan(final int flags)
	{
		if (!Discover.getDiscover().isDiscovering())
		{
			initBonded();
			Application.sThreadPool.execute(new Runnable()
			{
				
				@Override
				public void run()
				{
					Discover.getDiscover().startDiscover(flags,TIME_TO_DISCOVER);
				}
			});
		}
	}
	
}
