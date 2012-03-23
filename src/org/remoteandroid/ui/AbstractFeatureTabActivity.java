package org.remoteandroid.ui;

import static org.remoteandroid.Constants.NDEF_MIME_TYPE;
import static org.remoteandroid.Constants.NFC;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.AbstractBodyFragment.OnNfcEvent;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcEvent;
import android.nfc.Tag;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;



public abstract class AbstractFeatureTabActivity extends AbstractNetworkEventActivity
{
	
	protected NfcAdapter mNfcAdapter;
	protected ViewPager  mViewPager;
	protected TabsAdapter mTabsAdapter;
	protected FragmentManager mFragmentManager;
	protected ActionBar mActionBar;
    
	protected abstract FeatureTab[] getFeatureTabs();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
		requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_CONTEXT_MENU);
        
        super.onCreate(savedInstanceState);

    	mFragmentManager = getSupportFragmentManager();

    	mViewPager = new ViewPager(this);
        mViewPager.setId(R.id.body);
        setContentView(mViewPager);
    	
        mActionBar = getSupportActionBar();
        if (mActionBar!=null)
        {
	        mActionBar.setDisplayHomeAsUpEnabled(true);
	        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
//	        mActionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
	        mActionBar.setDisplayShowTitleEnabled(true);
        }        
        mTabsAdapter = new TabsAdapter(this, mActionBar, mViewPager);
        FeatureTab[] featureTabs=getFeatureTabs();
    	for (int i=0;i<featureTabs.length;++i)
    	{
    		if ((featureTabs[i].mFeature & Application.sFeature) == featureTabs[i].mFeature)
    		{
    			featureTabs[i].createTab(mTabsAdapter,mActionBar);
    		}
    	}
        if (savedInstanceState != null) 
        {
        	getSupportActionBar().setSelectedNavigationItem(savedInstanceState.getInt("index"));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) 
    {
        super.onSaveInstanceState(outState);
        outState.putInt("index", getSupportActionBar().getSelectedNavigationIndex());
    }
	
    @Override
    protected void onResume()
    {
    	super.onResume();
    	Application.hideSoftKeyboard(this);
		registerOnNfcTag();
    }
	@Override
	protected void onPause()
	{
		super.onPause();
		unregisterOnNFCTag();
	}
    
	// Invoked when NFC tag detected
	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		setIntent(intent);
		final Tag tag=(Tag)intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
		if (tag!=null)
		{
			// Check the caller. Refuse spoof events
			checkCallingPermission("com.android.nfc.permission.NFCEE_ADMIN");

			onNfcTag(tag);
			
			for (int i=0;i<mTabsAdapter.getCount();++i)
			{
				AbstractBodyFragment fragment=(AbstractBodyFragment)mTabsAdapter.getItem(i);
				if (fragment instanceof OnNfcEvent)
				{
					mTabsAdapter.onPageSelected(i);
					((OnNfcEvent)fragment).onNfcTag(intent);
				}
			}
		}
	}

	protected void onNfcTag(Tag tag)
	{
		
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getSupportMenuInflater();
		// FIXME: Menu different en cas de connect ?
		inflater.inflate(R.menu.main_fragment_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				// app icon in action bar clicked; go home
				Intent intent = new Intent(this, MainActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
				return true;
			case R.id.config:
				startActivity(new Intent(this, EditPreferenceActivity.class));
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	@Override
	protected AbstractBodyFragment getActiveFragment()
	{
		return mTabsAdapter.getActiveFragment();
//		return (AbstractBodyFragment)mTabsAdapter.getItem(mActionBar.getSelectedNavigationIndex());
	}	
	
	// Register a listener when another device ask my tag
	protected void nfcExpose()
	{
		if (NFC && Build.VERSION.SDK_INT>=Build.VERSION_CODES.GINGERBREAD)
		{
			mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
	        if (mNfcAdapter != null) 
	        {
	        	mNfcAdapter.setNdefPushMessageCallback(new CreateNdefMessageCallback()
	        	{

					@Override
					public NdefMessage createNdefMessage(NfcEvent event)
					{
						return AbstractFeatureTabActivity.createNdefMessage(
							AbstractFeatureTabActivity.this,Trusted.getInfo(AbstractFeatureTabActivity.this),
							true); // Expose
					}
	        		
	        	}, this);
	        }
		}
	}
	
	protected void registerOnNfcTag()
	{
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.GINGERBREAD)
		{
			NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
			if (NFC && mNfcAdapter!=null)
			{
				PendingIntent pendingIntent = 
						PendingIntent.getActivity(this, 0, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
				mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
			}
		}
	}
	// Unregister the exposition of my tag
    protected void unregisterOnNFCTag()
    {
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.GINGERBREAD)
		{
	    	if (NFC && mNfcAdapter!=null)
	    	{
	    		mNfcAdapter.disableForegroundDispatch(this);
	    	}
		}
    }
    
	public static NdefMessage createNdefMessage(Context context,RemoteAndroidInfo info,boolean expose)
	{
		Messages.BroadcastMsg.Builder broadcastBuilder = Messages.BroadcastMsg.newBuilder();
		Messages.BroadcastMsg msg=broadcastBuilder
			.setType(expose ? Messages.BroadcastMsg.Type.EXPOSE : Messages.BroadcastMsg.Type.CONNECT)
			.setIdentity(ProtobufConvs.toIdentity(info))
			.build();
		byte[] payload=msg.toByteArray();
		return new NdefMessage(
			new NdefRecord[]
			{
				NdefRecord.createApplicationRecord("org.remoteandroid"),
				new NdefRecord(NdefRecord.TNF_MIME_MEDIA, NDEF_MIME_TYPE, new byte[0], payload),
//				NdefRecord.createUri("www.remotandroid.org")
			}
		);
		
	}
	
	
	
	
}
