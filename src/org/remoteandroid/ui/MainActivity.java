package org.remoteandroid.ui;


import java.io.File;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.RemoteAndroidManager.FLAG_ACCEPT_ANONYMOUS;
import static org.remoteandroid.RemoteAndroidManager.FLAG_PROPOSE_PAIRING;
import static org.remoteandroid.internal.Constants.*;

import org.remoteandroid.Application;
import org.remoteandroid.NfcUtils;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.RemoteAndroidNfcHelper;
import org.remoteandroid.RemoteAndroidNfcHelper.OnNfcDiscover;
import org.remoteandroid.internal.RemoteAndroidManagerImpl;
import org.remoteandroid.internal.RemoteAndroidNfcHelperImpl;
import org.remoteandroid.ui.connect.ConnectActivity;
import org.remoteandroid.ui.expose.ExposeActivity;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.util.Log;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import com.actionbarsherlock.widget.ShareActionProvider;


// TODO: expose NFC tag
public final class MainActivity extends SherlockFragmentActivity
implements MainFragment.CallBack,OnNfcDiscover
{
	private RemoteAndroidNfcHelper mNfcIntegration;
	private FragmentManager	mFragmentManager;
	private MainFragment	mFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_CONTEXT_MENU);
		super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);
		setProgressBarIndeterminateVisibility(Boolean.FALSE); // Important: Use Boolean value !
		mFragmentManager = getSupportFragmentManager(); // getSupportFragmentManager();
		mFragment = (MainFragment) mFragmentManager.findFragmentById(R.id.fragment);
		Application.startService();
		mNfcIntegration=new RemoteAndroidNfcHelperImpl(this);
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		mNfcIntegration.onNewIntent(this, intent);
	}	
	@Override
	protected void onResume()
	{
		super.onResume();
		mFragment.setCallBack(this);
		NfcUtils.onResume(this, mNfcIntegration);
	}
	@Override
	protected void onPause()
	{
		super.onPause();
		mFragment.setCallBack(null);
		mNfcIntegration.onPause(this);
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_fragment_menu, menu);
		
		// Share menu
	    try
	    {
		    MenuItem item = menu.findItem(R.id.menu_item_share);
	        
		    PackageManager pm=getPackageManager();
		    ApplicationInfo info=pm.getApplicationInfo(getPackageName(), 0);
		    
		    Intent shareIntent=new Intent(Intent.ACTION_SEND)
		    	.putExtra(Intent.EXTRA_SUBJECT,pm.getText(getPackageName(), info.labelRes, info))
		    	.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(info.sourceDir)))
//		    	.setType("application/vnd.android.package-archive");
		    	.setType("*/*") // Android >3.x refuse to download apk
//		    	.setType("image/jpeg")
		    	;
		    ShareActionProvider shareActionProvider = (ShareActionProvider) item.getActionProvider();
		    shareActionProvider.setShareHistoryFileName(ShareActionProvider.DEFAULT_SHARE_HISTORY_FILE_NAME);
		    shareActionProvider.setShareIntent(shareIntent);
	    }
		catch (NameNotFoundException e)
		{
			// Ignore
			if (E) Log.e(TAG_INSTALL,"Impossible to share Remote Android");
		}
		return true;
	}
	
	@Override
	public void onExpose()
	{
		Intent intent = new Intent(this, ExposeActivity.class);
		startActivity(intent);
	}
	@Override
	public void onConnect()
	{
		Intent intent = new Intent(this, ConnectActivity.class) // For broadcast mode
			.putExtra(RemoteAndroidManager.EXTRA_FLAGS, FLAG_PROPOSE_PAIRING|FLAG_ACCEPT_ANONYMOUS);
		startActivity(intent);
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.config:
				startActivity(new Intent(this, EditPreferenceActivity.class));
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	@Override
	public void onNfcDiscover(RemoteAndroidInfo info)
	{
		// TODO Auto-generated method stub
		
	}

}
