package org.remoteandroid.pairing;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import org.remoteandroid.Application;
import org.remoteandroid.CommunicationWithLock;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.binder.AbstractSrvRemoteAndroid.ConnectionContext;
import org.remoteandroid.internal.AbstractProtoBufRemoteAndroid;
import org.remoteandroid.internal.AbstractRemoteAndroidImpl;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.Pair;
import org.remoteandroid.internal.Pairing;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;

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
    private static final String HASH_ALGORITHM="SHA-256";
    private static final int NONCE_BYTES_NEEDED=5;

    private long mChallenge;

	private final MessageDigest mDigest;
	byte[] mHa;
    byte[] mNonceA;
    byte[] mNonceB = new byte[NONCE_BYTES_NEEDED];


	public PairingImpl()
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
			int flags,
			long timeout) throws UnknownHostException, IOException, RemoteException
	{
		Msg msg;
		Msg resp=null;
		final long threadid = Thread.currentThread().getId();
		long challenge=Application.randomNextLong();
		if (challenge==0) challenge=1;
		
	    byte[] nonceA = new byte[NONCE_BYTES_NEEDED];
        Application.randomNextBytes(nonceA);

        RemoteAndroidInfo clientInfo=Application.getManager().getInfos();
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
			Trusted.registerDevice(Application.sAppContext, ProtobufConvs.toRemoteAndroidInfo(Application.sAppContext,resp.getIdentity()),
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
			Trusted.unregisterDevice(Application.sAppContext, ProtobufConvs.toRemoteAndroidInfo(Application.sAppContext, msg.getIdentity()));
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
						.setIdentity(ProtobufConvs.toIdentity(Application.sDiscover.getInfo()))
						.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS)
						.setChallengestep(11)
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