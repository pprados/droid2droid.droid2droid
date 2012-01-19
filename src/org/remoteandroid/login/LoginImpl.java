package org.remoteandroid.login;

import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_SECURITY;

import java.io.IOException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.Cipher;

import org.remoteandroid.Application;
import org.remoteandroid.binder.AbstractSrvRemoteAndroid.ConnectionContext;
import org.remoteandroid.internal.AbstractProtoBufRemoteAndroid;
import org.remoteandroid.internal.AbstractRemoteAndroidImpl;
import org.remoteandroid.internal.Login;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.Pair;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;

import android.os.RemoteException;
import android.util.Log;

import com.google.protobuf.ByteString;

// TODO: eviter en BT/secure, eviter l'authent serveur en HTTPS
public class LoginImpl extends Login
{
	private long mChallenge;
	public LoginImpl()
	{
		mChallenge=Application.sRandom.nextLong();
	}
	/**
	 * @return 0 if error. Else, return cookie.
	 */
	@Override
	public Pair<RemoteAndroidInfoImpl,Long> client(
			AbstractProtoBufRemoteAndroid android,
			long timeout) throws UnknownHostException, IOException, RemoteException
	{
		try
		{
			Msg msg;
			Msg resp;
			final long threadid = Thread.currentThread().getId();
			long challenge=Application.sRandom.nextLong();
			if (challenge==0) challenge=1;
			
			// Step 1: Ask a challenge with my public key
			msg = Msg.newBuilder()
				.setType(Type.CONNECT_FOR_COOKIE) // TODO: marier CONNECT et CONNECT_FOR_USING
				.setThreadid(threadid)
				.setChallengestep(1)
				.setIdentity(ProtobufConvs.toIdentity(Application.sDiscover.getInfo()))
				.build();
			resp = android.sendRequestAndReadResponse(msg,timeout);
			android.checkStatus(resp.getStatus());
			if (resp.getChallengestep()!=2)
			{
				if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject login process");
				throw new SecurityException("Reject login process");
			}
			
			RemoteAndroidInfoImpl remoteInfo=ProtobufConvs.toRemoteAndroidInfo(Application.sAppContext,resp.getIdentity());
			
			// Step 2: Resolve the chalenge and send a new chalenge with remote public key
			msg = Msg.newBuilder()
				.setType(Type.CONNECT_FOR_COOKIE)
				.setThreadid(threadid)
				.setChallengestep(3)
				.setChallenge1(ByteString.copyFrom(uncipher(resp.getChallenge1().toByteArray(),Application.getKeyPair().getPrivate())))
				.setChallenge2(ByteString.copyFrom(cipher(longToByteArray(challenge),remoteInfo.getPublicKey())))
				.build();
			resp = android.sendRequestAndReadResponse(msg,timeout);
			android.checkStatus(resp.getStatus());
			if (resp.getChallengestep()!=4)
			{
				if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject login process");
				throw new SecurityException("Invalide step");
			}

			// Step 3: check remote resolve the chalenge and keep cookie
			long resolvedChallenge=toLong(resp.getChallenge2().toByteArray());
			if (challenge!=resolvedChallenge)
			{
				if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Invalide challenge");
				throw new SecurityException("Invalide challenge");
			}
			long cookie=resp.getCookie();
			if (cookie==0)
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
		if (mChallenge==0) mChallenge=1;
		try
		{
		    switch (msg.getChallengestep())
			{
				case 1 :
					// Propose a cypher challenge with public key of the caller
					return Msg.newBuilder()
						.setType(msg.getType())
						.setThreadid(msg.getThreadid())
						.setIdentity(ProtobufConvs.toIdentity(Application.sDiscover.getInfo()))
						.setChallenge1(ByteString.copyFrom(cipher(longToByteArray(mChallenge),conContext.mClientInfo.publicKey)))
						.setChallengestep(2)
						.build();
				case 3:
					// Receive the result challenge and a new challenge
					long resolvedChallenge=toLong(msg.getChallenge1().toByteArray());
					if (mChallenge!=resolvedChallenge)
						throw new GeneralSecurityException("Chalenge failed");
					return Msg.newBuilder()
						.setType(msg.getType())
						.setThreadid(msg.getThreadid())
						.setChallenge2(ByteString.copyFrom(uncipher(msg.getChallenge2().toByteArray(),Application.getKeyPair().getPrivate())))
						.setChallengestep(4)
						.setCookie(cookie)
						.build();

				default:
					if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject login process from "+conContext.mClientInfo.getName());
					return Msg.newBuilder()
						.setType(msg.getType())
						.setThreadid(msg.getThreadid())
						.setStatus(AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS)
						.build();
			}
            
		}
		catch (GeneralSecurityException e)
		{
			if (E) Log.e(TAG_SECURITY,PREFIX_LOG+"Reject login process from "+conContext.mClientInfo.getName(),e);
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
	
	private static long toLong(byte[] data) 
	{
	    return (long)(
	            (long)(0xff & data[0]) << 56  |
	            (long)(0xff & data[1]) << 48  |
	            (long)(0xff & data[2]) << 40  |
	            (long)(0xff & data[3]) << 32  |
	            (long)(0xff & data[4]) << 24  |
	            (long)(0xff & data[5]) << 16  |
	            (long)(0xff & data[6]) << 8   |
	            (long)(0xff & data[7]) << 0
	            );
	}
	private static byte[] longToByteArray(long data) 
	{
		return new byte[] {
			(byte)((data >> 56) & 0xff),
			(byte)((data >> 48) & 0xff),
			(byte)((data >> 40) & 0xff),
			(byte)((data >> 32) & 0xff),
			(byte)((data >> 24) & 0xff),
			(byte)((data >> 16) & 0xff),
			(byte)((data >> 8 ) & 0xff),
			(byte)((data >> 0) & 0xff),
			};
	}

}
