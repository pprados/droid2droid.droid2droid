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

import static org.droid2droid.Constants.ETHERNET_KEEP_ALIVE;
import static org.droid2droid.Constants.ETHERNET_SO_TIMEOUT;
import static org.droid2droid.Constants.TAG_SERVER_BIND;
import static org.droid2droid.Constants.TLS_WANT_CLIENT_AUTH;
import static org.droid2droid.internal.Constants.D;
import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.ETHERNET_SO_LINGER;
import static org.droid2droid.internal.Constants.ETHERNET_SO_LINGER_TIMEOUT;
import static org.droid2droid.internal.Constants.I;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.TAG_SECURITY;
import static org.droid2droid.internal.Constants.TLS_IMPLEMENTATION_ALGORITHM;
import static org.droid2droid.internal.Constants.V;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;

import org.droid2droid.RAApplication;
import org.droid2droid.binder.UpstreamHandler;
import org.droid2droid.internal.Messages.Msg;
import org.droid2droid.internal.socket.ip.NetworkSocketChannel;
import org.droid2droid.login.LoginImpl;
import org.droid2droid.service.RemoteAndroidService;

import android.annotation.TargetApi;
import android.os.Process;
import android.util.Log;

import com.google.protobuf.MessageLite;

// Class en ecoute sur le socket.
// A chaque connexion, utilise un executor pour traiter le socket jusqu'a fermeture
final class NetServerSocketChannel implements Runnable
{
    static final MessageLite sPrototype=Msg.getDefaultInstance();

	protected ExecutorService mExecutors=Executors.newCachedThreadPool();
	protected UpstreamHandler mHandler;
    protected SSLServerSocket mSocket;
    private static final X509TrustManager[] sX509TrustManager=
    		new X509TrustManager[]
			{ 
				new X509TrustManager()
				{
					@Override
					public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
					{
						if (V) Log.v(TAG_SECURITY,"check client trusted");
					}
		
					@Override
					public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
					{
						if (V) Log.v(TAG_SECURITY,"check server trusted");
					}
		
					@Override
					public X509Certificate[] getAcceptedIssuers()
					{
						return new X509Certificate[0];
					}
				} 
			};
    
    NetServerSocketChannel(UpstreamHandler handler,int listenPort) throws IOException
    {
    	mHandler=handler;
    	try
    	{
	    	final SSLContext sslcontext = SSLContext.getInstance(TLS_IMPLEMENTATION_ALGORITHM);
			final KeyManager[] keyManagers=RAApplication.getKeyManager(); 
			sslcontext.init(
				keyManagers,
				sX509TrustManager, 
				RAApplication.getSecureRandom());
			mSocket=(SSLServerSocket)sslcontext.getServerSocketFactory().createServerSocket(listenPort);
			mSocket.setReuseAddress(true);
			if (TLS_WANT_CLIENT_AUTH)
				mSocket.setWantClientAuth(true);
    	}
		catch (NoSuchAlgorithmException e)
		{
			throw new Error(e);
		}
		catch (KeyManagementException e)
		{
			throw new Error(e);
		}
    	finally
    	{
    		
    	}
    }

    public void stop()
    {
    	try
		{
			mSocket.close();
		}
		catch (IOException e)
		{
    		if (E) Log.e(TAG_SERVER_BIND,PREFIX_LOG+"IP close socket exception",e);
		}
    	mSocket=null;
    	mExecutors.shutdown();
    }
    @Override
    public void run()
	{
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"IP accept call from "+mSocket.getInetAddress()+":"+mSocket.getLocalPort()+"...");
		//for (int i=0;i<ETHERNET_MAX_LOOP_BEFORE_STOP;++i) // FIXME: Flag non utilisé
        for (;;)
		{
			try
			{
				if (Thread.interrupted())
					return;
				if (mSocket==null) // Sometime, with quick on/off
					return;
				if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"IP Accept socket...");
				final SSLSocket socket=(SSLSocket)mSocket.accept();
				if (V) Log.v(TAG_SERVER_BIND,"IP Accept socket done.");
				socket.setTcpNoDelay(true);
				socket.setSoLinger(ETHERNET_SO_LINGER, ETHERNET_SO_LINGER_TIMEOUT);
				socket.setSoTimeout(ETHERNET_SO_TIMEOUT);
				socket.setKeepAlive(ETHERNET_KEEP_ALIVE);
				if (mExecutors.isShutdown()) return;
				mExecutors.execute(new Runnable()
				{
					@TargetApi(14)
					@Override
					public void run()
					{
//				    	if (VERSION.SDK_INT>=VERSION_CODES.ICE_CREAM_SANDWICH)
//				    	{
//				    		TrafficStats.setThreadStatsTag(ETHERNET_SOCKET_TAG);
//				    	}
						final int id=System.identityHashCode(socket);
						final NetworkSocketChannel channel=new NetworkSocketChannel(socket);
						PublicKey clientPublicKey;
						try
						{
							Certificate[] certificatesChaine=socket.getSession().getPeerCertificates();
							clientPublicKey=certificatesChaine[0].getPublicKey();
						}
						catch (SSLPeerUnverifiedException e)
						{
							// Ignore. Client without authentification
							clientPublicKey=null;
						}
						final PublicKey clientKey=clientPublicKey;
						
						for (;;)
						{
							if (Thread.interrupted())
								return;
							try
							{
								final Msg msg=channel.read();
								if (!RemoteAndroidService.isActive()) continue;
								if (D) Log.d(TAG_SERVER_BIND,PREFIX_LOG+"IP Receive msg...");
								mExecutors.execute(new Runnable()
								{
									@Override
									public void run()
									{
									    try
										{
											mHandler.messageReceived(clientKey,id,msg,channel);
										}
										catch (Exception e)
										{
											// TODO : BUG SI PERTE DE CONNEXION A CHAUD
											if (D) Log.d(TAG_SERVER_BIND,PREFIX_LOG+"TODO IP Receive exception",e);
										}
									}
								});
							}
							catch (Exception e)
							{
								try
								{
									if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Server socket ("+e.getMessage()+")");
									if (!socket.isClosed())
										mHandler.exceptionCaught(id,e);
									if (!socket.isClosed()) socket.close();
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
								LoginImpl.globalUnlock();
//						    	if (VERSION.SDK_INT>=VERSION_CODES.ICE_CREAM_SANDWICH)
//						    	{
//						    		TrafficStats.clearThreadStatsTag();
//						    	}
							}
						}
					}
				});
			}
			catch (IOException e)
			{
				mHandler.exceptionCaught(0,e);
			}
		}
        //if (E) Log.e(TAG_SERVER_BIND,PREFIX_LOG+"More error in ethernet thread. Stop.");
	}
    
}
