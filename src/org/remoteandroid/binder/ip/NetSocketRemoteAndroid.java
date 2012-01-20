package org.remoteandroid.binder.ip;

import static org.remoteandroid.Constants.ETHERNET_LISTEN_PORT;
import static org.remoteandroid.Constants.TAG_SERVER_BIND;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.ETHERNET;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;

import java.io.IOException;

import org.remoteandroid.binder.AbstractProtobufSrvRemoteAndroid;
import org.remoteandroid.binder.AbstractSrvRemoteAndroid;
import org.remoteandroid.binder.UpstreamHandler;
import org.remoteandroid.discovery.ip.IPDiscoverAndroids;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.socket.Channel;
import org.remoteandroid.ui.Notifications;

import android.content.Context;
import android.util.Log;

// Implementation de Remote android, utilise un socket channel
public class NetSocketRemoteAndroid extends AbstractProtobufSrvRemoteAndroid
{
	Context mContext;
    private int mListenPort;

    private static NetServerSocketChannel sServerSocket;
    private static Thread sThread;
//    private static IPV4SrvDiscoverAndroid sDiscoverAgent;
    
    static AbstractSrvRemoteAndroid sDaemonNet;
    public static void startDaemon(final Context context,final Notifications notifications)
    {
        if (!ETHERNET) return;
        if (sDaemonNet==null)
        {
        	sDaemonNet=new NetSocketRemoteAndroid(context, notifications);
        	sDaemonNet.start();
        	IPDiscoverAndroids.asyncRegisterService();
        }
    }
    public static void stopDaemon(Context context)
    {
        if (!ETHERNET) return;
		if (sDaemonNet!=null)
		{
			sDaemonNet.stop();
			sDaemonNet=null;
        	IPDiscoverAndroids.asyncUnregisterService();
		}
    }
    public static boolean isStarted()
    {
    	return sDaemonNet!=null;
    }
    // Bridge between server channel and NetSocketRemoteAndroid
    private UpstreamHandler mHandler=new UpstreamHandler()
    {

		@Override
		public void messageReceived(int id,Msg msg,Channel channel) throws Exception
		{
			Msg resp=doAndWriteReply(id,msg,false);
			if (resp!=null)
				channel.write(resp);
		}

		@Override
		public void channelOpen(int id)
		{
		}

		@Override
		public void channelClosed(int id)
		{
			connectionClose(id);
		}

		@Override
		public void exceptionCaught(int id,Throwable e)
		{
			if (D) Log.d(TAG_SERVER_BIND,PREFIX_LOG+"Exception "+((e!=null) ? e.getMessage():"(null)"));
            //if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Exception",e); // FIXME Exception non traité
		}
    	
    };
    private NetSocketRemoteAndroid(Context context,Notifications notifications)
	{
    	super(context,notifications);
		mContext=context.getApplicationContext();
		mListenPort=ETHERNET_LISTEN_PORT;
	}
    @Override
    public void start()
    {
		try
		{
			if (sServerSocket==null)
			{
				sServerSocket=new NetServerSocketChannel(mHandler,mListenPort);
				sThread=new Thread(sServerSocket,"IP Server channel *:"+mListenPort);
				sThread.setDaemon(true);
			}
			if (!sThread.isAlive())
			{
				if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Restart the server channel");
				sThread.start();
			}
		}
		catch (IOException e)
		{
			Log.e(TAG_SERVER_BIND,PREFIX_LOG+"Start failed",e); // FIXME. Cas du port deja utilisé par exemple.
			sThread.interrupt();
		}
    }
    @Override
    public void stop()
    {
    	super.stop();
    	if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG + "executor shutdown now");
    	if (sServerSocket!=null)
    		sServerSocket.stop();
    	sServerSocket=null;
    }
	@Override
	public void close()
	{
		stop();
	}
	@Override
	public void disconnect(int connid)
	{
		close(); // TODO disconnect the connexion
	}
	@Override
	public boolean connect(boolean forPairing,long timeout)
	{
		// FIXE: Why ?
		return false;
	}
}
