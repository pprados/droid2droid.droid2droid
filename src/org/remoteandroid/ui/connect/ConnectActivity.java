package org.remoteandroid.ui.connect;

import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.TAG_NFC;

import org.remoteandroid.R;
import org.remoteandroid.RAApplication;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.discovery.Discover;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.ui.AbstractFeatureTabActivity;
import org.remoteandroid.ui.FeatureTab;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;


public final class ConnectActivity extends AbstractFeatureTabActivity
{
	public static final String EXTRA_INFO="rainfo";
	
	public static boolean sIsConnect=false;
	private boolean mIsLight;
	private int mDisplaySet;
	protected int mFlags;
	/*package*/ boolean mBroadcast;
	
	// To broadcast my infos
	private static final FeatureTab[] sTabsBroadcast=
		{
			new ConnectDiscoverFragment.Provider(),
			new ConnectSMSFragment.Provider(), 
	//			new ConnectQRCodeFragment.Provider(), 
//			new ConnectSoundFragment.Provider(),
//			new ConnectWifiDirectFragment.Provider(),
			new ConnectNFCFragment.Provider(), 
//			new ConnectBumpFragment.Provider(), 
			new ConnectTicketFragment.Provider(), 
		};	
	private static final FeatureTab[] sTabsConnect=
		{
			new ConnectDiscoverFragment.Provider(),
			new ConnectSMSFragment.Provider(), 
	//			new ConnectQRCodeFragment.Provider(), 
//			new ConnectSoundFragment.Provider(),
//			new ConnectWifiDirectFragment.Provider(),
			new ConnectNFCFragment.Provider(), 
//			new ConnectBumpFragment.Provider(), 
//			new ConnectArroundFragment.Provider(), // Retourner un essemble de info
			new ConnectTicketFragment.Provider(), 
		};	

	private final Discover.Listener mDiscover=new Discover.Listener()
	{
		
		@Override
		public void onDiscoverStop()
		{
		}
		
		@Override
		public void onDiscoverStart()
		{
		}
		
		@Override
		public void onDiscover(RemoteAndroidInfoImpl info)
		{
			if (info.isDiscoverByNFC)
			{
				if (D) Log.d(TAG_NFC,"Discover NFC info");
				ConnectActivity.this.onDiscover(info);
			}
		}
	};
	
	@Override
	protected FeatureTab[] getFeatureTabs()
	{
		return (mBroadcast) ? sTabsBroadcast : sTabsConnect;
	}
	
	public boolean isBroadcast()
	{
		return mBroadcast;
	}

//	private Resources.Theme	mTheme	= null;
//	private int				mThemeResource;
//	private Resources		mResources;
//
//	@Override
//	public Resources.Theme getTheme()
//	{
//		if (!isBroadcast())
//		{
//			if (mTheme == null)
//			{
//				if (mThemeResource == 0)
//				{
//					mTheme = super.getTheme();
//					return mTheme;
//				}
//				mTheme = mResources.newTheme();
//				mTheme.applyStyle(mThemeResource, true);
//			}
//			return mTheme;
//		}
//		else
//			return super.getTheme();
//	}
//
//	@Override
//	public void setTheme(int resid)
//	{
//		if (isBroadcast())
//			super.setTheme(resid);
//		else
//			mThemeResource = resid;
//	}
	boolean isLight()
	{
		return mIsLight;
	}
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		mBroadcast=!RemoteAndroidManager.ACTION_CONNECT_ANDROID.equals(getIntent().getAction());
		final Intent intent=getIntent();
 		String title=null;
		String subtitle=null;
		int themeId=0;
		Drawable icon=null;
		ComponentName calling=getCallingActivity();
		Resources resources=null;
		ApplicationInfo appInfo=null;
		mFlags = intent.getIntExtra(RemoteAndroidManager.EXTRA_FLAGS,0);

		mDisplaySet=ActionBar.DISPLAY_HOME_AS_UP|ActionBar.DISPLAY_SHOW_HOME|ActionBar.DISPLAY_SHOW_TITLE|ActionBar.DISPLAY_USE_LOGO;
		int displayMask=ActionBar.DISPLAY_HOME_AS_UP|ActionBar.DISPLAY_SHOW_HOME|ActionBar.DISPLAY_SHOW_TITLE|ActionBar.DISPLAY_USE_LOGO;
		if (!isBroadcast())
		{
			try
			{
				PackageManager pm=getPackageManager();
				appInfo=pm.getApplicationInfo(calling.getPackageName(), 0);
				resources= pm.getResourcesForApplication(appInfo);

				// TODO: Check all theme
				themeId = intent.getIntExtra(RemoteAndroidManager.EXTRA_THEME_ID,0);
				switch (themeId)
				{
					case android.R.style.Theme_Black:
					case android.R.style.Theme_Holo:
					case android.R.style.Theme_DeviceDefault:
						themeId=R.style.Theme_Sherlock;
						break;
						
					case android.R.style.Theme_Black_NoTitleBar:
					case android.R.style.Theme_Black_NoTitleBar_Fullscreen:
						themeId=R.style.Theme_Sherlock;
						mDisplaySet=0;
						break;

					case android.R.style.Theme_NoTitleBar:
					case android.R.style.Theme_NoTitleBar_Fullscreen:
						themeId=R.style.Theme_Sherlock;
						mDisplaySet=0;
						break;
						
					case android.R.style.Theme_NoTitleBar_OverlayActionModes:
						themeId=R.style.Theme_Sherlock_ForceOverflow;
						mDisplaySet=0;
						break;
						
//					case android.R.style.Theme_NoActionBar:
					case android.R.style.Theme_DeviceDefault_NoActionBar:
					case android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen:
						themeId=R.style.Theme_Sherlock;
						mDisplaySet=0;
						break;

					case android.R.style.Theme_Light:
					case android.R.style.Theme_Holo_Light:
					case android.R.style.Theme_DeviceDefault_Light:
						themeId=R.style.Theme_Sherlock_Light;
						mIsLight=true;
						break;

//					case android.R.style.Theme_Light_DarkActionBar:
					case android.R.style.Theme_Holo_Light_DarkActionBar:
					case android.R.style.Theme_DeviceDefault_Light_DarkActionBar:
						themeId=R.style.Theme_Sherlock_Light_DarkActionBar;
						mIsLight=true;
						break;
						
					case android.R.style.Theme_Holo_Light_NoActionBar:
					case android.R.style.Theme_DeviceDefault_Light_NoActionBar:
					case android.R.style.Theme_DeviceDefault_Light_NoActionBar_Fullscreen:
					case android.R.style.Theme_Light_NoTitleBar:
						themeId=R.style.Theme_Sherlock_Light;
						mDisplaySet=0;
						mIsLight=true;
						break;
						
//					case android.R.style.Theme_NoDisplay:
//					case android.R.style.Theme_Holo_Light_Dialog:
//					case android.R.style.Theme_DeviceDefault_Light_Dialog:
//						
//					case android.R.style.Theme_Holo_Light_Dialog_MinWidth:
//					case android.R.style.Theme_DeviceDefault_Light_Dialog_MinWidth:
//						
//					case android.R.style.Theme_Holo_Light_Dialog_NoActionBar:
//					case android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar:
//						
//					case android.R.style.Theme_Holo_Light_Dialog_NoActionBar_MinWidth:
//					case android.R.style.Theme_DeviceDefault_Light_Dialog_NoActionBar_MinWidth:
//						
//					case android.R.style.Theme_Holo_Light_DialogWhenLarge:
//					case android.R.style.Theme_DeviceDefault_Light_DialogWhenLarge:
//						
//					case android.R.style.Theme_Holo_Light_DialogWhenLarge_NoActionBar:
//					case android.R.style.Theme_DeviceDefault_Light_DialogWhenLarge_NoActionBar:
						
//						case android.R.style.Theme_Dialog:
//						case android.R.style.Theme_Holo_Dialog:
//						case android.R.style.Theme_DeviceDefault_Dialog:
//							themeId=R.style.Theme_Sherlock_Dialog;
//							break;
						
//						case android.R.style.Theme_Holo_Dialog_MinWidth:
//						case android.R.style.Theme_DeviceDefault_Dialog_MinWidth:
//							break;

//						case android.R.style.Theme_Holo_Dialog_NoActionBar:
//						case android.R.style.Theme_DeviceDefault_Dialog_NoActionBar:
//							break;
						
//						case android.R.style.Theme_Holo_Dialog_NoActionBar_MinWidth:
//						case android.R.style.Theme_DeviceDefault_Dialog_NoActionBar_MinWidth:
//							break;
						
//						case android.R.style.Theme_Holo_DialogWhenLarge:
//						case android.R.style.Theme_DeviceDefault_DialogWhenLarge:
//							break;
						
//						case android.R.style.Theme_Holo_DialogWhenLarge_NoActionBar:
//						case android.R.style.Theme_DeviceDefault_DialogWhenLarge_NoActionBar:
//							break;
						
//						case android.R.style.Theme_Holo_InputMethod:
//						case android.R.style.Theme_DeviceDefault_InputMethod:
//							break;
							
							
//					case android.R.style.Theme_Panel:
//					case android.R.style.Theme_DeviceDefault_Panel:

//					case android.R.style.Theme_Light_Panel:
//					case android.R.style.Theme_DeviceDefault_Light_Panel:
						
//					case android.R.style.Theme_InputMethod:
						
//					case android.R.style.Theme_Translucent:
//					case android.R.style.Theme_Translucent_NoTitleBar:
//					case android.R.style.Theme_Translucent_NoTitleBar_Fullscreen:

//					case android.R.style.Theme_Wallpaper:
//					case android.R.style.Theme_DeviceDefault_Wallpaper:
						
//					case android.R.style.Theme_Wallpaper_NoTitleBar:
//					case android.R.style.Theme_DeviceDefault_Wallpaper_NoTitleBar:
						
//					case android.R.style.Theme_Wallpaper_NoTitleBar_Fullscreen:
						
//					case android.R.style.Theme_WallpaperSettings:
						
//					case android.R.style.Theme_Light_WallpaperSettings:
					
				}
				setTheme(themeId);
				title=intent.getStringExtra(RemoteAndroidManager.EXTRA_TITLE);
				subtitle=intent.getStringExtra(RemoteAndroidManager.EXTRA_SUBTITLE);
				icon=pm.getActivityIcon(calling);
				if (icon==null)
					icon=pm.getApplicationIcon(appInfo);
				int iconId = intent.getIntExtra(RemoteAndroidManager.EXTRA_ICON_ID,0);
				if (iconId!=0)
					icon=resources.getDrawable(iconId);
		
			}
			catch (NameNotFoundException e)
			{
				// Ignore
			}
		}
		if ((mDisplaySet & ActionBar.DISPLAY_SHOW_TITLE)!=0)
			requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
		getSupportActionBar().setDisplayOptions(mDisplaySet,displayMask);
		ActionBar actionBar=getSupportActionBar();
		if (title==null && appInfo!=null)
		{
			if (appInfo.labelRes!=0)
				title=resources.getString(appInfo.labelRes);
			else
				title=appInfo.name;
		}
		if (!isBroadcast() && resources!=null)
		{
			if (title!=null)
				setTitle(title);
			else
				setTitle(R.string.connect);
			if (actionBar!=null && subtitle!=null)
				actionBar.setSubtitle(subtitle);
			if (actionBar!=null && icon!=null)
				actionBar.setLogo(icon);
		}
		RAApplication.startService();
		RemoteAndroidInfoImpl info=getIntent().getParcelableExtra(EXTRA_INFO);
		if (info!=null)
		{
			onDiscover(info);
		}
		
	}
	@Override
	protected void onStart()
	{
		super.onStart();
		// Register listener for NFC
		Discover.getDiscover().registerListener(mDiscover);
	}
	@Override
	protected void onStop()
	{
		super.onStop();
		// UnRegister listener for NFC
		Discover.getDiscover().unregisterListener(mDiscover);
	}
	@Override
	protected void onResume()
	{
		super.onResume();
		sIsConnect=!mBroadcast;

//		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()))
//		{
//			mNfcIntegration.onNewIntent(ConnectActivity.this, intent);
//		}
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		if (((mDisplaySet & ActionBar.DISPLAY_SHOW_TITLE)!=0) && isBroadcast())
		{
			return super.onCreateOptionsMenu(menu);
		}
		return false;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				if (!isBroadcast())
				{
					finish();
					return true;
				}
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	public void onConnected(RemoteAndroidInfoImpl info)
	{
		if (info!=null)
		{
			Intent result=new Intent();
			result.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
			setResult(RESULT_OK,result);
		}
		sIsConnect=false;
		finish();
	}
}
