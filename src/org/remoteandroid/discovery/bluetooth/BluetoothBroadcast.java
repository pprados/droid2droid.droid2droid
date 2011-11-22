package org.remoteandroid.discovery.bluetooth;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.Constants.BT_INFORM_PRESENCE;
import static org.remoteandroid.Constants.BT_INFORM_PRESENCE_IN_PARALLEL;
import static org.remoteandroid.Constants.BT_WAIT_BEFORE_CLOSE_SOCKET;
import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.remoteandroid.Application;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.binder.bluetooth.BluetoothRemoteAndroid;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.socket.Channel;
import org.remoteandroid.internal.socket.bluetooth.BluetoothSocketBossSender;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.service.RemoteAndroidService;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;


public class BluetoothBroadcast extends BroadcastReceiver
{
	private static BroadcastReceiver mReceiver;
	private static Context mAppContext;
	
	public static synchronized void startBluetoothManager(Context context)
	{
		if (mReceiver==null)
		{
			mAppContext=context.getApplicationContext();
			mReceiver=new BluetoothBroadcast();
			IntentFilter filter=new IntentFilter();
			filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
			filter.addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED);
			context.registerReceiver(mReceiver, filter);
		}
		if (BluetoothAdapter.getDefaultAdapter().isEnabled())
		{
			if (BT_INFORM_PRESENCE)
				informPresence();
		}
	}
	
	@Override
	public void onReceive(Context context, final Intent intent)
	{
		if (!RemoteAndroidService.isActive()) return;
		final String action=intent.getAction();
		if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED))
		{
			if (BT_INFORM_PRESENCE)
			{
				int state=intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
				if (state==BluetoothAdapter.STATE_ON)
				{
					BluetoothRemoteAndroid.restart();
					final BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
					Application.setBackName(adapter.getName());
					informPresence();
				}
				else if ((state==BluetoothAdapter.STATE_OFF))
				{
					for (RemoteAndroidInfo i:Trusted.getBonded())
					{
						RemoteAndroidInfoImpl info=(RemoteAndroidInfoImpl)i;
						if (info.removeUrisWithScheme(SCHEME_BT) || info.removeUrisWithScheme(SCHEME_BTS))
						{
							if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP "+info.getName()+" is stopped.");
							info.isDiscoverBT=false;
							Intent intentDiscover=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
							intentDiscover.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
							// FIXME: remove remote android ?
//										if (info.getUris().length==0)
//											intentDiscover.putExtra(RemoteAndroidManager.EXTRA_REMOVE,true);
							Application.sAppContext.sendBroadcast(intentDiscover,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
							
						}
					}
					
				}
			}
		}
		else if (action.equals(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED))
		{
			String backName=intent.getStringExtra(BluetoothAdapter.EXTRA_LOCAL_NAME);
			if (backName!=null)
			{
				Application.setBackName(backName);
			}
			if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"BT local name changed to "+backName);
		}
	}
	private static void informPresence()
	{
		new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				final BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
				ExecutorService pool=null;
				ArrayList<Callable<Void>> jobs=null;
				if (BT_INFORM_PRESENCE_IN_PARALLEL)
				{
					if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT Inform of my presence in parallel...");
					pool=Executors.newCachedThreadPool(); // FIXME: newFixedThreadPool(1) ou 5 thread ?
					jobs=new ArrayList<Callable<Void>>();

				}
				else
					if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT inform of my presence...");

				for (final BluetoothDevice device:adapter.getBondedDevices())
				{
					if ((device.getBluetoothClass().getDeviceClass()==BluetoothClass.Device.PHONE_SMART)
						|| (device.getBluetoothClass().getMajorDeviceClass()==BluetoothClass.Device.Major.COMPUTER))
					{
						Callable<Void> job=new Callable<Void>()
						{
							public Void call() 
							{
								BluetoothSocket socket=null;
								final String name=(device.getName()==null) ? device.getAddress() : device.getName();
								try
								{
									// Wait bluetooth on
									while(adapter.getState()!=BluetoothAdapter.STATE_ON)
									{
										try 
										{ 
											Thread.sleep(200); 
										} catch (InterruptedException e) {	}
										if (!adapter.isEnabled()) return null;
									}
						    		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT presence: try to connect to device "+name+"... ("+BluetoothSocketBossSender.sDiscoverUUID+")");
									socket=device.createRfcommSocketToServiceRecord(BluetoothSocketBossSender.sDiscoverUUID);
								        
									if (socket!=null)
									{ 
										socket.connect();
										// Ask remote android info
										RemoteAndroidInfoImpl info=Trusted.getInfo(mAppContext);
										info.isBonded=true;
										Messages.Identity msg=ProtobufConvs.toIdentity(info);
						    			Channel.writeMsg(msg, socket.getOutputStream());
										// Wait the flush before close. The receiver can be load all the datas.
						    			if (BT_WAIT_BEFORE_CLOSE_SOCKET)
						    				try	{ Thread.sleep(2000); }	catch (InterruptedException ee) { }
										socket.close();
							    		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT presence: device "+name+" informed of my presence");
									}
								}
								catch (IOException e)
								{
									// "Unable to start Service Discovery"
									// "Service discovery failed" remote device not found
									if (!"Service discovery failed".equals(e.getMessage()))
									{
										// "Unable to start Service Discovery" Unknown
										if (I && !D) Log.i(TAG_DISCOVERY,PREFIX_LOG+"BT Impossible to submit my presence to "+name+" ("+e.getMessage()+")");
										if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"BT Impossible to submit my presence to "+name,e);
										// Work around. Wait before retry with another connection
										if (BT_HACK_WAIT_BEFORE_TRY_ANOTHER_CONNECTION)
											try	{ Thread.sleep(500); }	catch (InterruptedException ee) { }
									}
									else
									{
										if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"BT Device "+name+" not found");
									}
								}
								finally
								{
									if (socket!=null)
									{
										try
										{
											socket.close();
										}
										catch (Exception e)
										{
											// Ignore
										}
									}
								}
								return null;
							}
						};
						if (BT_INFORM_PRESENCE_IN_PARALLEL)
							jobs.add(job);
						else
						{
							try
							{
								job.call();
							}
							catch (Exception e)
							{
								// Ignore
					    		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT error",e);
							}
						}
					}
				}
				if (BT_INFORM_PRESENCE_IN_PARALLEL)
				{
					try
					{
						pool.invokeAll(jobs);
					}
					catch (InterruptedException e)
					{
						// Ignore
			    		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT error",e);
					}
				}
				if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"BT Inform presence done");
			}
		}).start();
		
	}
}
