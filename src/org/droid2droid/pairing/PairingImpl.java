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
package org.droid2droid.pairing;

import static org.droid2droid.Constants.LOCK_ASK_PAIRING;
import static org.droid2droid.Constants.PAIR_ANTI_SPOOF;
import static org.droid2droid.Constants.TIMEOUT_ASK_PAIR;
import static org.droid2droid.internal.Constants.COOKIE_NO;
import static org.droid2droid.internal.Constants.D;
import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.HASH_ALGORITHM;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.TAG_INSTALL;
import static org.droid2droid.internal.Constants.TAG_PAIRING;
import static org.droid2droid.internal.Constants.TAG_SECURITY;
import static org.droid2droid.internal.Constants.V;

import java.io.IOException;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.droid2droid.CommunicationWithLock;
import org.droid2droid.ConnectionType;
import org.droid2droid.RAApplication;
import org.droid2droid.RemoteAndroidInfo;
import org.droid2droid.binder.AbstractSrvRemoteAndroid.ConnectionContext;
import org.droid2droid.internal.AbstractProtoBufRemoteAndroid;
import org.droid2droid.internal.AbstractRemoteAndroidImpl;
import org.droid2droid.internal.Messages.Msg;
import org.droid2droid.internal.Messages.Type;
import org.droid2droid.internal.Pair;
import org.droid2droid.internal.Pairing;
import org.droid2droid.internal.ProtobufConvs;
import org.droid2droid.internal.RemoteAndroidInfoImpl;

import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import com.google.protobuf.ByteString;


// Accept or refuse connection ?
// |pair?|Ano? |cli|cProx|srv|sProx|
// +-----+-----+---+-----+---+-----+
// |  N  |  N  | N |  N  | N |  N  | Refused
// |  N  |  Y  | N |  N  | N |  N  | Refused
// |  Y  |  N  | N |  N  | N |  N  | Pair, Connect 
// |  Y  |  Y  | N |  N  | N |  N  | Pair, Connect
// +-----+-----+---+-----+---+-----+
// |  N  |  N  | Y |  N  | Y |  N  | Connect
// |  Y  |  N  | Y |  N  | Y |  N  | Connect
// |  N  |  Y  | Y |  N  | Y |  N  | Connect
// |  Y  |  Y  | Y |  N  | Y |  N  | Connect
// +-----+-----+---+-----+---+-----+
// |  N  |  N  | N |  N  | Y |  N  | Refuse toto discover, Refused
// |  N  |  Y  | N |  N  | Y |  N  | Connect
// |  Y  |  N  | N |  N  | Y |  N  | Pair, Connected
// |  Y  |  Y  | N |  N  | Y |  N  | Connected
// +-----+-----+---+-----+---+-----+
// |  N  |  N  | Y |  N  | N |  N  | Refused
// |  N  |  Y  | Y |  N  | N |  N  | Refused
// |  Y  |  N  | Y |  N  | N |  N  | Pair, Connected
// |  Y  |  Y  | Y |  N  | N |  N  | Pair, Connected
// +-----+-----+---+-----+---+-----+
// |  N  |  N  | N |  N  | N |  Y  | Refused
// |  N  |  Y  | N |  N  | N |  Y  | Connect
// |  Y  |  N  | N |  N  | N |  Y  | Pair, Connected
// |  Y  |  Y  | N |  N  | N |  Y  | Connected
// +-----+-----+---+-----+---+-----+
// |  N  |  N  | Y |  N  | Y |  Y  | Connected
// |  N  |  Y  | Y |  N  | Y |  Y  | Connected
// |  Y  |  N  | Y |  N  | Y |  Y  | Connected
// |  Y  |  Y  | Y |  N  | Y |  Y  | Connected
// +-----+-----+---+-----+---+-----+
// |  N  |  N  | Y |  N  | N |  Y  | Connected
// |  N  |  Y  | Y |  N  | N |  Y  | Connected
// |  Y  |  N  | Y |  N  | N |  Y  | Connected
// |  Y  |  Y  | Y |  N  | N |  Y  | Connected
// +-----+-----+---+-----+---+-----+
// |  N  |  N  | N |  N  | Y |  Y  | Refuse to discover, Refused
// |  N  |  Y  | N |  N  | Y |  Y  | Connected
// |  Y  |  N  | N |  N  | Y |  Y  | Pair, Connected
// |  Y  |  Y  | N |  N  | Y |  Y  | Connected

// TODO: eviter en BT/secure, eviter l'authent serveur en HTTPS
public final class PairingImpl extends Pairing
{
    private static final int NONCE_BYTES_NEEDED=5;

    private long mChallenge;

	private final MessageDigest mDigest;
	byte[] mHa;
    byte[] mNonceA;
    byte[] mNonceB = new byte[NONCE_BYTES_NEEDED];


	public PairingImpl()
	{
		mChallenge=RAApplication.randomNextLong();
	    try
		{
			mDigest = MessageDigest.getInstance(HASH_ALGORITHM);
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new InternalError(e.getMessage());
		}
	}
	/**
	 * @return 0 if error. Else, return cookie.
	 */
	@Override
	public Pair<RemoteAndroidInfoImpl,Long> client(
			AbstractProtoBufRemoteAndroid android,
			Uri uri,
			Type type,
			int flags,
			long timeout) throws UnknownHostException, IOException, RemoteException
	{
		Msg msg;
		Msg resp=null;
		final long threadid = Thread.currentThread().getId();
		long challenge=RAApplication.randomNextLong();
		if (challenge==0) challenge=1;
		
	    byte[] nonceA = new byte[NONCE_BYTES_NEEDED];
        RAApplication.randomNextBytes(nonceA);

        RemoteAndroidInfo clientInfo=RAApplication.getManager().getInfos();
        // 1. Send hash(Pka,Pkb,nonce);
		RSAPublicKey clientPubRsa=(RSAPublicKey)clientInfo.getPublicKey();
		RSAPublicKey serverPubRsa=(RSAPublicKey)android.mInfo.getPublicKey();
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
			.setType(type)
			.setThreadid(threadid)
			.setChallengestep(11)
			.setData(ByteString.copyFrom(digestBytes))
			.setPairing(true)
			.build();
		resp = android.sendRequestAndReadResponse(msg,timeout);
		
		// 2. Receive nonceB
		if ((resp.getType() != type) || resp.getChallengestep()!=12)
			return new Pair<RemoteAndroidInfoImpl,Long>(null,-1L);
		byte[] nonceB=resp.getData().toByteArray();
		
		// 3. Send nonceA
		msg = Msg.newBuilder()
			.setType(type)
			.setThreadid(threadid)
			.setChallengestep(13)
			.setData(ByteString.copyFrom(nonceA))
			.build();
		resp = android.sendRequestAndReadResponse(msg,timeout);
		if ((resp.getType() != type) || resp.getChallengestep()!=14)
			throw new SecurityException("Reject login process");;

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
		
		boolean accept=askUser(android.getInfos().getName(), passkey);
		msg = Msg.newBuilder()
			.setType(type)
			.setThreadid(threadid)
			.setChallengestep(15)
			.setRc(accept)
			.setIdentity(ProtobufConvs.toIdentity(android.mManager.getInfos()))
			.build();
		resp = android.sendRequestAndReadResponse(msg,timeout);
		if ((resp.getType() != type) || resp.getChallengestep()!=16)
			throw new SecurityException("Reject login process");
		if (accept)
		{
			Trusted.registerDevice(RAApplication.sAppContext, ProtobufConvs.toRemoteAndroidInfo(RAApplication.sAppContext,resp.getIdentity()),
				ConnectionType.ETHERNET); // FIXME: Not always ethernet
		}
		return new Pair<RemoteAndroidInfoImpl,Long>(android.mInfo,resp.getCookie());
	}

	@Override
	public Msg server(Object ctx,Msg msg,long cookie)
	{
		ConnectionContext conContext=(ConnectionContext)ctx;
		int step=msg.getChallengestep();
		if (step==0) // Unpairing
		{
			Trusted.unregisterDevice(RAApplication.sAppContext, ProtobufConvs.toRemoteAndroidInfo(RAApplication.sAppContext, msg.getIdentity()));
			return Msg.newBuilder()
				.setType(Type.CONNECT_FOR_COOKIE)
				.setThreadid(msg.getThreadid())
				.setChallengestep(-1)
				.build();
		}
		if (mChallenge==0) mChallenge=1;
	    switch (step)
		{
			case 1 :
				// Propose a cypher challenge with public key of the caller
				return Msg.newBuilder()
						.setType(msg.getType())
						.setThreadid(msg.getThreadid())
						.setIdentity(ProtobufConvs.toIdentity(RAApplication.sDiscover.getInfo()))
						.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS)
						.setChallengestep(11)
						.build();
			case 11:
				// Receive H(Pka,Pkb,na,0)
				mHa=msg.getData().toByteArray();
				mNonceB = new byte[NONCE_BYTES_NEEDED];
		        RAApplication.randomNextBytes(mNonceB);
		        return Msg.newBuilder()
	        		.setType(msg.getType())
	        		.setThreadid(msg.getThreadid())
	        		.setChallengestep(12)
	        		.setData(ByteString.copyFrom(mNonceB))
	        		.build();
		        
			case 13:
				// Receive nA
				mNonceA=msg.getData().toByteArray();
				RSAPublicKey clientPubRsa=(RSAPublicKey)conContext.mClientInfo.getPublicKey();
				RSAPublicKey serverPubRsa=(RSAPublicKey)RAApplication.getManager().getInfos().getPublicKey();
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
		        		.setType(msg.getType())
		        		.setThreadid(msg.getThreadid())
		        		.setChallengestep(14)
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
				startAskPairing(conContext.mClientInfo.getName(), passkey);
		        return Msg.newBuilder()
	        		.setType(msg.getType())
	        		.setThreadid(msg.getThreadid())
	        		.setChallengestep(14)
	        		.setData(ByteString.copyFrom(mNonceB))
	        		.build();
				
			case 15:
				boolean rc=finishAsPairing();
				conContext.mClientInfo=ProtobufConvs.toRemoteAndroidInfo(RAApplication.sAppContext,msg.getIdentity());
				if (conContext.mType==ConnectionType.BT)
				{
					conContext.mClientInfo.isDiscoverByBT=true;
				}
				if (conContext.mType==ConnectionType.ETHERNET)
				{
					conContext.mClientInfo.isDiscoverByEthernet=true;
				}
				if (rc && msg.getRc())
					Trusted.registerDevice(RAApplication.sAppContext,conContext);
				else
				{
					cookie=COOKIE_NO;
					RAApplication.removeCookie(conContext.mClientInfo.uuid.toString());
				}
		        return Msg.newBuilder()
	        		.setType(msg.getType())
	        		.setThreadid(msg.getThreadid())
	        		.setChallengestep(16)
	        		.setCookie(cookie)
	        		.setRc(rc)
	        		.setIdentity(ProtobufConvs.toIdentity(RAApplication.getManager().getInfos())) // Publish alls informations
	        		.build();
			default:
				if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject login process from '"+conContext.mClientInfo.getName()+'\'');
				return Msg.newBuilder()
					.setType(msg.getType())
					.setThreadid(msg.getThreadid())
					.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS)
					.build();
		}
	}

	public static final String byteToString(byte[] bytes,int max)
	{
	    BigInteger bigint = new BigInteger(1, bytes);
	    String digits=bigint.toString();
	    return (digits.length()>max) 
	    	? digits.substring(digits.length()-max)
	    	: digits;
	}
	
	private boolean askUser(String device,String passkey)
	{
		startAskPairing(device, passkey);
		return finishAsPairing();
	}
	
	private void startAskPairing(String device,String passkey)
	{
		if (isGlobalLock()) 
			return;
		final Intent intent = new Intent(RAApplication.sAppContext,AskAcceptPairActivity.class);
		intent.putExtra(AskAcceptPairActivity.EXTRA_DEVICE, device);
		intent.putExtra(AskAcceptPairActivity.EXTRA_PASSKEY, passkey);
		intent.setFlags(
			Intent.FLAG_ACTIVITY_NEW_TASK
			|Intent.FLAG_FROM_BACKGROUND
			|Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
			|Intent.FLAG_ACTIVITY_NO_ANIMATION
			|Intent.FLAG_ACTIVITY_NO_HISTORY
//			|Intent.FLAG_ACTIVITY_SINGLE_TOP
			|Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET
			);
		RAApplication.sHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				RAApplication.sAppContext.startActivity(intent);
			}
		});
	}
	private boolean finishAsPairing()
	{
		try
		{
			Boolean b=(Boolean)CommunicationWithLock.getResult(LOCK_ASK_PAIRING, TIMEOUT_ASK_PAIR);
			if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv receive the user response");
			if (b==null)
				return false; // Timeout
			else
				return b;
		}
		finally
		{
			globalUnlock();
		}
	}
	public static boolean isGlobalLock()
	{
		if (PAIR_ANTI_SPOOF)
		{
			if (D) Log.d(TAG_PAIRING,PREFIX_LOG+"Global lock set");
			if (sNoDOS.get()) 
			{
				if (E) Log.e(TAG_PAIRING,PREFIX_LOG+"Multiple pairing process at the same time");
				return false;
			}
			sNoDOS.set(true);
		}
		return false;
	}
	public static void globalUnlock()
	{
		if (PAIR_ANTI_SPOOF)
		{
			if (sNoDOS.get())
			{
				if (D) Log.d(TAG_PAIRING,PREFIX_LOG+"Global lock unset");
				sNoDOS.set(false);
			}
		}
	}
	private static AtomicBoolean sNoDOS=new AtomicBoolean();
	
}
