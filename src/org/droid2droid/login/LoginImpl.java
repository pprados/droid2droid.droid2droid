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
package org.droid2droid.login;

import static org.droid2droid.Droid2DroidManager.FLAG_PROPOSE_PAIRING;
import static org.droid2droid.internal.Constants.CIPHER_ALGORITHM;
import static org.droid2droid.internal.Constants.COOKIE_SECURITY;
import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.TAG_CLIENT_BIND;
import static org.droid2droid.internal.Constants.TAG_SECURITY;
import static org.droid2droid.internal.Constants.V;

import java.io.IOException;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.Cipher;

import org.droid2droid.RAApplication;
import org.droid2droid.binder.AbstractSrvRemoteAndroid.ConnectionContext;
import org.droid2droid.internal.AbstractProtoBufRemoteAndroid;
import org.droid2droid.internal.AbstractRemoteAndroidImpl;
import org.droid2droid.internal.Login;
import org.droid2droid.internal.Messages.Msg;
import org.droid2droid.internal.Messages.Type;
import org.droid2droid.internal.Pair;
import org.droid2droid.internal.ProtobufConvs;
import org.droid2droid.internal.RemoteAndroidInfoImpl;
import org.droid2droid.internal.Tools;
import org.droid2droid.pairing.Trusted;

import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import com.google.protobuf.ByteString;

public final class LoginImpl extends Login
{
    private long mChallenge;

    private final PublicKey mClientKey;

	public LoginImpl(PublicKey clientKey)
	{
		mChallenge=RAApplication.randomNextLong();
		mClientKey=clientKey;
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
		try
		{
			final boolean isProposePairing=(flags & FLAG_PROPOSE_PAIRING)!=0;
			Msg msg;
			Msg resp=null;
			final long threadid = Thread.currentThread().getId();
			
			// Step 1: Ask a challenge with my public key
    		if (V) Log.v(TAG_CLIENT_BIND,PREFIX_LOG+"-> CONNECT_FOR_COOKIE");
			msg = Msg.newBuilder()
				.setType(type)
				.setThreadid(threadid)
				.setChallengestep(1)
				.setIdentity(ProtobufConvs.toIdentity(RAApplication.sDiscover.getInfo()))
				.setPairing(isProposePairing)
				.build();
			resp = android.sendRequestAndReadResponse(msg,timeout);
    		if (V) Log.v(TAG_CLIENT_BIND,PREFIX_LOG+"<- "+resp.getStatus());
		    final RemoteAndroidInfoImpl remoteInfo=ProtobufConvs.toRemoteAndroidInfo(RAApplication.sAppContext,resp.getIdentity());
		    android.mInfo=remoteInfo;
			android.checkStatus(resp);
			if (resp.getChallengestep()==4)
			{
				PublicKey pubKey=android.getPeerPublicKey();
				String uuid=android.getPeerUUID();
				RemoteAndroidInfoImpl info=Trusted.getBonded(uuid);
				if (info!=null && !info.getPublicKey().equals(pubKey))
					throw new SecurityException("Invalid public key");

				return new Pair<RemoteAndroidInfoImpl,Long>(remoteInfo,resp.getCookie());
			}
			if (resp.getChallengestep()!=2)
			{
				if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject login process");
				return new Pair<RemoteAndroidInfoImpl,Long>(remoteInfo,COOKIE_SECURITY);
			}
			
			long challenge=RAApplication.randomNextLong();
			if (challenge==0) challenge=1;
			
			// Step 2: Resolve the chalenge and send a new chalenge with remote public key
			msg = Msg.newBuilder()
				.setType(type)
				.setThreadid(threadid)
				.setChallengestep(3)
				.setChallenge1(ByteString.copyFrom(uncipher(resp.getChallenge1().toByteArray(),RAApplication.getKeyPair().getPrivate())))
				.setChallenge2(ByteString.copyFrom(cipher(Tools.longToByteArray(challenge),remoteInfo.getPublicKey())))
				.setPairing(isProposePairing)
				.build();
			resp = android.sendRequestAndReadResponse(msg,timeout);
			android.checkStatus(resp);
			if (resp.getChallengestep()!=4)
			{
				if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject login process");
				throw new SecurityException("Invalide step");
			}

			// Step 3: check remote resolve the chalenge and keep cookie
			long resolvedChallenge=Tools.byteArrayToLong(resp.getChallenge2().toByteArray());
			if (challenge!=resolvedChallenge)
			{
				if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Invalide challenge");
				throw new SecurityException("Invalide challenge");
			}
			return new Pair<RemoteAndroidInfoImpl,Long>(remoteInfo,resp.getCookie());
		}
		catch (GeneralSecurityException e)
		{
			if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Invalide challenge");
			throw new SecurityException("Invalide challenge");
		}
	}

	@Override
	public Msg server(Object ctx,Msg msg,long cookie,boolean acceptAnonymous)
	{
		boolean isBound;
		ConnectionContext conContext=(ConnectionContext)ctx;
		int step=msg.getChallengestep();
		if (step==0) // Unpairing
		{
			// FIXME
			Trusted.unregisterDevice(RAApplication.sAppContext, ProtobufConvs.toRemoteAndroidInfo(RAApplication.sAppContext, msg.getIdentity()));
			return Msg.newBuilder()
				.setType(Type.CONNECT_FOR_COOKIE)
				.setThreadid(msg.getThreadid())
				.setChallengestep(-1)
				.build();
		}
		if (mChallenge==0) mChallenge=1;
		try
		{
		    switch (step)
			{
				case 1 :
					// Propose a cypher challenge with public key of the caller
					// Short circuit because use a TLS channel
					if (mClientKey!=null)
					{
						isBound=Trusted.isBonded(mClientKey);
						return Msg.newBuilder()
								.setType(msg.getType())
								.setThreadid(msg.getThreadid())
								.setChallengestep(4)
								.setIdentity(ProtobufConvs.toIdentity(RAApplication.sDiscover.getInfo()))
//								.setCookie(msg.getPairing() ? (isBound||acceptAnonymous ? cookie : COOKIE_NO) : cookie) // Too early to publish cookie ?
								.setCookie(cookie)
								.build();
					}

					return Msg.newBuilder()
						.setType(msg.getType())
						.setThreadid(msg.getThreadid())
						.setIdentity(ProtobufConvs.toIdentity(RAApplication.sDiscover.getInfo()))
						.setChallenge1(ByteString.copyFrom(cipher(Tools.longToByteArray(mChallenge),conContext.mClientInfo.publicKey)))
						.setChallengestep(2)
						.build();
				case 3:
					// Receive the result challenge and a new challenge
					long resolvedChallenge=Tools.byteArrayToLong(msg.getChallenge1().toByteArray());
					if (mChallenge!=resolvedChallenge)
						throw new GeneralSecurityException("Chalenge failed");
					isBound=Trusted.isBonded(conContext.mClientInfo);
					return Msg.newBuilder()
						.setType(msg.getType())
						.setThreadid(msg.getThreadid())
						.setChallenge2(ByteString.copyFrom(uncipher(msg.getChallenge2().toByteArray(),RAApplication.getKeyPair().getPrivate())))
						.setChallengestep(4)
						.setCookie(msg.getPairing() ? (isBound||acceptAnonymous ? cookie : 0) : cookie) // Too early to publish cookie ?
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
		catch (GeneralSecurityException e)
		{
			if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject login process from '"+conContext.mClientInfo.getName()+'\'',e);
			return Msg.newBuilder()
				.setType(msg.getType())
				.setThreadid(msg.getThreadid())
				.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS)
				.build();
		}
	}

	private byte[] cipher(byte[] challenge,PublicKey key) throws GeneralSecurityException
	{
		Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(challenge);
	}
	private byte[] uncipher(byte[] challenge,PrivateKey key) throws GeneralSecurityException
	{
		Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
		return cipher.doFinal(challenge);
	}
	
	public static final String byteToString(byte[] bytes,int max)
	{
	    BigInteger bigint = new BigInteger(1, bytes);
	    String digits=bigint.toString();
	    return (digits.length()>max) 
	    	? digits.substring(digits.length()-max)
	    	: digits;
	}
	
//	public static boolean isGlobalLock()
//	{
//		if (PAIR_ANTI_SPOOF)
//		{
//			if (D) Log.d(TAG_PAIRING,PREFIX_LOG+"Global lock set");
//			if (sNoDOS.get()) 
//			{
//				if (E) Log.e(TAG_PAIRING,PREFIX_LOG+"Multiple pairing process at the same time");
//				return false;
//			}
//			sNoDOS.set(true);
//		}
//		return false;
//	}
	public static void globalUnlock()
	{
//		if (PAIR_ANTI_SPOOF)
//		{
//			if (sNoDOS.get())
//			{
//				if (D) Log.d(TAG_PAIRING,PREFIX_LOG+"Global lock unset");
//				sNoDOS.set(false);
//			}
//		}
	}
//	private static AtomicBoolean sNoDOS=new AtomicBoolean();
	
}
