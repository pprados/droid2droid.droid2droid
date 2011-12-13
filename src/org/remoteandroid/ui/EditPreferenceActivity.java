package org.remoteandroid.ui;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.NetworkTools.*;
import static org.remoteandroid.Constants.PREFERENCES_ANO_WIFI_LIST;
import static org.remoteandroid.Constants.PREFERENCES_IN_ONE_SCREEN;
import static org.remoteandroid.Constants.PREFERENCES_NAME;
import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.Constants.TIME_TO_DISCOVER;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.ETHERNET;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_PREFERENCE;
import static org.remoteandroid.internal.Constants.W;
import static org.remoteandroid.internal.Constants.WAN;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.remoteandroid.Application;
import org.remoteandroid.ListRemoteAndroidInfo;
import org.remoteandroid.NetworkTools;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.ListRemoteAndroidInfoImpl;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.service.RemoteAndroidBackup;
import org.remoteandroid.service.RemoteAndroidService;
import org.remoteandroid.ui.expose.Expose;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.TextView;

// TODO: Sur xoom, enlever le menu contextuel
public class EditPreferenceActivity extends PreferenceActivity implements ListRemoteAndroidInfo.DiscoverListener
{
	private static final String ACTION_LAN="org.remoteandroid.action.LAN";
	private static final String ACTION_WAN="org.remoteandroid.action.WAN";
	
	private static final int MODE_MAIN_SCREEN=1 << 0;
	private static final int MODE_ANO_SCREEN=1 << 1;
	private static final int MODE_KNOW_SCREEN=1 << 2;
	private static final int MODE_ONE_SCREEN=MODE_MAIN_SCREEN|MODE_ANO_SCREEN|MODE_KNOW_SCREEN;
	
	private Expose[] 		mExposeModel;
	private CharSequence[] 	mExposeValues;
	private Boolean[]		mExposeActive;
	
	private int mMode;

	// No persistante preference
	private static final String PREFERENCES_ANO					="ano";
	private static final String PREFERENCES_KNOWN				="known";

	private static final String PREFERENCE_DEVICE_LIST			="lan.list";
	private static final String PREFERENCE_EXPOSE 				= "expose";
	private static final String PREFERENCE_SCAN 				= "scan";

	private static final String ALL_WIFI="#ALL#";

	private int mScreenType;
	private SharedPreferences mPreferences;
	private Preference mActive;
	private Preference mExpose;
	private Preference mName;
	private Preference mScan;
	
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
	            		clearEthernet();
		            	break;
		            default:
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
	            		
	            }
            }
        }
    };
    
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
	private BroadcastReceiver mAirPlaine = new BroadcastReceiver() 
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
			if (intent.hasExtra(RemoteAndroidManager.EXTRA_UPDATE))
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
	private ListRemoteAndroidInfoImpl mDiscovered;
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
	private void onRestoreRetainNonConfigurationInstance(Cache cache)
	{
		if (cache==null) 
		{
	        initBonded();
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
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setTitle(R.string.app_name);
		
		getPreferenceManager().setSharedPreferencesName(Application.sDeviceId);
		final Intent intent=getIntent();
		checkMode(intent);
		initSync(intent);
		initAsync(intent);
	}

	// Initialisation synchrone
	private void initSync(Intent intent)
	{
        // Main
		if (PREFERENCES_IN_ONE_SCREEN) // FIXME
		{
			addPreferencesFromResource(R.xml.all_preferences);
		}
		else
		{
			switch (mMode)
			{
				case MODE_MAIN_SCREEN:
					addPreferencesFromResource(R.xml.main_preferences);
					break;
				case MODE_ANO_SCREEN:
					addPreferencesFromResource(R.xml.ano_preferences);
					break;
				case MODE_KNOW_SCREEN:
					addPreferencesFromResource(R.xml.know_preferences);
					break;
			}
		}

		final Intent intentRemoteContext=new Intent(this,RemoteAndroidService.class);
		mExpose=findPreference(PREFERENCES_EXPOSE);
		
		mActive=findPreference(PREFERENCES_ACTIVE);
		mActive.setOnPreferenceChangeListener(new OnPreferenceChangeListener()
		{
			
			@Override
			public boolean onPreferenceChange(Preference preference, final Object newValue)
			{
				if (((Boolean)newValue).booleanValue())
				{
					Application.sThreadPool.execute(new Runnable()
					{
						public void run()
						{
							startService(intentRemoteContext);
						}
					});
					delayEnableActive();
				}
				else
				{
					Application.sThreadPool.execute(new Runnable()
					{
						public void run()
						{
							stopService(intentRemoteContext);
						}
					});
					delayEnableActive(); 
				}
				Application.sThreadPool.execute(new Runnable()
				{
					public void run()
					{
						mPreferences.edit().putBoolean(PREFERENCES_ACTIVE, ((Boolean)newValue).booleanValue()).commit();
					}
				});
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

		// Device name
		if ((mMode & MODE_MAIN_SCREEN)!=0)
		{
			mName=findPreference(PREFERENCES_NAME);
			mName.setSummary(Application.getName());
			findPreference(PREFERENCES_NAME).setOnPreferenceChangeListener(new OnPreferenceChangeListener()
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
					return true;
				}
			});


			// Scan
			mPreferenceScan=findPreference(PREFERENCE_SCAN);
	        mDeviceList = (ProgressGroup) findPreference(PREFERENCE_DEVICE_LIST);
	        mScan=findPreference(PREFERENCE_SCAN);
		}
        if ((mMode & MODE_ANO_SCREEN)!=0)
        {
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
        }

        if ((mMode & MODE_KNOW_SCREEN)!=0)
        {
            if (!WAN)
            {
            	Preference mobile=findPreference(PREFERENCES_KNOWN);
            	if (mobile!=null)
            		getPreferenceScreen().removePreference(mobile);
            }
        }
        
        mDeviceList = (ProgressGroup) findPreference(PREFERENCE_DEVICE_LIST);
		mDiscovered=new ListRemoteAndroidInfoImpl(Application.sManager, null);

	}
	// Initialisation assynchrone (StrictMode)
	private void initAsync(Intent intent) // TODO: en ASYNC
	{
		mPreferences=Application.getPreferences();
		final boolean active=mPreferences.getBoolean(PREFERENCES_ACTIVE, false);
		final Intent intentRemoteContext=new Intent(this,RemoteAndroidService.class);
		final ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
		final List<ActivityManager.RunningServiceInfo> services=am.getRunningServices(100);
		final ComponentName name=new ComponentName(this,RemoteAndroidService.class);
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
				Application.sThreadPool.execute(new Runnable()
				{
					
					@Override
					public void run()
					{
						if (startService(intentRemoteContext)==null)
						{
							if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"Impossible to start the service");
							// TODO
						}
					}
				});
			}
		}
		// Device name
		if ((mMode & MODE_MAIN_SCREEN)!=0)
		{
			// Scan
			runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					updateDiscoverExposeButton();
				}
			});
		}


        if ((mMode & MODE_ANO_SCREEN)!=0)
        {
	        // Level 11: MultiSelectListPreference
	        if (ETHERNET)
	        {
		        String curPref=mPreferences.getString(PREFERENCE_DEVICE_LIST, null);
		        if (curPref==null)
		        {
		        	Application.propertyCommit(mPreferences.edit().putString(PREFERENCE_DEVICE_LIST, ALL_WIFI));
		        }
	        }
        }

   		onRestoreRetainNonConfigurationInstance((Cache)getLastNonConfigurationInstance());
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
				addInfo((RemoteAndroidInfoImpl)info);
			}
    	}
    }
	
	private void checkMode(Intent intent)
	{
		String action=intent.getAction();
		if (PREFERENCES_IN_ONE_SCREEN)
			mMode=MODE_ONE_SCREEN;
		else
		{
			if (ACTION_LAN.equals(action))
			{
				mMode=MODE_ANO_SCREEN;
			}
			else if (ACTION_WAN.equals(action))
			{
				mMode=MODE_KNOW_SCREEN;
			}
			else
				mMode=MODE_MAIN_SCREEN;
		}
	}
	
   @Override
    protected void onResume() 
    {
       super.onResume();
	   if (V) Log.v(TAG_PREFERENCE,PREFIX_LOG+"onResume()");
       if ((mMode & MODE_MAIN_SCREEN)!=0)
       {
        	new AsyncTask<Void, Void, Boolean>()
        	{
        		protected Boolean doInBackground(Void... paramArrayOfParams) 
        		{
        			return Application.sManager.isDiscovering();
        		}
        		protected void onPostExecute(Boolean result) 
        		{
        	        mDeviceList.setProgress(result);
        	    	mDiscovered.setListener(EditPreferenceActivity.this);
        	        onDiscoverStart();
        		}
        	}.execute();
        }
        
		// Register receiver
        registerReceiver(mNetworkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		registerReceiver(mAirPlaine,new IntentFilter("android.intent.action.SERVICE_STATE"));
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

    @Override
    protected void onPause() 
    {
    	super.onPause();
 	   if (V) Log.v(TAG_PREFERENCE,PREFIX_LOG+"onPause()");

    	if (mDiscovered!=null)
    	{
	    	mDiscovered.setListener(null);
	    	mDiscovered.clear();
    	}
		// Unregister the discovery receiver
   		unregisterReceiver(mNetworkStateReceiver);
		unregisterReceiver(mRemoteAndroidReceiver); 
		unregisterReceiver(mBluetoothReceiver); 
		unregisterReceiver(mAirPlaine); 
    }
    
    @Override
    protected void onUserLeaveHint() 
    {
        super.onUserLeaveHint();
        if (Application.sManager.isDiscovering())
        	Application.sManager.cancelDiscover();
    }
    
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,Preference preference) 
    {

        if (PREFERENCE_SCAN.equals(preference.getKey())) 
        {
        	if (!Application.sManager.isDiscovering())
        	{
        		scan(RemoteAndroidManager.FLAG_ACCEPT_ANONYMOUS);
        	}
            return true;
        }
        if (PREFERENCE_EXPOSE.equals(preference.getKey()))
        {
        	expose();
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
			new Runnable()
			{
				
				@Override
				public void run()
				{
					RemoteAndroidBackup.dataChanged();
				}
			}.run();
		}
	}

	@Override
	public void onDiscoverStart() 
	{
		if ((mMode & MODE_MAIN_SCREEN)!=0)
		{
			boolean isDiscovering=Application.sManager.isDiscovering();
			mPreferenceScan.setEnabled(!isDiscovering);
			mDeviceList.setProgress(isDiscovering);
		}
	}
	
	@Override
	public void onDiscoverStop() 
	{
		if ((mMode & MODE_MAIN_SCREEN)!=0)
		{
			try
			{
				boolean airPlane=Settings.System.getInt(getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
				boolean isDiscovering=!airPlane && Application.sManager.isDiscovering();
				mPreferenceScan.setEnabled(!isDiscovering);
				mDeviceList.setProgress(isDiscovering);
			} catch (Throwable e)
			{
				e.printStackTrace(); // FIXME
			}
		}
	}
	
	@Override
	public void onDiscover(RemoteAndroidInfo info, boolean update)
	{
		RemoteAndroidInfoImpl remoteAndroidInfo=(RemoteAndroidInfoImpl)info;
		if (I) Log.i(TAG_PREFERENCE,PREFIX_LOG+"onDiscover "+remoteAndroidInfo.getName());
		DevicePreference preference=mDevicePreferenceMap.get(info.getUuid());
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
		ArrayList<CharSequence> a=new ArrayList<CharSequence>();
		ArrayList<Expose> e=new ArrayList<Expose>();
		ArrayList<Boolean> act=new ArrayList<Boolean>();
		final int activeFeature=Application.getActiveFeature();
		for (int i=0;i<Expose.sExpose.length;++i)
		{
			if ((Expose.sExpose[i].mFeature & Application.sFeature) == Expose.sExpose[i].mFeature)
			{
				a.add(getString(Expose.sExpose[i].mValue));
				e.add(Expose.sExpose[i]);
				act.add((Expose.sExpose[i].mFeature & activeFeature) ==Expose.sExpose[i].mFeature);
			}
		}
		mExposeValues=a.toArray(new CharSequence[a.size()]);
		mExposeModel=e.toArray(new Expose[e.size()]);
		mExposeActive=act.toArray(new Boolean[act.size()]);

		int netStatus=NetworkTools.getActiveNetwork();
		boolean samp=(netStatus & (ACTIVE_LOCAL_NETWORK|ACTIVE_BLUETOOTH|ACTIVE_NFC|ACTIVE_GLOBAL_NETWORK))!=0;
		mPreferenceScan.setEnabled(
			samp
			&& !Application.sManager.isDiscovering());
		mExpose.setEnabled(samp);
		
	}
	private void scan(final int flags)
	{
		if (!Application.sManager.isDiscovering())
		{
			initBonded();
			Application.sThreadPool.execute(new Runnable()
			{
				
				@Override
				public void run()
				{
					Application.sManager.startDiscover(flags,TIME_TO_DISCOVER);
				}
			});
		}
	}
	private void expose()
	{
		ArrayAdapter<CharSequence> adapter=new ArrayAdapter<CharSequence>(this,android.R.layout.simple_dropdown_item_1line,mExposeValues)
				{
					@Override
					public View getView(int position, View convertView, ViewGroup parent)
					{
						// TODO Auto-generated method stub
						View v=super.getView(position, convertView, parent);
						((TextView)v.findViewById(android.R.id.text1)).setEnabled(mExposeActive[position]);
						return v;
					}
				};
		new AlertDialog.Builder(this)
			.setAdapter(adapter, new DialogInterface.OnClickListener()
			{
				
				@Override
				public void onClick(DialogInterface dialog, int which)
				{
					if (mExposeActive[which])
						mExposeModel[which].startExposition(EditPreferenceActivity.this);
				}
			})
			.setTitle(R.string.connect_expose_title)
			.create()
			.show();

		
	}
}
