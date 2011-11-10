package org.remoteandroid.discovery.bluetooth;

import static org.remoteandroid.Constants.BT_DEVICE_CLASSES;
import static org.remoteandroid.Constants.BT_DISCOVERY_IN_PARALLEL;
import static org.remoteandroid.Constants.BT_DISCOVER_ANONYMOUS;
import static org.remoteandroid.Constants.BT_DISCOVER_ANONYMOUS_IN_PARALLELE;
import static org.remoteandroid.Constants.BT_HACK_RETRY_IF_UNABLE_TO_START_SERVICE_DISCOVERY;
import static org.remoteandroid.Constants.BT_MAJOR_DEVICE_CLASSES;
import static org.remoteandroid.Constants.PAIR_AUTO_PAIR_BT_BOUNDED_DEVICE;
import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.internal.Constants.BT_HACK_WAIT_AFTER_CREATE_RF_COMM;
import static org.remoteandroid.internal.Constants.BT_NB_UUID;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.internal.Constants.W;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.remoteandroid.ConnectionType;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.discovery.DiscoverAndroids;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.socket.Channel;
import org.remoteandroid.internal.socket.bluetooth.BluetoothSocketBossSender;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.service.RemoteAndroidManagerStub;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

// TODO: recyler la connection BT après la découverte
/*
 * FIXME
Scenario de test: 
	- Sur Milestone, Remote android + test en mode discover
	- Sur HTC, désactiver BT, puis Remote android, puis activer BT, désactiver BT, attendre X secondes, activer BT
Il arrive que le BT ne redémarre pas.
Est-ce qu'il faut fermer proprement tous les listeners BT lors de la désactivation ?
*/

public class BluetoothDiscoverAndroids implements DiscoverAndroids
{
	private Context mApplicationContext;
	private ExecutorService mPool=Executors.newCachedThreadPool(); // FIXME: newFixedThreadPool(1) ou 5 thread ?
	private ArrayList<Runnable> mJobs=new ArrayList<Runnable>();
	private HashSet<String> mDiscover=new HashSet<String>();
	private Object mLock=new Object();
	private RemoteAndroidManagerStub mBoss;
	
	private static enum State { PENDING, DISCOVERY_ANONYMOUS,DISCOVERY};
	private State mState=State.PENDING;
	
	static Method sMethod;
	static
	{
		try
		{
			sMethod = BluetoothDevice.class.getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
		}
		catch (Exception e)
		{
			if (W) Log.w(TAG_DISCOVERY,PREFIX_LOG+"Anonymous bluetooth not supported with this device");
		}
	}

	
	// Manage the bluetooth status
	private BroadcastReceiver mReceiver=new BroadcastReceiver()
	{
		
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action =intent.getAction();
			if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
			{
				// Discovery is finished. It's time to try to connect
				mPool.submit(new Runnable()
					{
						public synchronized void run() 
						{
							// Wait discovery finished before try to connect
							for (Runnable job:mJobs)
							{
								mPool.submit(job);
							}
							mJobs.clear();
							synchronized (mLock)
							{
								mLock.notify();
							}
						}
					});
			}
			else if (action.equals(BluetoothDevice.ACTION_FOUND))
			{
				// Found a remote device. Register a job for try to connect
				if (!mBoss.isDiscovering()) return;
				final BluetoothDevice device=(BluetoothDevice)intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				// TODO: filtrer les non associés
				boolean deviceClass=false;
				boolean majorClass=false;
				for (int i:BT_DEVICE_CLASSES)
				{
					if (device.getBluetoothClass().getDeviceClass()==i)
					{
						deviceClass=true;
						break;
					}
				}
				if (!deviceClass)
				{
					for (int i:BT_MAJOR_DEVICE_CLASSES)
					{
						if (device.getBluetoothClass().getMajorDeviceClass()==i)
						{
							majorClass=true;
							break;
						}
					}
				}
				if (deviceClass || majorClass)
				{
					if (mDiscover.contains(device.getAddress()))
						return;
					if (D) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT Discovery "+device.getName()+". Try to use...");
					if (V && deviceClass) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT compatible with device classe");
					if (V && majorClass) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT compatible with major classe");
					final RemoteAndroidInfoImpl myInfo=mBoss.getInfo(ConnectionType.BT);
					for (BluetoothDevice device2:BluetoothAdapter.getDefaultAdapter().getBondedDevices())
					{
						if (device.getAddress().equals(device2.getAddress()))
							return; // I know this device. Try connection later
					}
					mDiscover.add(device.getAddress());
					mJobs.add(new Runnable()
					{
						@Override
						public void run() 
						{
							if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"BT Try to connection anonymously to "+device.getAddress()+" ("+device.getName()+")");
							RemoteAndroidInfoImpl info=tryConnect(device,myInfo,true);
							if (info!=null)
							{
								info.isDiscoverBT=true;
								if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"BT Connection anonymously to "+device.getAddress()+" ("+device.getName()+") done");
								Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
								intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
								if (!mBoss.isDiscovering()) return;
								// TODO: Via handler
								mApplicationContext.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
							}
					    	else
					    		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT device "+device.getAddress()+" not found now");
						}
					});
				}
			}
		}
	};
	
	public BluetoothDiscoverAndroids(Context appContext,RemoteAndroidManagerStub boss)
	{
		mApplicationContext=appContext;
		mBoss=boss;
		IntentFilter filter=new IntentFilter();
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		// TODO: Api level 11, pour vérifier s'il ne faut pas supprimer une association car que BT
		// filter.addAction(BluetoothDevice.ACTION_CONNECTION_STATE_CHANGED);
		appContext.registerReceiver(mReceiver, filter);
	}
	
	@Override
	protected void finalize() throws Throwable
	{
		super.finalize();
		mApplicationContext.unregisterReceiver(mReceiver);
	}
	
	@Override
	public boolean startDiscovery(long timeToIdentify,final int flags,final RemoteAndroidManagerStub discover)
	{
		if (mState==State.DISCOVERY_ANONYMOUS) return true;

		final BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
		if (adapter==null || !adapter.isEnabled()) 
		{
			if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"BT disabled");
			return false;
		}
		
		mJobs.clear();
		mDiscover.clear();
		if ((flags & RemoteAndroidManager.FLAG_NO_BLUETOOTH)!=0) 
		{
			return false;
		}

		// Start the job to discover visible mobile
		mPool.submit(new Runnable()
		{
			
			@Override
			public void run()
			{
				ArrayList<Callable<Void>> jobs=null;
				try
				{
					
					//TODO: exploiter les infos des Bounded devices pour essayer des connexions sans anonymous
					// Try to discover bluetooth device who accept anonymous connection
					if (adapter.isDiscovering())
					{
						if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT is discovering !!!");
					}
					if (BT_DISCOVER_ANONYMOUS)
					{
						if (((flags & RemoteAndroidManager.FLAG_ACCEPT_ANONYMOUS)!=0) && (Compatibility.VERSION_SDK_INT>=Compatibility.VERSION_GINGERBREAD_MR1))
						{
							if (BT_DISCOVER_ANONYMOUS_IN_PARALLELE)
							{
								new Thread()
								{
									public void run() 
									{
										btDiscover(adapter);
									}
								}.start();
							}
							else
								btDiscover(adapter);
						}
					}
				
					// Now, it's time to try to connect to all bonded devices
					if ((adapter.getScanMode() & BluetoothAdapter.SCAN_MODE_CONNECTABLE)!=BluetoothAdapter.SCAN_MODE_CONNECTABLE)
						if (E) Log.d(TAG_DISCOVERY,PREFIX_LOG+"BT not connectable !");
					if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT try to connect all bonded devices...");
					if (BT_DISCOVERY_IN_PARALLEL)
					{
						if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT Discover in parallel...");
						jobs=new ArrayList<Callable<Void>>();
					}
					final RemoteAndroidInfoImpl myInfo=mBoss.getInfo(ConnectionType.BT);
					for (final BluetoothDevice device:adapter.getBondedDevices())
					{
						if ((device.getBluetoothClass().getDeviceClass()==BluetoothClass.Device.PHONE_SMART)
							|| (device.getBluetoothClass().getMajorDeviceClass()==BluetoothClass.Device.Major.COMPUTER))
						{
							// Add the job in the pool
							Callable<Void> job=new Callable<Void>()
							{
								public Void call() 
								{
									final String name=((device.getName()==null) ? device.getAddress() : device.getName());
									RemoteAndroidInfoImpl info=tryConnect(device,myInfo,false);
							    	if (info!=null)
							    	{
							    		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT discover bonded "+name+" with a connection.");
							    		// Auto register BT device if it's bonded
							    		if (PAIR_AUTO_PAIR_BT_BOUNDED_DEVICE)
							    		{
								    		if (!Trusted.isBonded(info))
								    		{
									    		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT Auto register bonded device "+name);
								    			Trusted.registerDevice(mApplicationContext, info,ConnectionType.BT);
								    		}
							    		}
							    		info.isDiscoverBT=true;
										Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
										intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
										mApplicationContext.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
							    	}
							    	else
							    		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT device "+name+" not found now");
									return null;
								}
							};
							if (BT_DISCOVERY_IN_PARALLEL)
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
					if (BT_DISCOVERY_IN_PARALLEL)
					{
						try
						{
							List<Future<Void>> j=mPool.invokeAll(jobs);
							for (int i=0;i<j.size();++i)
								j.get(i).get();
						}
						catch (InterruptedException e)
						{
							// Ignore
				    		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT discover interrupted.",e);
						}
						catch (ExecutionException e)
						{
				    		if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"BT error",e);
						}
					}
				} 
				finally
				{
					mState=State.PENDING;
					if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT all bonded devices tested");
					discover.finishDiscover();
				}
			}

		});
		return true;
	}
	
	private void btDiscover(final BluetoothAdapter adapter)
	{
		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT Start BT discovering for detect unknown device...");
		// Discover unknown device, and try to connect.
		mState=BluetoothDiscoverAndroids.State.DISCOVERY_ANONYMOUS;
		adapter.startDiscovery();
		// Wait ACTION_DISCOVERY_FINISHED
		synchronized (mLock)
		{
			try
			{
				mLock.wait(14000); // 12s max for finish
			}
			catch (InterruptedException e)
			{
				// Ignore
			}
		}
		if (adapter.isDiscovering())
		{
			if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"BT remains discovering !");
		}
		mState=BluetoothDiscoverAndroids.State.DISCOVERY;
		
		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT All unknown devices detected.");
	}

	public static RemoteAndroidInfoImpl tryConnect(Uri uri)
	{
		// TODO
		return null;
	}
	private static RemoteAndroidInfoImpl tryConnect(BluetoothDevice device,RemoteAndroidInfo myInfo,boolean anno)
	{
		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT Try to connect "+((anno) ? "anonymously " : "") +"to "+device.getName()+"...");
		BluetoothSocket socket=connect(device,anno);
		if (socket!=null)
		{
			try
			{
				if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT device "+device.getName()+" connected. Ask info...");
				// Ask remote android info
				final long threadid = Thread.currentThread().getId();
				Msg msg = Msg.newBuilder()
					.setType(Type.CONNECT_FOR_DISCOVERING)
					.setThreadid(threadid)
					.setIdentity(ProtobufConvs.toIdentity(myInfo))
					.build();
				Channel.writeMsg(msg, socket.getOutputStream());
		
				// Read protobuf message
				byte[] buf=new byte[Channel.INITIAL_BUFSIZE];
				int ss=socket.getInputStream().read(buf,0,4);
				if (ss!=4)
				{
					throw new EOFException("Communication closed");
				}
				int length=(buf[0] << 24)
		        	+ ((buf[1] & 0xFF) << 16)
		        	+ ((buf[2] & 0xFF) << 8)
		        	+ (buf[3] & 0xFF);
				if (length<0)
					return null; // Hack protocol ?
				if (length+4>buf.length)
				{
					buf=new byte[length+4];
				}
				int pos=4;
				int l=length;
				do
				{
					int s=socket.getInputStream().read(buf,pos,l);
					if (s==-1)
						throw new EOFException();
					pos+=s;
					l-=s;
				} while (l>0);
				Msg resp=(Msg)Channel.sPrototype.newBuilderForType().mergeFrom(buf, 4, length).build();
	
				if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT device "+device.getName()+" return info.");
				
				RemoteAndroidInfoImpl info = ProtobufConvs.toRemoteAndroidInfo(resp.getIdentity());
				// I find it !
				if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"BT Device "+device.getName()+" found");
				return info;
			}
			catch (IOException e)
			{
				if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"BT Device "+device.getName()+" error",e);
				return null;
			}
			finally
			{
				try
				{
					if (socket!=null)
						socket.close();
					if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT device "+device.getName()+" connection closed.");
				}
				catch (IOException e)
				{
					// Ignore
					if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT Error when close socket",e);
				}
			}
		}
		return null;
	}
	
	@Override
	public void cancelDiscovery(final RemoteAndroidManagerStub discover)
	{
		try
		{
			BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
			if (Compatibility.VERSION_SDK_INT>=Compatibility.VERSION_GINGERBREAD_MR1)
			{
				if (adapter.isDiscovering())
					adapter.cancelDiscovery();
			}
			mPool.shutdown();
			mPool=Executors.newCachedThreadPool();
			discover.finishDiscover();
		}
		catch (SecurityException e)
		{
			// Ignore
		}
	}
	/**
	 * Connect to one of UUID.
	 * 
	 * @param device The device to connect
	 * @param anno Use anonyme connection if true
	 * @return
	 */
	public static BluetoothSocket connect(BluetoothDevice device,boolean anno)
	{
    	UUID uuid=null;
    	BluetoothSocket socket=null;
    	for (int i=0;i<BT_NB_UUID;++i)
    	{
    		uuid=BluetoothSocketBossSender.sKeys[i];
			if (Thread.interrupted())
				return null;
    		try
    		{
    			if (anno)
    			{
					try
					{
						// socket=device.createInsecureRfcommSocketToServiceRecord(uuid);
    					socket=(BluetoothSocket)sMethod.invoke(device,uuid);
					}
					catch (Exception e)
					{
						throw new IllegalArgumentException("Anonymous bluetooth not supported with this device");
					}
    			}
    			else
    			{
    				socket=device.createRfcommSocketToServiceRecord(uuid);
    			}
    			if (BT_HACK_WAIT_AFTER_CREATE_RF_COMM!=0)
    				try { Thread.sleep(BT_HACK_WAIT_AFTER_CREATE_RF_COMM); } catch (InterruptedException e) {} 
    	    	socket.connect(); // May be blocked if the same uuid is allready used.
    	        return socket;
    		}
    		catch (IOException e)
    		{
    			// If "Unable to start Service Discovery" unknown
    			// If "Service discovery failed", not found remote device
    			if (!"Service discovery failed".equals(e.getMessage()))
    			{
    				if (W) Log.w(TAG_DISCOVERY,PREFIX_LOG+"BT try connect error for "+device.getName()+" ("+e.getMessage()+")");
    				if (BT_HACK_RETRY_IF_UNABLE_TO_START_SERVICE_DISCOVERY)
    				{
	    				if ("Unable to start Service Discovery".equals(e.getMessage()))
	    				{
	    					try { Thread.sleep(1000); } catch (InterruptedException ee) {}
	    					continue;
	    				}
    				}
    			}
    		}
    	}
    	return null;
	}
	
}