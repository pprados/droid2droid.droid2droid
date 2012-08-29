/******************************************************************************
 *
 * droid2droid - Distributed Android Framework
 * ==========================================
 *
 * Copyright (C) 2012 by Atos (http://www.http://atos.net)
 * http://www.droid2droid.org
 *
 ******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
******************************************************************************/
package org.droid2droid.binder.ip;

import static org.droid2droid.Constants.ETHERNET_LISTEN_PORT;
import static org.droid2droid.Constants.TAG_SERVER_BIND;
import static org.droid2droid.internal.Constants.D;
import static org.droid2droid.internal.Constants.ETHERNET;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.V;

import java.io.IOException;
import java.security.PublicKey;

import org.droid2droid.RAApplication;
import org.droid2droid.binder.AbstractProtobufSrvRemoteAndroid;
import org.droid2droid.binder.AbstractSrvRemoteAndroid;
import org.droid2droid.binder.UpstreamHandler;
import org.droid2droid.discovery.ip.IPDiscoverAndroids;
import org.droid2droid.internal.Messages.Msg;
import org.droid2droid.internal.Messages.Type;
import org.droid2droid.internal.socket.Channel;
import org.droid2droid.ui.Notifications;

import android.content.Context;
import android.util.Log;

// Implementation de Remote android, utilise un socket channel
public final class NetSocketRemoteAndroid extends AbstractProtobufSrvRemoteAndroid
{
	RAApplication mContext;
    private final int mListenPort;

    private static NetServerSocketChannel sServerSocket;
    private static Thread sThread;
    
    private static AbstractSrvRemoteAndroid sDaemonNet;
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
    private final UpstreamHandler mHandler=new UpstreamHandler()
    {

		@Override
		public void messageReceived(PublicKey clientKey,int id,Msg msg,Channel channel) throws Exception
		{
			Msg resp=doAndWriteReply(clientKey,id,msg,false);
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
		mContext=(RAApplication)context.getApplicationContext();
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
	public boolean connect(Type type,int flags,long cookie,long timeout)
	{
		throw new IllegalArgumentException(); // Not implemented in serveur.
	}
}
