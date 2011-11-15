package org.remoteandroid.binder.bluetooth;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.BLUETOOTH;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.V;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.remoteandroid.Application;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.Constants;
import org.remoteandroid.binder.AbstractProtobufSrvRemoteAndroid;
import org.remoteandroid.binder.UpstreamHandler;
import org.remoteandroid.discovery.bluetooth.BluetoothBroadcast;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.socket.Channel;
import org.remoteandroid.service.RemoteAndroidService;
import org.remoteandroid.ui.Notifications;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


// A bluetooth connection to a remote android
public class BluetoothRemoteAndroid extends AbstractProtobufSrvRemoteAndroid
{
	private BluetoothServerSocketChannel mServerSocket;
    static ExecutorService sExecutors=Executors.newCachedThreadPool();
    
    private static BluetoothRemoteAndroid sDaemonBluetooth;
    public static void startDaemon(Context context,Notifications notifications)
    {
//        if (BLUETOOTH && BluetoothAdapter.getDefaultAdapter()!=null)
//        {
//        	BluetoothBroadcast.startBluetoothManager(context);
//	    	sDaemonBluetooth=new BluetoothRemoteAndroid(context, notifications);
//	    	sDaemonBluetooth.start();
//        }
    	
    }
    public static void restart()
    {
    	sDaemonBluetooth.mServerSocket.run();
    }
    
    public static void stopDaemon()
    {
    	if (sDaemonBluetooth!=null) 
    	{
    		sDaemonBluetooth.stop();
        	sDaemonBluetooth=null;
    	}
    }
    private UpstreamHandler mHandler=new UpstreamHandler()
    {

		@Override
		public void messageReceived(int id,Msg msg,Channel channel) throws Exception
		{
        	Msg reply=doAndWriteReply(id,msg,channel.isBluetoothSecure());
        	if (reply!=null)
        		channel.write(reply);
		}

		@Override
		public void channelOpen(int id)
		{
			// TODO Auto-generated method stub
		}

		@Override
		public void channelClosed(int id)
		{
			connectionClose(id);
			// TODO: supprimer les notifications de download
	        Intent intent = new Intent(mContext,RemoteAndroidService.class);
	        intent.setAction(Constants.ACTION_CLEAR_DOWNLOAD);
	        mContext.startService(intent);
			
		}

		@Override
		public void exceptionCaught(int id,Throwable e)
		{
			if (D) Log.d(TAG_SERVER_BIND,PREFIX_LOG+"Exception "+e.getMessage());
            if (V && !D) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Exception",e); // FIXME: Gestion d'exception dans upstream handler
		}
    	
    };
	
	private BluetoothRemoteAndroid(Context context,Notifications notifications)
	{
		super(context,notifications);
	}
	
	@Override
	public ConnectionType getType()
	{
		return ConnectionType.BT;
	}
	@Override
	public void start()
	{
		super.start();
		if (BT_HACK_CREATE_LISTEN_IN_MAIN_THREAD)
		{
			// TODO: essais dans main threa
			Application.sHandler.post(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						mServerSocket=new BluetoothServerSocketChannel(sExecutors, mHandler);
						//sExecutors.execute(mServerSocket);
						mServerSocket.run();
					}
					catch (IOException e)
					{
						if (E) Log.e(TAG_SERVER_BIND,PREFIX_LOG+"Start failed",e);
					}
				}
			});
		}
		else
		{
			try
			{
				mServerSocket=new BluetoothServerSocketChannel(sExecutors, mHandler);
				sExecutors.execute(mServerSocket);
			}
			catch (IOException e)
			{
				if (E) Log.e(TAG_SERVER_BIND,PREFIX_LOG+"Start failed",e);
			}
		}
	}
	
	@Override
	public void stop()
	{
		super.stop();
		mServerSocket.stop();
	}

	@Override
	public void close()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void disconnect(int connid)
	{
		// TODO Auto-generated method stub
		// Disconnect the connexion
	}

	@Override
	public boolean connect(boolean forPairing,long timeout)
	{
		// TODO Auto-generated method stub
		return false;
	}

}
