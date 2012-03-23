package org.remoteandroid.binder;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.Constants.PAIR_CHECK_WIFI_ANONYMOUS;
import static org.remoteandroid.Constants.PREFERENCES_ANO_ACTIVE;
import static org.remoteandroid.Constants.PREFERENCES_ANO_WIFI_LIST;
import static org.remoteandroid.Constants.TAG_SERVER_BIND;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.SECURITY;
import static org.remoteandroid.internal.Constants.TAG_SECURITY;
import static org.remoteandroid.internal.Constants.*;

import org.remoteandroid.Application;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.Cookies;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.binder.AbstractSrvRemoteAndroid.ConnectionContext.State;
import org.remoteandroid.discovery.Discover;
import org.remoteandroid.internal.AbstractRemoteAndroidImpl;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.login.LoginImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.Notifications;

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

    protected Msg doAndWriteReply(int connid,Msg msg,boolean btsecure) throws RemoteException, SendIntentException
	{
		final SharedPreferences preferences=Application.getPreferences();
		boolean acceptAnonymous=preferences.getBoolean(PREFERENCES_ANO_ACTIVE, false); //TODO: et pour BT ? Cf BT_DISCOVER_ANONYMOUS
    	ConnectionContext conContext = getContext(connid);
    	Parcel data=null;
    	Parcel reply=null;
    	try
    	{
    		Type type=msg.getType();
    		long timeout=msg.getTimeout();
            if (type==Type.PING)
            {
            	if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Ping");
    			return Msg.newBuilder()
					.setType(type)
					.setThreadid(msg.getThreadid())
					.build();
            }
            else if (type==Type.CONNECT_FOR_BROADCAST)
            {
            	if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Connect for "+type.name()+"...");
            	RemoteAndroidInfoImpl info=ProtobufConvs.toRemoteAndroidInfo(mContext,msg.getIdentity());
            	if (Trusted.getBonded(info.uuid.toString())==null && !acceptAnonymous)
            	{
	    			return Msg.newBuilder()
							.setType(msg.getType())
							.setThreadid(msg.getThreadid())
							.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS)
							.build();
            	}
            		
    			if (!PendingBroadcastRequest.notify(msg.getCookie(),info))
    			{
    				//Discover.getDiscover().discover(info);
    				Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
    				intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
    				intent.putExtra(RemoteAndroidManager.EXTRA_UPDATE, info);
    				Application.sAppContext.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
    				
    			}
    			return Msg.newBuilder()
						.setType(msg.getType())
						.setThreadid(msg.getThreadid())
						.setStatus(AbstractRemoteAndroidImpl.STATUS_OK)
						.build();
            }
            else if (type==Type.CONNECT || type==Type.CONNECT_FOR_PAIRING || type==Type.CONNECT_FOR_COOKIE || type==Type.CONNECT_FOR_DISCOVERING)
            {
            	if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Connect for "+type.name()+"...");
            	// Create a connection context ?
            	if (conContext==null)
            	{
            		conContext=new ConnectionContext();
            		// Save type
            		conContext.mType=getType();
            		// and remote infos
            		conContext.mClientInfo=ProtobufConvs.toRemoteAndroidInfo(mContext,msg.getIdentity());
		    		if (PAIR_AUTO_PAIR_BT_BOUNDED_DEVICE)
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
            	if (type==Type.CONNECT || type==Type.CONNECT_FOR_COOKIE || type==Type.CONNECT_FOR_DISCOVERING)
            	{
            		// Check connection with anonymous
	        		if (SECURITY && PAIR_CHECK_WIFI_ANONYMOUS && acceptAnonymous) // FIXME Si non actif , pairing ?
	        		{
	        			// Accept anonymous only from specific wifi network
	        			if (getType()==ConnectionType.ETHERNET)
	        			{
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
	    	    							break;
	    	    						}
	    	    				}
	        				}
//	        				if (SECURITY && !acceptAnonymous && !Trusted.isBonded(conContext.mClientInfo))
//	        				{
//	        					if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject anonymous call from '"+conContext.mClientInfo.getName()+'\'');
//	        					return Msg.newBuilder()
//	        						.setType(msg.getType())
//	        						.setThreadid(msg.getThreadid())
//	        						.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS)
//	        						.setIdentity(ProtobufConvs.toIdentity(Trusted.getInfo(mContext)))
//	        						.build();
//	        				}

	        			}
	        		}
	        		
        			if (SECURITY && (type==Type.CONNECT_FOR_COOKIE) && !acceptAnonymous && conContext.mLogin==null)
        			{
        				Application.removeCookie(conContext.mClientInfo.uuid.toString());
        				if (!Trusted.isBonded(conContext.mClientInfo))
        				{
                			if (conContext.mLogin==null) 
                				conContext.mLogin=new LoginImpl();
                			if (PAIR_AUTO_IF_NO_COOKIE)
                			{
                				if (I) Log.i(TAG_SECURITY,PREFIX_LOG+"Reject no bounded device '"+conContext.mClientInfo.getName()+'\'');
                			}
                			else
                			{
                				if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject no bounded device '"+conContext.mClientInfo.getName()+'\'');
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
            	}
            	
        		if (SECURITY && type==Type.CONNECT_FOR_COOKIE || type==Type.CONNECT_FOR_PAIRING)
        		{
        			// Connection to receive a cookie, then close. Must be called only from RemoteAndroid.apk
        			// Because the others applications can't known the private key.
        			final long cookie=getCookie(conContext);
        			// TODO: Pas de chalenge si BT sécure
        			if (conContext.mLogin==null) 
        				conContext.mLogin=new LoginImpl();
        			return conContext.mLogin.server(conContext,msg,cookie);
        		}
        		conContext.mLogin=null;
        		
        		// Must present a valid cookie
        		if (SECURITY && type==Type.CONNECT)
        		{
                	Msg msgCookie=checkCookie(msg, conContext);
        			if (msgCookie!=null)
        				return msgCookie;
	            	conContext.mState=State.CONNECTED;
        		}
//        		if (type==Type.CONNECT_FOR_PAIRING)
//        			conContext.mState=State.CONNECTED_FOR_PAIRING;
        		if (!SECURITY)
        			conContext.mState=State.CONNECTED;
            	return Msg.newBuilder()
					.setType(type)
        			.setThreadid(msg.getThreadid())
					.setIdentity(ProtobufConvs.toIdentity(Trusted.getInfo(mContext)))
					.setRc(true)
					.build();
            }
//            else if (type==Type.PAIRING_CHALENGE)
//            {
//            	if (conContext.mState!=State.CONNECTED_FOR_PAIRING)
//            	{
//            		if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Not connected for pairing");
//            		close();
//            		return null;
//            	}
//    			final long cookie=getCookie(conContext);
//            	return transactPairingChalenge(connid,msg,cookie);
//            }
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

	private Msg checkCookie(Msg msg, ConnectionContext conContext)
	{
		long cookie=Application.getCookie(conContext.mClientInfo.uuid.toString());
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
		if (clientCookie!=cookie)
		{
			if (I) Log.i(TAG_SECURITY,PREFIX_LOG+"Invalide cookie '"+cookie+"' from '"+conContext.mClientInfo.getName()+'\'');
			return Msg.newBuilder()
				.setType(msg.getType())
				.setThreadid(msg.getThreadid())
				.setStatus(AbstractRemoteAndroidImpl.STATUS_INVALIDE_COOKIE)
				.build();
		}
		return null;
	}

    private long getCookie(ConnectionContext conContext)
    {
    	String strUUID=conContext.mClientInfo.uuid.toString();
		long cookie=Application.getCookie(strUUID);
		if (cookie==COOKIE_NO)
		{
			cookie=Application.randomNextLong();
			if (cookie==COOKIE_NO) cookie=1; // Zero: no cookie, -1: exception when load cookie
			Application.addCookie(strUUID, cookie);
			if (V) Log.v(TAG_SECURITY,PREFIX_LOG+"Set cookie for '"+conContext.mClientInfo.uuid+"' : "+cookie);
		}
    	return cookie;
    }
    
    protected ConnectionType getType()
    {
    	return ConnectionType.ETHERNET;
    }

}
