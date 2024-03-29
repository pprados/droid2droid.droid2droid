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
package org.droid2droid.binder;

import static org.droid2droid.Constants.PAIR_AUTO_IF_NO_COOKIE;
import static org.droid2droid.Constants.PAIR_AUTO_PAIR_BT_BONDED_DEVICE;
import static org.droid2droid.Constants.PAIR_CHECK_WIFI_ANONYMOUS;
import static org.droid2droid.Constants.PREFERENCES_ANO_ACTIVE;
import static org.droid2droid.Constants.PREFERENCES_ANO_WIFI_LIST;
import static org.droid2droid.Constants.TAG_SERVER_BIND;
import static org.droid2droid.Droid2DroidManager.ACTION_DISCOVER_ANDROID;
import static org.droid2droid.Droid2DroidManager.EXTRA_DISCOVER;
import static org.droid2droid.Droid2DroidManager.EXTRA_UPDATE;
import static org.droid2droid.Droid2DroidManager.FLAG_REMOVE_PAIRING;
import static org.droid2droid.Droid2DroidManager.PERMISSION_DISCOVER_RECEIVE;
import static org.droid2droid.internal.Constants.COOKIE_NO;
import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.I;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.SECURITY;
import static org.droid2droid.internal.Constants.TAG_SECURITY;
import static org.droid2droid.internal.Constants.V;

import java.security.PublicKey;

import org.droid2droid.ConnectionType;
import org.droid2droid.RAApplication;
import org.droid2droid.binder.AbstractSrvRemoteAndroid.ConnectionContext.State;
import org.droid2droid.internal.AbstractRemoteAndroidImpl;
import org.droid2droid.internal.Messages.Msg;
import org.droid2droid.internal.Messages.Type;
import org.droid2droid.internal.Pairing;
import org.droid2droid.internal.ProtobufConvs;
import org.droid2droid.internal.RemoteAndroidInfoImpl;
import org.droid2droid.login.LoginImpl;
import org.droid2droid.pairing.PairingImpl;
import org.droid2droid.pairing.Trusted;
import org.droid2droid.ui.Notifications;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import com.google.protobuf.ByteString;

public abstract class AbstractProtobufSrvRemoteAndroid extends AbstractSrvRemoteAndroid
{
	
	public AbstractProtobufSrvRemoteAndroid(Context context,Notifications notifications)
	{
		super(context,notifications);
	}

    private Parcel byteStringToparcel(ByteString bs)
    {
    	Parcel data=Parcel.obtain();
    	data.setDataPosition(0);
    	byte[] buf=bs.toByteArray();
    	data.unmarshall(buf, 0, buf.length);
    	data.setDataPosition(0);
    	return data; 
    }
    private ByteString parcelToByteString(Parcel p)
    {
    	return ByteString.copyFrom(p.marshall());
    }

    protected Msg doAndWriteReply(PublicKey clientKey,int connid,Msg msg,boolean btsecure) throws RemoteException, SendIntentException
	{
		final SharedPreferences preferences=RAApplication.getPreferences();
		boolean acceptAnonymous=preferences.getBoolean(PREFERENCES_ANO_ACTIVE, false); //TODO: et pour BT ? Cf BT_DISCOVER_ANONYMOUS
    	ConnectionContext conContext = getContext(connid);
    	Parcel data=null;
    	Parcel reply=null;
    	try
    	{
    		Type type=msg.getType();
        	if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"-> "+ type.toString());
    		long timeout=msg.getTimeout();
            if (type==Type.PING)
            {
    			return Msg.newBuilder()
					.setType(type)
					.setThreadid(msg.getThreadid())
					.build();
            }
            else if (type==Type.CONNECT_FOR_BROADCAST)
            {
            	RemoteAndroidInfoImpl info=ProtobufConvs.toRemoteAndroidInfo(mContext,msg.getIdentity());
            		
    			if (!PendingBroadcastRequest.notify(msg.getCookie(),info))
    			{
            		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"== Broadcast discover new RA");
    				Intent intent=new Intent(ACTION_DISCOVER_ANDROID);
    				intent.putExtra(EXTRA_DISCOVER, info);
    				intent.putExtra(EXTRA_UPDATE, info);
    				RAApplication.sAppContext.sendBroadcast(intent,PERMISSION_DISCOVER_RECEIVE);
    				
    			}
        		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"<- Status ok");
    			return Msg.newBuilder()
						.setType(msg.getType())
						.setThreadid(msg.getThreadid())
						.setStatus(AbstractRemoteAndroidImpl.STATUS_OK)
						.build();
            }
            else if (type==Type.CONNECT || type==Type.CONNECT_FOR_COOKIE || type==Type.CONNECT_FOR_DISCOVERING)
            {
            	boolean isTrusted=false;
            	// Create a connection context ?
            	if (conContext==null)
            	{
            		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Create connexion context");
            		conContext=new ConnectionContext();
            		// Save type
            		conContext.mType=getType();
            		// and remote infos
            		conContext.mClientInfo=ProtobufConvs.toRemoteAndroidInfo(mContext,msg.getIdentity());
            		isTrusted=Pairing.isTemporaryAcceptAnonymous() || Trusted.isBonded(conContext.mClientInfo);
		    		if (PAIR_AUTO_PAIR_BT_BONDED_DEVICE) // FIXME: pas si for discovering
		    		{
                		// Auto register binded bluetooth devices
                		if (btsecure && !Trusted.isBonded(conContext.mClientInfo))
                		{
				    		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"BT Auto register bonded "+conContext.mClientInfo.getName()+" with a connection.");
                			Trusted.registerDevice(mContext, conContext);
                		}
		    		}
		    		setContext(connid,conContext);
            	}
        		// Check connection with anonymous
        		if (SECURITY && PAIR_CHECK_WIFI_ANONYMOUS && acceptAnonymous)
        		{
        			// Accept anonymous only from specific wifi network
        			if (getType()==ConnectionType.ETHERNET)
        			{
                		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Check wifi network before accept anonymous");
        				WifiManager manager=(WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
        				if (manager!=null)
        				{
    	    				String[] wifis=preferences.getString(PREFERENCES_ANO_WIFI_LIST,"").split(",");
    	    				if (!"#ALL#".equals(wifis[0]))
    	    				{
    	        				acceptAnonymous=false;
    	    					String wifiname=manager.getConnectionInfo().getSSID();
    	    					for (int i=wifis.length-1;i>=0;--i)
    	    						if (wifis[i].equals(wifiname))
    	    						{
    	    							acceptAnonymous=true;
    	    	                		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Accept anonymous if wifi "+wifiname);
    	    							break;
    	    						}
    	    				}
        				}
        			}
        		}
        		
    			if (SECURITY && (type==Type.CONNECT_FOR_COOKIE) && !acceptAnonymous && conContext.mLogin==null)
    			{
    				RAApplication.removeCookie(conContext.mClientInfo.uuid.toString()); // FIXME
    				if (!isTrusted)
    				{
            			if (conContext.mLogin==null)
            			{
	                		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Register login context");
            				conContext.mLogin=new LoginImpl(clientKey);
            			}
            			if (PAIR_AUTO_IF_NO_COOKIE)
            			{
            				if (I) Log.i(TAG_SECURITY,PREFIX_LOG+"<- Reject no bonded device '"+conContext.mClientInfo.getName()+'\'');
            			}
            			else
            			{
            				if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"<- Reject no bonded device '"+conContext.mClientInfo.getName()+'\'');
            			}
    					return Msg.newBuilder()
    						.setType(msg.getType())
    						.setThreadid(msg.getThreadid())
    						.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_NO_BOUND)
    						.setIdentity(ProtobufConvs.toIdentity(Trusted.getInfo(mContext)))
    						.setChallengestep(11)
							.build();
    				}
    			}
            	
        		if (SECURITY && type==Type.CONNECT_FOR_COOKIE)
        		{
        			// Connection to receive a cookie, then close. Must be called only from RemoteAndroid.apk
        			// because the others applications can't known the private key.
        			final long cookie=getCookie(conContext);
        			// TODO: Pas de chalenge si BT sécure
        			if (conContext.mLogin==null)
        			{
                		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Register login context");
        				conContext.mLogin=new LoginImpl(clientKey);
        			}
        			if ((msg.getFlags() & FLAG_REMOVE_PAIRING)!=0)
        			{
        				Trusted.unregisterDevice(RAApplication.sAppContext,conContext.mClientInfo);
        				return Msg.newBuilder()
        						.setType(type)
        						.setThreadid(Thread.currentThread().getId())
        						.setRc(true)
        						.build();
        			}
    				if (msg.getPairing() && conContext.mPairing==null)
    				{
    					conContext.mPairing=new PairingImpl();
    				}

            		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Enter in login steps");
            		if (msg.getChallengestep()>10)
            		{
            			return conContext.mPairing.server(conContext,msg,cookie);
            		}
            		else
            			return conContext.mLogin.server(conContext,msg,cookie,Pairing.isTemporaryAcceptAnonymous() | acceptAnonymous);
        		}
        		conContext.mLogin=null;
        		conContext.mPairing=null;
        		
        		Msg.Builder builder=Msg.newBuilder();
        		// Must present a valid cookie
        		if (SECURITY && type==Type.CONNECT)
        		{
                	Msg msgCookie=checkCookie(msg, builder,conContext);
        			if (msgCookie!=null)
        				return msgCookie;
            		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Connection state:connected");
	            	conContext.mState=State.CONNECTED;
        		}
        		if (!SECURITY)
        			conContext.mState=State.CONNECTED;
        		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"<- OK");
            	return builder
					.setType(type)
        			.setThreadid(msg.getThreadid())
					.setIdentity(ProtobufConvs.toIdentity(Trusted.getInfo(mContext)))
					.setRc(true)
					.build();
            }
            if (conContext==null)
            {
        		if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Invalide state");
            	close();
            	return null;
            }
        	if (conContext.mState!=State.CONNECTED)
        	{
        		if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Not connected");
        		close();
        		return null;
        	}
            if (type==Type.TRANSACT_RemoteAndroid)
            {
            	int code=msg.getCmd();
        		data=byteStringToparcel(msg.getData());
            	int flags=msg.getFlags();
            	reply=Parcel.obtain();
            	boolean rc=transactRemoteAndroid(connid,code,data,reply,flags,timeout);
            	reply.setDataPosition(0);
            	return Msg.newBuilder()
        			.setType(type)
        			.setThreadid(msg.getThreadid())
					.setRc(rc)
					.setReply(parcelToByteString(reply))
        			.build();
            }
            else if (type==Type.TRANSACT_Binder)
            {
            	int oid=msg.getOid();
            	int code=msg.getCmd();
        		data=byteStringToparcel(msg.getData());
            	int flags=msg.getFlags();
            	reply=Parcel.obtain();
        		data=updateData(data);
            	boolean rc=transactBinder(connid,oid, code, data, reply, flags,timeout);//TODO: if transact...
            	
    			return Msg.newBuilder()
    				.setType(type)
    				.setThreadid(msg.getThreadid())
					.setRc(rc)
					.setReply(parcelToByteString(reply))
    				.build();
            }
            else if (type==Type.TRANSACT_Apk)
            {
            	int code=msg.getCmd();
        		data=byteStringToparcel(msg.getData());
            	int flags=msg.getFlags();
            	reply=Parcel.obtain();
            	boolean rc=transactApk(connid,code,data,reply,flags,timeout);
    			return Msg.newBuilder()
					.setType(type)
					.setThreadid(msg.getThreadid())
					.setRc(rc)
					.setReply(parcelToByteString(reply))
					.build();
            }
            return null;
    	}
    	finally
    	{
    		if (data!=null) data.recycle();
    		if (reply!=null) reply.recycle();
    	}
		
	}

	private final long srvCookie(long cookie)
	{
		return cookie & 0xFFFF0000;
	}
	private final long cliCookie(long cookie)
	{
		return cookie & 0xFFFF;
	}    
	private Msg checkCookie(Msg msg, Msg.Builder builder,ConnectionContext conContext)
	{
		long cookie=RAApplication.getCookie(conContext.mClientInfo.uuid.toString());
		if (V) Log.v(TAG_SECURITY,PREFIX_LOG+"Get cookie for '"+conContext.mClientInfo.uuid+"' is "+cookie);
		long clientCookie=msg.getCookie();
		if (cookie==COOKIE_NO)
		{
			if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject connection from '"+conContext.mClientInfo.getName()+"' without cookie.");
			return Msg.newBuilder()
				.setType(msg.getType())
				.setThreadid(msg.getThreadid())
				.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS)
				.setChallengestep(11)
				.build();
		}
		if (clientCookie!=cliCookie(cookie))
		{
			if (I) Log.i(TAG_SECURITY,PREFIX_LOG+"Invalide cookie '"+cookie+"' from '"+conContext.mClientInfo.getName()+'\'');
			return Msg.newBuilder()
				.setType(msg.getType())
				.setThreadid(msg.getThreadid())
				.setStatus(AbstractRemoteAndroidImpl.STATUS_INVALIDE_COOKIE)
				.build();
		}
		builder.setCookie(srvCookie(cookie));
		return null;
	}

    private Long getCookie(ConnectionContext conContext)
    {
    	String strUUID=conContext.mClientInfo.uuid.toString();
		long cookie=RAApplication.getCookie(strUUID);
		if (cookie==COOKIE_NO)
		{
			cookie=RAApplication.randomNextLong();
			if (cookie==COOKIE_NO) cookie=1; // Zero: no cookie, -1: exception when load cookie
			RAApplication.addCookie(strUUID, cookie);
			if (V) Log.v(TAG_SECURITY,PREFIX_LOG+"Set cookie for '"+conContext.mClientInfo.uuid+"' : "+cookie);
		}
    	return cookie;
    }
    
    @Override
	protected ConnectionType getType()
    {
    	return ConnectionType.ETHERNET;
    }

}
