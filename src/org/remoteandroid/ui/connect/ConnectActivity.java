package org.remoteandroid.ui.connect;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.ui.AbstractFeatureTabActivity;
import org.remoteandroid.ui.FeatureTab;

import android.content.Intent;
import android.nfc.Tag;
import android.os.Bundle;

import com.actionbarsherlock.view.Window;


public class ConnectActivity extends AbstractFeatureTabActivity
{
	// To broadcast my infos
	private static final FeatureTab[] sTabsBroadcast=
		{
			new ConnectDiscoverFragment.Provider(), // BUG sur basculement
			new ConnectQRCodeFragment.Provider(), 
			new ConnectSMSFragment.Provider(), 
//			new ConnectSoundFragment.Provider(),
//			new ConnectWifiDirectFragment.Provider(),
			new ConnectNFCFragment.Provider(), 
//			new ConnectBumpFragment.Provider(), 
			new ConnectTicketFragment.Provider(), 
		};	
	private static final FeatureTab[] sTabsConnect=
		{
			new ConnectDiscoverFragment.Provider(), // BUG sur basculement
			new ConnectQRCodeFragment.Provider(), 
			new ConnectSMSFragment.Provider(), 
//			new ConnectSoundFragment.Provider(),
//			new ConnectWifiDirectFragment.Provider(),
			new ConnectNFCFragment.Provider(), 
//			new ConnectBumpFragment.Provider(), 
//			new ConnectArroundFragment.Provider(), // Retourner un essemble de info
			new ConnectTicketFragment.Provider(), 
		};	

	private boolean mBroadcast; // false: Broadcast
	
	protected FeatureTab[] getFeatureTabs()
	{
		return (mBroadcast) ? sTabsBroadcast : sTabsConnect;
	}
	
	public boolean isBroadcast()
	{
		return mBroadcast;
	}
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		mBroadcast=!RemoteAndroidManager.ACTION_CONNECT_ANDROID.equals(getIntent().getAction());
		super.onCreate(savedInstanceState);
    	setTitle(R.string.connect);
		Application.startService();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
	}
	@Override
	protected void onNfcTag(Tag tag)
	{
		super.onNfcTag(tag);
//		Messages.BroadcastMsg bmsg=nfcCheckDiscovered();
//		if (bmsg!=null)
//		{
//			//if (bmsg.getType()==Messages.BroadcastMsg.Type.CONNECT)
//			{
//				RemoteAndroidInfoImpl info=ProtobufConvs.toRemoteAndroidInfo(this,bmsg.getIdentity());
//				info.isDiscoverNFC=true;
//				info.isBonded=Trusted.isBonded(info);
//				Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
//				intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
//				Application.sAppContext.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
//	//FIXME											onDiscover(info, true);
//			}
//		}

	}
	public void onConnected(RemoteAndroidInfoImpl info)
	{
		if (info!=null)
		{
			Intent result=new Intent();
			result.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
			setResult(RESULT_OK,result);
		}
		finish();
	}
	
}
