package org.remoteandroid.login;

import static org.remoteandroid.Constants.LOCK_ASK_PAIRING;
import static org.remoteandroid.Constants.PAIR_ANTI_SPOOF;
import static org.remoteandroid.Constants.TIMEOUT_ASK_PAIR;
import static org.remoteandroid.internal.Constants.COOKIE_NO;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_INSTALL;
import static org.remoteandroid.internal.Constants.TAG_PAIRING;
import static org.remoteandroid.internal.Constants.TAG_SECURITY;
import static org.remoteandroid.internal.Constants.V;

import java.io.IOException;
import java.math.BigInteger;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.crypto.Cipher;

import org.remoteandroid.Application;
import org.remoteandroid.CommunicationWithLock;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.binder.AbstractSrvRemoteAndroid.ConnectionContext;
import org.remoteandroid.internal.AbstractProtoBufRemoteAndroid;
import org.remoteandroid.internal.AbstractRemoteAndroidImpl;
import org.remoteandroid.internal.Login;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.Pair;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.Tools;
import org.remoteandroid.pairing.AskAcceptPairActivity;
import org.remoteandroid.pairing.Trusted;

import android.content.Intent;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import com.google.protobuf.ByteString;

// TODO: eviter en BT/secure, eviter l'authent serveur en HTTPS
public class LoginImpl extends Login
{
    private static final String HASH_ALGORITHM="SHA-256";
    private static final int NONCE_BYTES_NEEDED=5;

    private long mChallenge;

	private final MessageDigest mDigest;
	byte[] mHa;
    byte[] mNonceA;
    byte[] mNonceB = new byte[NONCE_BYTES_NEEDED];


	public LoginImpl()
	{
		mChallenge=Application.randomNextLong();
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
			long timeout) throws UnknownHostException, IOException, RemoteException
	{
		try
		{
			Msg msg;
			Msg resp=null;
			final long threadid = Thread.currentThread().getId();
			long challenge=Application.randomNextLong();
			if (challenge==0) challenge=1;
			
			// Step 1: Ask a challenge with my public key
			msg = Msg.newBuilder()
				.setType(type)
				.setThreadid(threadid)
				.setChallengestep(1)
				.setIdentity(ProtobufConvs.toIdentity(Application.sDiscover.getInfo()))
				.build();
			resp = android.sendRequestAndReadResponse(msg,timeout);
			if (//type==Type.CONNECT_FOR_PAIRING ||
				resp.getStatus()==AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS ||
				resp.getStatus()==AbstractRemoteAndroidImpl.STATUS_REFUSE_NO_BOUND
				)
			{
				// Branch pairing
				if (type!=Type.CONNECT_FOR_PAIRING && resp.getChallengestep()!=11)
				{
					if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject login process");
					throw new SecurityException("Reject login process");
				}
			    
				RemoteAndroidInfo serverInfo=ProtobufConvs.toRemoteAndroidInfo(Application.sAppContext,resp.getIdentity());
				android.mInfo=ProtobufConvs.toRemoteAndroidInfo(Application.sAppContext,resp.getIdentity());
			    byte[] nonceA = new byte[NONCE_BYTES_NEEDED];
		        Application.randomNextBytes(nonceA);

		        RemoteAndroidInfo clientInfo=Application.getManager().getInfos();
		        // 1. Send hash(Pka,Pkb,nonce);
				RSAPublicKey clientPubRsa=(RSAPublicKey)clientInfo.getPublicKey();
				RSAPublicKey serverPubRsa=(RSAPublicKey)serverInfo.getPublicKey();
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
				Trusted.registerDevice(Application.sAppContext, ProtobufConvs.toRemoteAndroidInfo(Application.sAppContext,resp.getIdentity()),
					ConnectionType.ETHERNET); // FIXME: Pas toujours ethernet
//				android.mInfo=ProtobufConvs.toRemoteAndroidInfo(Application.sAppContext,resp.getIdentity());
//				if (accept)
//					Application.sDiscover.addCookie(uri, resp.getCookie());
//				return accept && resp.getRc();
				return new Pair<RemoteAndroidInfoImpl,Long>(android.mInfo,resp.getCookie());
				
			}
			
			
			
			android.checkStatus(resp.getStatus());
			if (resp.getChallengestep()!=2)
			{
				if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject login process");
				throw new SecurityException("Reject login process");
			}
			
			RemoteAndroidInfoImpl remoteInfo=ProtobufConvs.toRemoteAndroidInfo(Application.sAppContext,resp.getIdentity());
			
			// Step 2: Resolve the chalenge and send a new chalenge with remote public key
			msg = Msg.newBuilder()
				.setType(type)
				.setThreadid(threadid)
				.setChallengestep(3)
				.setChallenge1(ByteString.copyFrom(uncipher(resp.getChallenge1().toByteArray(),Application.getKeyPair().getPrivate())))
				.setChallenge2(ByteString.copyFrom(cipher(Tools.longToByteArray(challenge),remoteInfo.getPublicKey())))
				.build();
			resp = android.sendRequestAndReadResponse(msg,timeout);
			android.checkStatus(resp.getStatus());
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
			long cookie=resp.getCookie();
			if (cookie==COOKIE_NO)
			{
				if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Invalide challenge");
				throw new SecurityException("Invalide challenge");
			}
			return new Pair<RemoteAndroidInfoImpl,Long>(remoteInfo,cookie);
		}
		catch (GeneralSecurityException e)
		{
			if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Invalide challenge");
			throw new SecurityException("Invalide challenge");
		}
	}

	@Override
	public Msg server(Object ctx,Msg msg,long cookie)
	{
		ConnectionContext conContext=(ConnectionContext)ctx;
		int step=msg.getChallengestep();
		if (step==0) // Unpairing
		{
			Trusted.unregisterDevice(Application.sAppContext, ProtobufConvs.toRemoteAndroidInfo(Application.sAppContext, msg.getIdentity()));
			return Msg.newBuilder()
				.setType(Type.CONNECT_FOR_PAIRING)
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
					if (msg.getType()==Type.CONNECT_FOR_PAIRING)
					{
						return Msg.newBuilder()
								.setType(msg.getType())
								.setThreadid(msg.getThreadid())
								.setIdentity(ProtobufConvs.toIdentity(Application.sDiscover.getInfo()))
								.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS)
								.setChallengestep(11)
								.build();
					}
					else
					{
						return Msg.newBuilder()
							.setType(msg.getType())
							.setThreadid(msg.getThreadid())
							.setIdentity(ProtobufConvs.toIdentity(Application.sDiscover.getInfo()))
							.setChallenge1(ByteString.copyFrom(cipher(Tools.longToByteArray(mChallenge),conContext.mClientInfo.publicKey)))
							.setChallengestep(2)
							.build();
					}
				case 3:
					// Receive the result challenge and a new challenge
					long resolvedChallenge=Tools.byteArrayToLong(msg.getChallenge1().toByteArray());
					if (mChallenge!=resolvedChallenge)
						throw new GeneralSecurityException("Chalenge failed");
					return Msg.newBuilder()
						.setType(msg.getType())
						.setThreadid(msg.getThreadid())
						.setChallenge2(ByteString.copyFrom(uncipher(msg.getChallenge2().toByteArray(),Application.getKeyPair().getPrivate())))
						.setChallengestep(4)
						.setCookie(cookie)
						.build();

				case 11:
					// Receive H(Pka,Pkb,na,0)
					mHa=msg.getData().toByteArray();
					mNonceB = new byte[NONCE_BYTES_NEEDED];
			        Application.randomNextBytes(mNonceB);
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
					RSAPublicKey serverPubRsa=(RSAPublicKey)Application.getManager().getInfos().getPublicKey();
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
					conContext.mClientInfo=ProtobufConvs.toRemoteAndroidInfo(Application.sAppContext,msg.getIdentity());
					if (conContext.mType==ConnectionType.BT)
					{
						conContext.mClientInfo.isDiscoverBT=true;
					}
					if (conContext.mType==ConnectionType.ETHERNET)
					{
						conContext.mClientInfo.isDiscoverEthernet=true;
					}
					if (rc && msg.getRc())
						Trusted.registerDevice(Application.sAppContext,conContext);
					else
					{
						cookie=COOKIE_NO;
						Application.removeCookie(conContext.mClientInfo.uuid.toString());
					}
			        return Msg.newBuilder()
		        		.setType(msg.getType())
		        		.setThreadid(msg.getThreadid())
		        		.setChallengestep(16)
		        		.setCookie(cookie)
		        		.setRc(rc)
		        		.setIdentity(ProtobufConvs.toIdentity(Application.getManager().getInfos())) // Publish alls informations
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
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(challenge);
	}
	private byte[] uncipher(byte[] challenge,PrivateKey key) throws GeneralSecurityException
	{
		Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
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
	
	private boolean askUser(String device,String passkey)
	{
		startAskPairing(device, passkey);
		return finishAsPairing();
	}
	
	private void startAskPairing(String device,String passkey)
	{
		if (isGlobalLock()) 
			return;
		final Intent intent = new Intent(Application.sAppContext,AskAcceptPairActivity.class);
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
		Application.sHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				Application.sAppContext.startActivity(intent);
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
