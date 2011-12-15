package org.remoteandroid.pairing;

import static org.remoteandroid.Constants.LOCK_ASK_PAIRING;
import static org.remoteandroid.Constants.TIMEOUT_ASK_PAIR;
import static org.remoteandroid.internal.Constants.*;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

import org.remoteandroid.Application;
import org.remoteandroid.CommunicationWithLock;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.binder.AbstractSrvRemoteAndroid.ConnectionContext;
import org.remoteandroid.internal.AbstractProtoBufRemoteAndroid;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.ProtobufConvs;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

import com.google.protobuf.ByteString;

// TODO: Vérifier la bonne utilisation du global lock, lors de la perte de connexion, et gérer les erreurs correspondantes.

public class SimplePairing extends Pairing
{
	
    private static final String HASH_ALGORITHM="SHA-256";
    private static final int NONCE_BYTES_NEEDED=5;
    
	public static final String byteToString(byte[] bytes,int max)
	{
	    BigInteger bigint = new BigInteger(1, bytes);
	    String digits=bigint.toString();
	    return (digits.length()>max) 
	    	? digits.substring(digits.length()-max)
	    	: digits;
	}
	
	private final Handler mHandler;
	private final RemoteAndroidInfo mClientInfo;
	private final RemoteAndroidInfo mServerInfo;
	private final MessageDigest mDigest;
	private final ConnectionType mType;
	
//	public SimplePairing(Context appContext,Handler handler,RemoteAndroidInfo clientInfo,RemoteAndroidInfo serverInfo)
//	{
//		this(appContext,handler,clientInfo,serverInfo,null);
//	}
	public SimplePairing(Context appContext,
			Handler handler,
			RemoteAndroidInfo clientInfo,
			RemoteAndroidInfo serverInfo,
			ConnectionType type)
	{
		super(appContext);
		mHandler=handler;
		mClientInfo=clientInfo;
		mServerInfo=serverInfo;
		mType=type;
	    try
		{
			mDigest = MessageDigest.getInstance(HASH_ALGORITHM);
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new InternalError(e.getMessage());
		}
	}
	public boolean client(AbstractProtoBufRemoteAndroid remoteAndroid,String uri,long timeout)
	{
		try
		{
			if (isGlobalLock()) 
				return false;
		    final long threadid = Thread.currentThread().getId();
		    Msg msg;
			
		    byte[] nonceA = new byte[NONCE_BYTES_NEEDED];
	        Application.sRandom.nextBytes(nonceA);

	        // 1. Send hash(Pka,Pkb,nonce);
			RSAPublicKey clientPubRsa=(RSAPublicKey)mClientInfo.getPublicKey();
			RSAPublicKey serverPubRsa=(RSAPublicKey)mServerInfo.getPublicKey();
			byte[] clientModulus=clientPubRsa.getModulus().abs().toByteArray();
		    byte[] clientExponent=clientPubRsa.getPublicExponent().abs().toByteArray();
		    byte[] serverModulus=serverPubRsa.getModulus().abs().toByteArray();
		    byte[] serverExponent=serverPubRsa.getPublicExponent().abs().toByteArray();

		    mDigest.update(clientModulus);
		    mDigest.update(clientExponent);
		    mDigest.update(serverModulus);
		    mDigest.update(serverExponent);
		    mDigest.update(nonceA);
		    
		    byte[] digestBytes= mDigest.digest();

			msg = Msg.newBuilder()
				.setType(Type.PAIRING_CHALENGE)
				.setThreadid(threadid)
				.setPairingstep(1)
				.setData(ByteString.copyFrom(digestBytes))
				.build();
			Msg resp = remoteAndroid.sendRequestAndReadResponse(msg,timeout);
			
			// 2. Receive nonceB
			if ((resp.getType() != Type.PAIRING_CHALENGE) || resp.getPairingstep()!=2)
				return false;
			byte[] nonceB=resp.getData().toByteArray();
			
			// 3. Send nonceA
			msg = Msg.newBuilder()
				.setType(Type.PAIRING_CHALENGE)
				.setThreadid(threadid)
				.setPairingstep(3)
				.setData(ByteString.copyFrom(nonceA))
				.build();
			resp = remoteAndroid.sendRequestAndReadResponse(msg,timeout);
			if ((resp.getType() != Type.PAIRING_CHALENGE) || resp.getPairingstep()!=4)
				return false;

			mDigest.reset();
		    mDigest.update(clientModulus);
		    mDigest.update(clientExponent);
		    mDigest.update(serverModulus);
		    mDigest.update(serverExponent);
		    mDigest.update(nonceA);
		    mDigest.update(nonceB);
		    
			byte[] digest=mDigest.digest();
			String passkey=byteToString(digest, 6);
			if (D) Log.d(TAG_PAIRING,PREFIX_LOG+"Client alpha="+passkey);
			
			boolean accept=askUser(remoteAndroid.getInfos().getName(), passkey);
			msg = Msg.newBuilder()
				.setType(Type.PAIRING_CHALENGE)
				.setThreadid(threadid)
				.setPairingstep(5)
				.setRc(accept)
				.setIdentity(ProtobufConvs.toIdentity(remoteAndroid.mManager.getInfos()))
				.build();
			resp = remoteAndroid.sendRequestAndReadResponse(msg,timeout);
			if ((resp.getType() != Type.PAIRING_CHALENGE) || resp.getPairingstep()!=6)
				return false;
			remoteAndroid.mInfo=ProtobufConvs.toRemoteAndroidInfo(resp.getIdentity());
			Application.sDiscover.addCookie(uri, resp.getCookie());
			return accept && resp.getRc();
		}
		catch (RemoteException e)
		{
			// Ignore
		}
		return false;
	}
	
	byte[] mHa;
    byte[] mNonceA;
    byte[] mNonceB = new byte[NONCE_BYTES_NEEDED];

	public Msg server(ConnectionContext connContext, Msg msg,long cookie)
	{
		Msg superMsg=super.server(connContext,msg,cookie);
		if (superMsg!=null)
			return superMsg;
		int step=msg.getPairingstep();
		switch (step)
		{
			case 1:
				// Receive H(Pka,Pkb,na,0)
				mHa=msg.getData().toByteArray();
				mNonceB = new byte[NONCE_BYTES_NEEDED];
		        Application.sRandom.nextBytes(mNonceB);
		        return Msg.newBuilder()
	        		.setType(Type.PAIRING_CHALENGE)
	        		.setThreadid(msg.getThreadid())
	        		.setPairingstep(2)
	        		.setData(ByteString.copyFrom(mNonceB))
	        		.build();
		        
			case 3:
				// Receive nA
				mNonceA=msg.getData().toByteArray();
				RSAPublicKey clientPubRsa=(RSAPublicKey)mClientInfo.getPublicKey();
				RSAPublicKey serverPubRsa=(RSAPublicKey)mServerInfo.getPublicKey();
				byte[] clientModulus=clientPubRsa.getModulus().abs().toByteArray();
			    byte[] clientExponent=clientPubRsa.getPublicExponent().abs().toByteArray();
			    byte[] serverModulus=serverPubRsa.getModulus().abs().toByteArray();
			    byte[] serverExponent=serverPubRsa.getPublicExponent().abs().toByteArray();
			    
			    // Check H(PkA,PkB,nA)
			    mDigest.update(clientModulus);
			    mDigest.update(clientExponent);
			    mDigest.update(serverModulus);
			    mDigest.update(serverExponent);
			    mDigest.update(mNonceA);
			    
			    // Check previous mHa
			    if (!Arrays.equals(mDigest.digest(), mHa))
			    {
			        return Msg.newBuilder()
		        		.setType(Type.PAIRING_CHALENGE)
		        		.setThreadid(msg.getThreadid())
		        		.setPairingstep(4)
		        		.setData(ByteString.copyFrom(mNonceB))
		        		.setRc(false)
		        		.build();
			    }

			    // Calc gamma
			    mDigest.reset();
			    mDigest.update(clientModulus);
			    mDigest.update(clientExponent);
			    mDigest.update(serverModulus);
			    mDigest.update(serverExponent);
			    mDigest.update(mNonceA);
			    mDigest.update(mNonceB);
			    byte[] digest=mDigest.digest();
				String passkey=byteToString(digest, 6);
				
				if (D) Log.d(TAG_PAIRING,PREFIX_LOG+"Server alpha="+passkey);
				startAskUser(connContext.mClientInfo.getName(), passkey);
		        return Msg.newBuilder()
	        		.setType(Type.PAIRING_CHALENGE)
	        		.setThreadid(msg.getThreadid())
	        		.setPairingstep(4)
	        		.setData(ByteString.copyFrom(mNonceB))
	        		.build();
				
			case 5:
				boolean rc=finishAskUser();
				connContext.mClientInfo=ProtobufConvs.toRemoteAndroidInfo(msg.getIdentity());
				if (mType==ConnectionType.BT)
				{
					connContext.mClientInfo.isDiscoverBT=true;
				}
				if (mType==ConnectionType.ETHERNET)
				{
					connContext.mClientInfo.isDiscoverEthernet=true;
				}
				if (rc && msg.getRc())
					Trusted.registerDevice(mAppContext,connContext);
		        return Msg.newBuilder()
	        		.setType(Type.PAIRING_CHALENGE)
	        		.setThreadid(msg.getThreadid())
	        		.setPairingstep(6)
	        		.setCookie(cookie)
	        		.setRc(rc)
	        		.setIdentity(ProtobufConvs.toIdentity(Application.getManager().getInfos())) // Publish alls informations
	        		.build();
		        
			default:
		        return Msg.newBuilder()
	        		.setType(Type.PAIRING_CHALENGE)
	        		.setThreadid(msg.getThreadid())
	        		.setRc(false)
	        		.build();
		}
	}
	
	private boolean askUser(String device,String passkey)
	{
		startAskUser(device, passkey);
		return finishAskUser();
	}
	
	private void startAskUser(String device,String passkey)
	{
		if (isGlobalLock()) 
			return;
		final Intent intent = new Intent(mAppContext,AskAcceptPairActivity.class);
		intent.putExtra(AskAcceptPairActivity.EXTRA_DEVICE, device);
		intent.putExtra(AskAcceptPairActivity.EXTRA_PASSKEY, passkey);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_FROM_BACKGROUND|Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		mHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				mAppContext.startActivity(intent);
			}
		});
	}
	private boolean finishAskUser()
	{
		try
		{
			Boolean b=(Boolean)CommunicationWithLock.getResult(LOCK_ASK_PAIRING, TIMEOUT_ASK_PAIR);
			if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv receive the user response");
			boolean accept=false;
			if (b==null)
				accept=false; // Timeout
			else
				accept=b.booleanValue();
			return accept;
		}
		finally
		{
			globalUnlock();
		}
	}
}
