package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.HACK_CONNECT_FORCE_FRAGMENTS;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.Messages.Identity;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.Pair;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.socket.Channel;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.StyleFragmentActivity;
import org.remoteandroid.ui.connect.qrcode.CameraManager;

import com.google.protobuf.InvalidProtocolBufferException;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.Window;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

// TODO: getSystemAvailableFeatures ()
public class ConnectActivity extends StyleFragmentActivity 
implements TechnologiesFragment.Listener
{
	static final int DIALOG_TRY_CONNECTION=1;
	private static TryConnection sTryConnections;
	
	NfcAdapter mNfcAdapter;
	
//	private BroadcastReceiver mPhoneStateReceiver=new BroadcastReceiver() 
//    {
//		
//        @Override
//        public void onReceive(Context context, Intent intent) 
//        {
//        	onReceivePhoneEvent(context,intent);
//        }
//    };
    
	private BroadcastReceiver mNetworkStateReceiver=new BroadcastReceiver() 
    {
		
        @Override
        public void onReceive(Context context, Intent intent) 
        {
        	onReceiveNetworkEvent(context,intent);
        }
    };
    
    private BroadcastReceiver mBluetoothReceiver=new BroadcastReceiver()
    {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			onReceiveBluetoothEvent(context,intent);
		}
    };
	private BroadcastReceiver mAirPlane = new BroadcastReceiver() 
	{
	      @Override
	      public void onReceive(Context context, Intent intent) 
	      {
	            onReceiveAirplaneEvent(context,intent);
	      }
	};
	
	FragmentManager mFragmentManager;
	TechnologiesFragment mTechnologiesFragment;
	AbstractBodyFragment mBodyFragment;

	Technology[] mTechnologies;
	public int mActiveNetwork;

	private boolean mMerge;
	
	private boolean mAcceptAnonymous;
	
	/*
	 * Use two strategie:
	 * * for use one fragment at a time, use the android.R.id.content
	 * * for use merged fragment, use layout and R.id.technology, R.id.help and R.id.body.
	 * @see org.remoteandroid.ui.connect.StyleFragmentActivity#onCreate(android.os.Bundle)
	 */
	// FIXME: FLAG_ACTIVITY_FORWARD_RESULT
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		//setTheme(android.R.style.Theme_Light_NoTitleBar);
		// TODO: placer tous les styles dans des wrappers de style pour pouvoir les adapter
		PackageManager pm=getPackageManager();
		if (pm.checkPermission(Manifest.permission.ACCESS_NETWORK_STATE, getCallingPackage())!=PackageManager.PERMISSION_GRANTED)
		{
			if (E) Log.e(TAG_CONNECT,PREFIX_LOG+"Allowed "+Manifest.permission.ACCESS_NETWORK_STATE+" permission");
			setResult(-1);
			finish();
			return;
		}
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);
        this.setContentView(new TextView(this)); // Hack to activate the progress bar
		setProgressBarIndeterminateVisibility(Boolean.FALSE);
		
		mAcceptAnonymous=getIntent().getBooleanExtra(RemoteAndroidManager.EXTRA_ACCEPT_ANONYMOUS, false);
		mFragmentManager=getSupportFragmentManager();
		mTechnologies=Technology.getTechnologies();
		
		mMerge=getResources().getBoolean(R.bool.connect_merge);
		
		// Hack to simulate merger in landscape, and not merged in portrait
		if (HACK_CONNECT_FORCE_FRAGMENTS)
		{
			if (getResources().getConfiguration().orientation  == Configuration.ORIENTATION_LANDSCAPE)
				mMerge=true;
		}

		FragmentTransaction transaction=mFragmentManager.beginTransaction();
		mTechnologiesFragment=(TechnologiesFragment)mFragmentManager.findFragmentById(R.id.technologies);
		Fragment f=(Fragment)mFragmentManager.findFragmentById(R.id.body);
		if (mTechnologiesFragment!=null) transaction.remove(mTechnologiesFragment);
		if (f!=null) transaction.remove(f);
		if (f instanceof AbstractBodyFragment)
			mBodyFragment=(AbstractBodyFragment)f;
		else
			mTechnologiesFragment=(TechnologiesFragment)f;
		transaction.commit(); // remove all fragments
		mFragmentManager.executePendingTransactions();
		
		transaction=mFragmentManager.beginTransaction();
		if (mMerge)
		{
			setContentView(R.layout.connect_frames);
			if (mBodyFragment==null) mBodyFragment=new EmptyBodyFragment();
			if (mTechnologiesFragment==null) mTechnologiesFragment=new TechnologiesFragment();
			transaction.replace(R.id.technologies, mTechnologiesFragment);
			transaction.replace(R.id.body, mBodyFragment);
			transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
		}
		else
		{
			setContentView(R.layout.connect_noframes);
			if (mTechnologiesFragment==null)
			{
				mTechnologiesFragment=new TechnologiesFragment();
				// Restore state after changed the orientation
				if (mBodyFragment==null || mBodyFragment instanceof EmptyBodyFragment)
				{
					transaction.replace(R.id.body, mTechnologiesFragment);
				}
				else
					transaction.replace(R.id.body, mBodyFragment);
			}
			else
			{
				if (mBodyFragment==null) mBodyFragment=new EmptyBodyFragment();
				if (mBodyFragment instanceof EmptyBodyFragment)
					transaction.replace(R.id.body, mTechnologiesFragment);
				else
					transaction.replace(R.id.body, mBodyFragment);
			}
				
			transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
		}
		transaction.commit();
//mFragmentManager.executePendingTransactions();		
		if (mTechnologiesFragment!=null)
			mTechnologiesFragment.setTechnologies(mTechnologies); // FIXME
		// Reconnect background thread after rotation
		TryConnection tryConnections=sTryConnections;
		if (tryConnections!=null)
		{
			tryConnections.mActivity=new WeakReference<ConnectActivity>(this);
			tryConnections.mProgressDialog=(ConnectDialogFragment)mFragmentManager.findFragmentByTag("dialog");
		}
	}
	
	@Override
	protected void onResume()
	{
		super.onResume();
		mActiveNetwork=NetworkTools.getActiveNetwork(Application.sAppContext);
		registerReceiver(mNetworkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
		registerReceiver(mAirPlane,new IntentFilter("android.intent.action.SERVICE_STATE"));
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.ECLAIR)
		{
			IntentFilter filter=new IntentFilter();
			filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
			filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
			registerReceiver(mBluetoothReceiver, filter);
		}
		onNdefDiscovered();
		
	}
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }	
    // TODO: enregister à la main. Receive an expose because the user push from other device
	private void onNdefDiscovered()
	{
		NfcManager nfcManager=(NfcManager)getSystemService(NFC_SERVICE);
		if (NFC && nfcManager!=null)
		{
			mNfcAdapter=nfcManager.getDefaultAdapter();
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
			        				if (bmsg.getType()==Messages.BroadcastMsg.Type.EXPOSE)
			        				{
										RemoteAndroidInfo info=ProtobufConvs.toRemoteAndroidInfo(this,bmsg.getIdentity());
										if (D) Log.d(TAG_NFC,PREFIX_LOG+"info="+info);
										tryConnect(null, Arrays.asList(info.getUris()), true);
			        				}
			        				else
										if (W) Log.d(TAG_NFC,PREFIX_LOG+"Connect tag. Ignore.");
								}
								catch (InvalidProtocolBufferException e)
								{
									if (W) Log.d(TAG_NFC,PREFIX_LOG+"Invalide data");
								}
		        			}
		        		}
		            }
		        }
		    }
    		PendingIntent pendingIntent = 
    				PendingIntent.getActivity(this, 0, 
    					new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
    		mNfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);
		}
		
	}
	
	@Override
	protected void onPause()
	{
		super.onPause();
   		unregisterReceiver(mNetworkStateReceiver);
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.ECLAIR)
			unregisterReceiver(mBluetoothReceiver); 
		unregisterReceiver(mAirPlane); 
	}
	
	void onReceivePhoneEvent(Context context,Intent intent)
	{
		if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED))
		{
			// TODO
		}
	}
	void onReceiveNetworkEvent(Context context,Intent intent)
	{
		if (mBodyFragment!=null)
			mBodyFragment.onReceiveNetworkEvent(context,intent);
		
		ConnectivityManager conn=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
		if (conn==null || conn.getActiveNetworkInfo()==null)
		{
			mActiveNetwork&=~NetworkTools.ACTIVE_LOCAL_NETWORK;
		}
		else
		{
			int type=conn.getActiveNetworkInfo().getType();
			switch (type)
			{
				case ConnectivityManager.TYPE_MOBILE:
				case ConnectivityManager.TYPE_MOBILE_DUN:
				case ConnectivityManager.TYPE_MOBILE_HIPRI:
				case ConnectivityManager.TYPE_MOBILE_MMS:
				case ConnectivityManager.TYPE_MOBILE_SUPL:
				case ConnectivityManager.TYPE_WIMAX:
					mActiveNetwork&=~NetworkTools.ACTIVE_LOCAL_NETWORK;
					break;
				case ConnectivityManager.TYPE_BLUETOOTH:
				case ConnectivityManager.TYPE_ETHERNET:
				case ConnectivityManager.TYPE_WIFI:
					mActiveNetwork|=NetworkTools.ACTIVE_LOCAL_NETWORK;
					break;
	            default:
	            	if (W) Log.w(TAG_DISCOVERY,PREFIX_LOG+"Unknown network type "+type);
	            	break;
	        }
		}
		onUpdateActiveNetwork();
	}
	void onReceiveBluetoothEvent(Context context, Intent intent)
	{
		if (mBodyFragment!=null)
			mBodyFragment.onReceiveBluetoothEvent(context,intent);
		
		if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))
		{
			int state=intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_DISCONNECTED);
			if (state==BluetoothAdapter.STATE_ON)
				mActiveNetwork|=NetworkTools.ACTIVE_BLUETOOTH;
			else
				mActiveNetwork&=~NetworkTools.ACTIVE_BLUETOOTH;
			onUpdateActiveNetwork();
		}
	}
	void onReceiveAirplaneEvent(Context context,Intent intent)
	{
		if (mBodyFragment!=null)
			mBodyFragment.onReceiveAirplaneEvent(context,intent);
	}
	
	void onUpdateActiveNetwork()
	{
		if (mBodyFragment!=null)
			mBodyFragment.onUpdateActiveNetwork();
	}

	int getActiveNetwork()
	{
		return mActiveNetwork;
	}
	
	@Override
	public void onBackPressed()
	{
		if (!mMerge)
		{
			Fragment f=mFragmentManager.findFragmentById(R.id.body);
			if (!(f instanceof TechnologiesFragment))
			{
				FragmentTransaction transaction=mFragmentManager.beginTransaction();
				transaction.replace(R.id.body, mTechnologiesFragment);
				transaction.setTransitionStyle(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
				transaction.commit();
			}
			else
				super.onBackPressed();
		}
		else
			super.onBackPressed();
	}
	
	@Override
	public void onTechnologieSelected(Technology technology)
	{
		InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
		FragmentTransaction transaction;
		transaction=mFragmentManager.beginTransaction();
		mBodyFragment=technology.makeFragment();
		transaction.replace(R.id.body, mBodyFragment);
		if (!mMerge)
			transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
		else
			transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
		transaction.commit();
		mFragmentManager.executePendingTransactions();
	}
	
//	@Override
//	protected void onPause()
//	{
//		super.onPause();
//		TryConnection tryHandler=sTryConnections;
//		if (tryHandler!=null)
//		{
//			tryHandler.mProgressDialog=null;
//		}
//	}

	
	public void tryConnect(final FirstStep firstStep,List<String> uris,boolean acceptAnonymous)
	{
		mFragmentManager.executePendingTransactions();
		
		InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
		if (sTryConnections==null)
		{
//			if (urls==null)
//			{
//				 DialogFragment alert = AlertDialogFragment.newInstance(android.R.drawable.ic_dialog_alert, 
//					 R.string.connect_alert_title, 
//					 R.string.connect_alert_connection_impossible);
//				 alert.show(mFragmentManager, "dialog");
//				 return;
//			}
			final ConnectDialogFragment dlg=ConnectDialogFragment.newInstance();
			dlg.show(mFragmentManager, "dialog");
			sTryConnections=new TryConnection(acceptAnonymous);
			TryConnection tryHandler=sTryConnections;
			if (tryHandler!=null)
			{
				tryHandler.mActivity=new WeakReference<ConnectActivity>(this);
				tryHandler.init(firstStep,uris);
				tryHandler.mProgressDialog = dlg;
				tryHandler.execute();
			}
		}
	}
	
	private void finishWithOk(RemoteAndroidInfoImpl info)
	{
		Intent result=new Intent();
		result.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
		setResult(RESULT_OK,result);
		finish();
	}
	
	public static class AlertDialogFragment extends DialogFragment 
	{
		public static AlertDialogFragment newInstance(int icon,int title,int message)
		{
			AlertDialogFragment frag=new AlertDialogFragment();
			Bundle args = new Bundle();
	        if (icon!=-1) args.putInt("icon", icon);
	        if (title!=-1) args.putInt("title", title);
	        if (message!=-1)
	        	args.putInt("message",message);
	        frag.setArguments(args);
	        return frag;
		}
	    @Override
	    public Dialog onCreateDialog(Bundle savedInstanceState) 
	    {
	    	int icon=getArguments().getInt("icon");
	    	int title=getArguments().getInt("title");
	    	int message=getArguments().getInt("message");
	    	
	        AlertDialog.Builder builder=new AlertDialog.Builder(getActivity());
	        if (icon!=-1) builder.setIcon(icon);
	        if (title!=-1) builder.setTitle(title);
	        if (message!=-1) builder.setMessage(message);
	        builder.setPositiveButton(android.R.string.ok,
		            new DialogInterface.OnClickListener() 
		        	{
		                public void onClick(DialogInterface dialog, int whichButton) 
		                {
		                    //((FragmentAlertDialog)getActivity()).doPositiveClick();
		                }
		            }
		        );
		     return builder.create();
	    }
	};
	public static class ConnectDialogFragment extends DialogFragment
	{
		public static ConnectDialogFragment newInstance()
		{
			return new ConnectDialogFragment();
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			super.onCancel(dialog);
			TryConnection tryHandler=sTryConnections;
			if (tryHandler!=null)
			{
				sTryConnections=null;
				if (tryHandler.mProgressDialog!=null && tryHandler.mProgressDialog.isVisible())
				{
					tryHandler.mProgressDialog.dismiss();
					tryHandler.mProgressDialog=null;
				}
				tryHandler.cancel(false);
			}
		}
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{

			ProgressDialog progressDialog = new ProgressDialog(getActivity());
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	        progressDialog.setTitle(R.string.connect_try);
			progressDialog.setMessage(getResources().getText(R.string.connect_try));
            progressDialog.setCancelable(true);
            return progressDialog;
		}

	}	
	public interface FirstStep
	{
		int run(TryConnection connection);
	}
	public static class TryConnection extends AsyncTask<Void, Integer, Object>
	{
		private FirstStep mFirstStep;
		public ConnectDialogFragment mProgressDialog=null;
		private List<String> mUris;
		private WeakReference<ConnectActivity> mActivity=new WeakReference<ConnectActivity>(null);
		private boolean mAcceptAnonymous;
		private int mMessage;
		
		public void publishMessage(int message,int val)
		{
			mMessage=message;
			publishProgress(val);
		}
		
		TryConnection(boolean acceptAnonymous)
		{
			mAcceptAnonymous=acceptAnonymous;
		}
		void init(FirstStep firstStep,List<String> uris)
		{
			mFirstStep=firstStep;
			mUris=uris;
		}
		void setUris(ArrayList<String> uris)
		{
			mUris=uris;
		}

		
		@Override
		protected Object doInBackground(Void...params)
		{
			int firststep=0;
			if (mFirstStep!=null)
			{
				int msg=mFirstStep.run(this);
				if (msg!=0) // Error
				{
					return msg;
				}
				firststep=1;
			}
			
			// TODO: Ne pas utiliser les technos non acceptés par le caller
			// if (pm.checkPermission(Manifest.permission.BLUETOOTH, getCallingPackage())!=PackageManager.PERMISSION_GRANTED)
			for (int i=0;i<mUris.size();++i)
			{
				RemoteAndroidInfoImpl info=null;
				String uri=mUris.get(i);
				try
				{
					if (isCancelled())
						return null;
					publishMessage(R.string.connect_try_connect,i+firststep);
					if (D) Log.d(TAG_CONNECT,PREFIX_LOG+"Try "+uri+"...");
					
					info=tryConnectForCookie(uri);
				}
				catch (final IOException e)
				{
					if (W) Log.w(TAG_CONNECT,PREFIX_LOG+"Connection for cookie impossible ("+e.getMessage()+")");
					if (D)
					{
						Application.sHandler.post(new Runnable()
						{
							@Override
							public void run()
							{
								Toast.makeText(Application.sAppContext, e.getMessage(), Toast.LENGTH_LONG).show();
							}
						});
					}
				}
				catch (SecurityException e)
				{
					// Accept only bounded device.
					info=new Trusted(Application.sAppContext, Application.sHandler).pairWith(mUris);
					if (info==null)
					{
						if (W) Log.w(TAG_CONNECT,PREFIX_LOG+"Pairing impossible");
						return R.string.connect_alert_pairing_impossible;
					}
					if (I) Log.i(TAG_CONNECT,PREFIX_LOG+"Pairing successfull");
				}
				if (info!=null) // Cool
				{
					if (I) Log.i(TAG_CONNECT,PREFIX_LOG+"Connection for cookie with "+uri);
					return info;
				}
				
			}
			return R.string.connect_alert_connection_impossible;
		}
		
		@Override
		protected void onProgressUpdate(Integer... values)
		{
			DialogFragment dlg=mProgressDialog;
			if (dlg!=null)
			{
				ProgressDialog d=(ProgressDialog)dlg.getDialog();
				if (d!=null)
				{
					d.setMessage(Application.sAppContext.getText(mMessage));
					int firststep=(mFirstStep==null) ? 0 : 1;
					d.setProgress(values[0]*100/(mUris.size()+firststep));
					
				}
			}
		}
		
		/**
		 * @param result Integer with message or RemoteAndroidInfo
		 */
		@Override
		protected void onPostExecute(final Object result)
		{
			final ConnectActivity activity=mActivity.get();
			if (activity==null)
				return;
			final DialogFragment dlg=mProgressDialog;
			if (result instanceof RemoteAndroidInfoImpl)
			{
				final RemoteAndroidInfoImpl info=(RemoteAndroidInfoImpl)result;
				if (result!=null)
				{
					Application.sHandler.postDelayed(new Runnable()
					{
						@Override
						public void run()
						{
							if (dlg!=null && dlg.getFragmentManager()!=null && dlg.isVisible())
							{
								dlg.dismiss();
							}
							activity.finishWithOk(info);
						}
					}, 500);
				}
				if (dlg!=null && result!=null)
				{
					ProgressDialog d=(ProgressDialog)dlg.getDialog();
					if (d!=null)
						d.setProgress(100);
				}
			}
			else
			{
				if (dlg!=null)
					dlg.dismiss();
				AlertDialogFragment.newInstance(android.R.drawable.ic_dialog_alert, 
					R.string.connect_alert_title, (Integer)result)
					.show(activity.mFragmentManager, "dialog");;
			}
			sTryConnections=null;
		}
		@Override
		protected void onCancelled()
		{
			super.onCancelled();
			final DialogFragment dlg=mProgressDialog;
			if (dlg!=null)
				dlg.dismiss();
			sTryConnections=null;
		}
	}
	
	protected boolean isAcceptAnonymous()
	{
		return mAcceptAnonymous;
	}
	public static RemoteAndroidInfoImpl tryConnectForCookie(String uri) throws SecurityException, IOException
	{
		Pair<RemoteAndroidInfoImpl,Long> msg=Application.getManager().askMsgCookie(Uri.parse(uri));
		if (msg==null || msg.second==0)
			throw new SecurityException();
		return msg.first;
		
	}

	private boolean hasCamera()
	{
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.ECLAIR)
		{
			return (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA));
		}
		else
		{
			return (CameraManager.get()!=null);
		}
	}
}
