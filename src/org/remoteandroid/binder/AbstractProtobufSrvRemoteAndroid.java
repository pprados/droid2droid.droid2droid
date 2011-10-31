package org.remoteandroid.binder;

import static org.remoteandroid.Constants.PAIR_AUTO_PAIR_BT_BOUNDED_DEVICE;
import static org.remoteandroid.Constants.PAIR_CHECK_ANONYMOUS;
import static org.remoteandroid.Constants.PREFERENCES_ANO_ACTIVE;
import static org.remoteandroid.Constants.PREFERENCES_ANO_WIFI_LIST;
import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.internal.Constants.*;

import org.remoteandroid.Application;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.binder.AbstractSrvRemoteAndroid.ConnectionContext.State;
import org.remoteandroid.internal.AbstractRemoteAndroidImpl;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.login.LoginImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.pairing.SimplePairing;
import org.remoteandroid.ui.Notifications;

import android.content.Context;
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
    	ConnectionContext conContext = getContext(connid);
    	Parcel data=null;
    	Parcel reply=null;
    	try
    	{
    		Type type=msg.getType();
    		long timeout=msg.getTimeout();
            if (type==Type.PING)
            {
    			return Msg.newBuilder()
					.setType(type)
					.setThreadid(msg.getThreadid())
					.build();
            }
            else if (type==Type.CONNECT || type==Type.CONNECT_FOR_PAIRING || type==Type.CONNECT_FOR_COOKIE || type==Type.CONNECT_FOR_DISCOVERING)
            {
            	// Differences kind of connection
            	if (conContext==null)
            	{
            		// Create a connection context
            		conContext=new ConnectionContext();
            		conContext.mType=getType();
            		conContext.mClientInfo=ProtobufConvs.toRemoteAndroidInfo(msg.getIdentity());
		    		if (PAIR_AUTO_PAIR_BT_BOUNDED_DEVICE)
		    		{
                		// Auto register binded bluetooth devices
                		if (btsecure && !Trusted.isBonded(conContext.mClientInfo))
                		{
				    		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"BT Auto register bonded "+conContext.mClientInfo.getName()+" with a connection.");
                			Trusted.registerDevice(mContext, conContext);
                		}
		    		}
		    		setContext(connid,conContext);
            	}
            	if (type==Type.CONNECT || type==Type.CONNECT_FOR_DISCOVERING || type==Type.CONNECT_FOR_COOKIE)
            	{
            		// Would like to connect for using
	    			final SharedPreferences preferences=Application.getPreferences();
	    			boolean acceptAnonymous=preferences.getBoolean(PREFERENCES_ANO_ACTIVE, false); //TODO: et pour BT ? Cf BT_DISCOVER_ANONYMOUS
	        		if (SECURITY && PAIR_CHECK_ANONYMOUS && acceptAnonymous) // FIXME Si non actif , pairing ?
	        		{
	        			// Accept only from specific wifi network
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
	        				if (SECURITY && !acceptAnonymous && !Trusted.isBonded(conContext.mClientInfo))
	        				{
	        					if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject anonymous call from "+conContext.mClientInfo.getName());
	        					return Msg.newBuilder()
	        						.setType(msg.getType())
	        						.setThreadid(msg.getThreadid())
	        						.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS)
	        						.build();
	        				}

	        			}
	        		}
        			if (SECURITY && (type==Type.CONNECT_FOR_COOKIE) && !acceptAnonymous)
        			{
        				if (!Trusted.isBonded(conContext.mClientInfo))
        				{
        					if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject no bounded device "+conContext.mClientInfo.getName());
        					return Msg.newBuilder()
        						.setType(msg.getType())
        						.setThreadid(msg.getThreadid())
        						.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_NO_BOUND)
        						.build();
        				}
        			}
            	}
        		if (SECURITY && type==Type.CONNECT_FOR_COOKIE)
        		{
        			// Connection for receive a cookie, then close. Must be called only from RemoteAndroid.apk
        			// Because the others applications can't known the private key.
        			long cookie=Application.getCookie(conContext.mClientInfo.uuid.toString());
        			if (cookie==0)
        			{
        				cookie=Application.sRandom.nextLong();
        				if (cookie==0) cookie=1;
        				Application.addCookie(conContext.mClientInfo.uuid.toString(), cookie);
            			if (V) Log.v(TAG_SECURITY,PREFIX_LOG+"Set cookie for "+conContext.mClientInfo.uuid+" : "+cookie);
        			}
        			// TODO: Pas de chalenge si BT sécure
        			if (conContext.mLogin==null) 
        				conContext.mLogin=new LoginImpl();
        			return conContext.mLogin.server(conContext,msg,cookie);
        		}
        		// Must present a valid cookie
        		if (SECURITY && type==Type.CONNECT)
        		{
        			long cookie=Application.getCookie(conContext.mClientInfo.uuid.toString());
        			if (V) Log.v(TAG_SECURITY,PREFIX_LOG+"Get cookie for "+conContext.mClientInfo.uuid+" is "+cookie);
	        		long clientCookie=msg.getCookie();
	        		if (cookie==0)
	        		{
						if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject connection from "+conContext.mClientInfo.getName()+" without cookie.");
						return Msg.newBuilder()
							.setType(msg.getType())
							.setThreadid(msg.getThreadid())
							.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS)
							.build();
	        		}
	        		if (clientCookie!=cookie)
	        		{
						if (I) Log.i(TAG_SECURITY,PREFIX_LOG+"Invalide cookie "+cookie+" from "+conContext.mClientInfo.getName());
						return Msg.newBuilder()
							.setType(msg.getType())
							.setThreadid(msg.getThreadid())
							.setStatus(AbstractRemoteAndroidImpl.STATUS_INVALIDE_COOKIE)
							.build();
	        		}
	            	conContext.mState=State.CONNECTED;
        		}
        		if (type==Type.CONNECT_FOR_PAIRING)
        			conContext.mState=State.CONNECTED_FOR_PAIRING;
        		if (!SECURITY)
        			conContext.mState=State.CONNECTED;
            	return Msg.newBuilder()
					.setType(type)
        			.setThreadid(msg.getThreadid())
					.setIdentity(ProtobufConvs.toIdentity(Trusted.getInfo(mContext,getType())))
					.setRc(true)
					.build();
            }
            else if (type==Type.PAIRING_CHALENGE)
            {
            	if (conContext.mState!=State.CONNECTED_FOR_PAIRING)
            	{
            		if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Not connected for pairing");
            		close();
            		return null;
            	}
            	return transactPairingChalenge(connid,msg);
            }
            if (conContext==null)
            {
        		if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Invalide state");
            	close();
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

    protected ConnectionType getType()
    {
    	return ConnectionType.ETHERNET;
    }

	private Msg transactPairingChalenge(int connid,Msg msg)
	{
    	ConnectionContext context=getContext(connid);
    	
    	if (context.mPairing==null)
    	{
    		context.mPairing=new SimplePairing(mContext,Application.sHandler,context.mClientInfo, 
    				Application.sManager.getInfos(),getType());
    	}
    	
		return context.mPairing.server(context,msg);
	}

}
