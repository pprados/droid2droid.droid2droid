package org.remoteandroid.binder.bluetooth;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.BindException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.remoteandroid.Application;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.binder.UpstreamHandler;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.socket.Channel;
import org.remoteandroid.internal.socket.bluetooth.BluetoothSocketBossSender;
import org.remoteandroid.internal.socket.bluetooth.BluetoothSocketChannel;
import org.remoteandroid.pairing.Pairing;
import org.remoteandroid.service.RemoteAndroidService;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Process;
import android.util.Log;


public class BluetoothServerSocketChannel implements Runnable

{
	protected ExecutorService mExecutors;
	BluetoothAdapter mAdapter;
	private UpstreamHandler mHandler;
    private List<BluetoothServerSocket> mSrvSockets=Collections.synchronizedList(new ArrayList<BluetoothServerSocket>());
    

    protected BluetoothServerSocketChannel(
    		ExecutorService executors,
    		UpstreamHandler handler) throws IOException
    {
    	mExecutors=executors;
    	mHandler=handler;
    	mAdapter=BluetoothAdapter.getDefaultAdapter();
    }
    
    public void stop()
    {
    	for (BluetoothServerSocket srvSocket:mSrvSockets)
    	{
    		try
    		{
    			srvSocket.close();
    			if (D) Log.d(TAG_SERVER_BIND,PREFIX_LOG+"BT stop listen on "+srvSocket);
    		}
    		catch (IOException e)
    		{
    			// Ignore
    		}
    	}
    }

    @Override
    public void run()
    {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        if (!mAdapter.isEnabled()) return;
        startRuntimeDiscoverAgent();
        for (int i=0;i<BT_NB_UUID;++i)
        {
        	final int j=i;
        	final UUID uuid=BluetoothSocketBossSender.sKeys[i];
//			try
			{
//		        final BluetoothServerSocket srvSocket=mAdapter.listenUsingRfcommWithServiceRecord("RemoteAndroid", uuid);
//				mSrvSockets.add(srvSocket);
	        	new Thread(
	    			new Runnable()
	    			{
	    				@Override
	    				public void run() 
	    				{
	    					if (BT_HACK_DELAY_STARTUP!=0)
	    					{
	    						try { Thread.sleep(j*BT_HACK_DELAY_STARTUP); } catch (Exception e) {}
	    					}
	    					
	    					BluetoothServerSocket srvSocket=null;
	    			        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
	    			        for (int i=0;i<BT_MAX_LOOP_BEFORE_STOP;++i)
	    			        {
		    			        //BluetoothServerSocket srvSocket=null;
								try
								{
									if (!mAdapter.isEnabled())
										return;
									if (D) Log.d(TAG_SERVER_BIND,PREFIX_LOG+"BT listen on "+uuid);
			    					srvSocket=mAdapter.listenUsingRfcommWithServiceRecord("RemoteAndroid", uuid);
			    					mSrvSockets.add(srvSocket);
			    					manageBluetoothSocket(uuid, srvSocket,true);
								}
								catch (IOException e)
								{
									mHandler.exceptionCaught(0,e);
									if (e.getMessage().startsWith("Unknopwn error"))
									{
										if (E) Log.e(TAG_DISCOVERY,"BT invalide state.");
										return;
									}
								}
								catch (Throwable e)
								{
									if (E) Log.e(TAG_DISCOVERY,"BT error. ("+e.getMessage()+")");
									return;
								}
								finally
								{
									if (srvSocket!=null)
									{
										try
										{
											if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"BT socket closing... "+uuid);
											srvSocket.close();
											if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"BT socket closed. "+uuid);
										}
										catch (IOException e)
										{
											if (W) Log.w(TAG_SERVER_BIND,PREFIX_LOG+"Impossible to close BT socket",e);
										}
									}
		
								}
	    			        }
	    			        if (E) Log.e(TAG_SERVER_BIND,PREFIX_LOG+"More error in bluetooth thread. Stop.");
	    				}
	
	    			},
	    			"BT server channel").start();
			}
//			catch (IOException e1)
//			{
//				// TODO Auto-generated catch block
//				if (E) Log.e(TAG_SERVER_BIND,PREFIX_LOG+"BT Error when start server",e1);
//			}
        	if (BT_LISTEN_ANONYMOUS && Build.VERSION.SDK_INT>=Compatibility.VERSION_GINGERBREAD_MR1)
        	{
        		try
				{
					final Method method=mAdapter.getClass().getMethod("listenUsingInsecureRfcommWithServiceRecord", new Class<?>[]{String.class,UUID.class});
					final BluetoothServerSocket srvSocket=(BluetoothServerSocket)method.invoke(mAdapter, "RemoteAndroid", uuid);
					mSrvSockets.add(srvSocket);
		        	new Thread(
		        			new Runnable()
		        			{
		        				public void run() 
		        				{
		        			        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
		        			        for (int i=0;i<BT_MAX_LOOP_BEFORE_STOP;++i)
		        			        {
//			        			        BluetoothServerSocket srvSocket=null;
										if (!mAdapter.isEnabled())
											return;
			    						try
			    						{
//			    	    					srvSocket=mAdapter.listenUsingInsecureRfcommWithServiceRecord("RemoteAndroid", uuid);
//			    							mSrvSockets.add(srvSocket);
			    	    					manageBluetoothSocket(uuid, srvSocket,false);
			    						}
			    						catch (IOException e)
			    						{
			    							mHandler.exceptionCaught(0,e);
			    						}
										catch (IllegalArgumentException e)
										{
											if (D) Log.d(TAG_SERVER_BIND,PREFIX_LOG+"Android version problem",e);
										}
										catch (Throwable e)
										{
											if (E) Log.e(TAG_DISCOVERY,"BT error.",e);
											return;
										}
										finally
										{
											if (srvSocket!=null)
											{
												try
												{
													srvSocket.close();
												}
												catch (IOException e)
												{
													if (W) Log.w(TAG_SERVER_BIND,PREFIX_LOG+"Impossible to close BT socket",e);
												}
											}
										}
		        			        }
		        			        if (E) Log.e(TAG_SERVER_BIND,PREFIX_LOG+"More error in bluetooth thread. Stop.");
		        				}
		
		        			},
		        			"BT anonymous server channel").start();
				}
				catch (IllegalAccessException e)
				{
					if (D) Log.d(TAG_SERVER_BIND,PREFIX_LOG+"Android version problem",e);
				}
				catch (InvocationTargetException e)
				{
					if (D) Log.d(TAG_SERVER_BIND,PREFIX_LOG+"Android version problem",e);
				}
				catch (SecurityException e)
				{
					if (D) Log.d(TAG_SECURITY,PREFIX_LOG+"Android version problem",e);
				}
				catch (NoSuchMethodException e)
				{
					if (D) Log.d(TAG_SERVER_BIND,PREFIX_LOG+"Android version problem",e);
				}
        	}
        }
    }
	private void manageBluetoothSocket(final UUID uuid,
			BluetoothServerSocket srvSocket,final boolean secure) throws IOException
	{
		try
		{
			for (;;)
			{
				if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"BT accept in "+uuid+"...");
				final BluetoothSocket socket=srvSocket.accept();
				if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"BT accept in "+uuid+" done.");
				if (Thread.interrupted())
					return;
				mExecutors.submit(new Runnable()
				{
					public void run() 
					{
				        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
						final BluetoothSocketChannel channel=new BluetoothSocketChannel(uuid,socket,secure);
						final int id=System.identityHashCode(channel);
						for (;;)
						{
							if (Thread.interrupted())
								return;
							try
							{
								final Msg msg=channel.read();
								if (!RemoteAndroidService.isActive()) continue;
								if (D) Log.d(TAG_SERVER_BIND,PREFIX_LOG+"BT Receive msg...");
								mExecutors.submit(new Runnable()
								{
									public void run()
									{
									    try
										{
											mHandler.messageReceived(id,(Msg)msg,channel);
										}
										catch (Exception e)
										{
											mHandler.exceptionCaught(id,e);
										}
									}
								});
							}
							catch (EOFException e)
							{
								if (I) Log.i(TAG_SERVER_BIND,PREFIX_LOG+"Server socket closed");
								//if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Server socket closed",e);
								mHandler.exceptionCaught(id,e);
								return;
							}
							catch (Exception e)
							{
								if (V && !e.getMessage().equals("Connection reset by peer")) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Server socket",e);
								mHandler.exceptionCaught(id,e);
								try
								{
									try {socket.getInputStream().close();} catch (Exception ee) {}
									try {socket.getOutputStream().close();} catch (Exception ee) {}
									if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"BT socket closing... "+uuid);
									socket.close();
									if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"BT socket closed. "+uuid);
								}
								catch (Exception e1)
								{
									// Ignore
									if (I) Log.i(TAG_SERVER_BIND,PREFIX_LOG+"Server socket",e1);
								}
								mHandler.channelClosed(id);
								return;
							}
							finally
							{
								Pairing.globalUnlock();
							}
						}
						
					}
				});
			}
		}
		catch (IOException e)
		{
			if (e.getMessage().indexOf("Canceled")!=0)
				return; // Sometime, with quick on/off
			throw e;
		}
	}
	
	// An agent to receive BT connection for runtime connections
	private static Thread sDiscoverAgent;
	private synchronized boolean startRuntimeDiscoverAgent()
	{
		if (sDiscoverAgent==null)
		{
			if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT Start client discover agent...");
			final BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
			try
			{
				final BluetoothServerSocket srvSocket = adapter.listenUsingRfcommWithServiceRecord("RemoteAndroidAgent", BluetoothSocketBossSender.sDiscoverUUID);
				sDiscoverAgent=new Thread(new Runnable()
				{
					
					@Override
					public void run()
					{
						try
						{
							if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT Client discover agent started.");
							for (;;)
							{
								BluetoothSocket socket=null;
								try
								{
									socket=srvSocket.accept();
									// FIXME
//									if (!mBoss.isDiscovering()) 
//										continue;
									// Read Identity message
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
										continue;
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
									Messages.Identity resp=Messages.Identity.newBuilder().mergeFrom(buf, 4, length).build();
									RemoteAndroidInfoImpl info=ProtobufConvs.toRemoteAndroidInfo(resp);
	
									if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"BT Discover remote android "+info.name+" at runtime");
									Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
									intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
									Application.sAppContext.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
								}
								catch (BindException e)
								{
									if (E && !D) Log.e(TAG_DISCOVERY,PREFIX_LOG+"BT local discover agent error ("+e.getMessage()+")");
									if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"BT local discover agent error",e);
									return;
								}
								catch (IOException e)
								{
									// Very special. It's a bug in bluetooth stack.
									// The hack is because the compile assum e can't be null and detect a dead-code and remove it.
									if (Application.hackNullException(e)==null)
									{
										if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"BT local discover agent error (null exception)");
										return;
									}
									String msg=e.getMessage();
									if (msg!=null)
									{
										if (e.getMessage().equals("Operation Canceled")) // BT OFF
										{
											if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"BT off");
											return;
										}
										if (e.getMessage().equals("Software caused connection abort")) // BT OFF
										{
											if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"BT off");
											return;
										}
									}
									else
									{
										if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"BT local discover agent error",e);
									}
								}
								finally
								{
									try
									{
										if (socket!=null) socket.close();
									}
									catch (IOException e)
									{
										// Ignore
									}
								}
							}
						}
						catch (Exception e)
						{
							if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"BT local discover agent error",e);
						}
						finally{}
						
					}
				},"Client service to accept bluetooth connection when discovering");
				sDiscoverAgent.start();
				return true;
			}
			catch (Exception e)
			{
				if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"BT local discover agent error",e);
			}
		}
		return false;
	}
}
