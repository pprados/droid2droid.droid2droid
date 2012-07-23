package org.remoteandroid;

import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.NDEF_MIME_TYPE;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_NFC;

import java.io.IOException;

import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;

import android.annotation.TargetApi;
import android.content.Context;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Build;
import android.util.Log;

@TargetApi(10)
public class NfcUtils
{
	public static int writeTag(Context context,Tag tag,RemoteAndroidInfo info) throws IOException, FormatException
	{
		
// Hack to register specific device		
//RemoteAndroidInfoImpl ii=((RemoteAndroidInfoImpl)info)	;
//ii.uris.clear();ii.uris.add("ip://192.168.0.60:19876");
		Ndef tech = Ndef.get(tag);
		try
		{
			String[] techList=tag.getTechList();
			String ndefName=Ndef.class.getName();
			int i=0;
			for (;i<techList.length;++i)
			{ 
				if (techList[i].equals(ndefName))
					break;
			}
			if (i==techList.length) 
			{
				return R.string.expose_nfc_error_unknown_format;
			}
			
			if (!tech.isWritable())
			{
				if (E) Log.e(TAG_NFC, PREFIX_LOG+"NFC No writable");
				return R.string.expose_nfc_error_no_writable;
			}
			tech.connect();
			tech.writeNdefMessage(createNdefMessage(context,info));
			return R.string.expose_nfc_writed;
		}
		finally
		{
			try
			{
				if (tech!=null) tech.close();
			}
			catch (IOException e)
			{
				if (E) Log.e("NFC", "IOException while closing MifareUltralight...", e);
			}
		}
	}
	@TargetApi(14)
	public static NdefMessage createNdefMessage(Context context,RemoteAndroidInfo info)
	{
		Messages.Identity msg=ProtobufConvs.toIdentity(info);
		byte[] payload=msg.toByteArray();

		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.ICE_CREAM_SANDWICH)
		{
			return new NdefMessage(
				new NdefRecord[]
				{
					new NdefRecord(NdefRecord.TNF_MIME_MEDIA, NDEF_MIME_TYPE, new byte[0], payload),
					//NdefRecord.createUri("www.remoteandroid.org")
					NdefRecord.createApplicationRecord("org.remoteandroid"),
				});
		}
		else
		{
			return new NdefMessage(
				new NdefRecord[]
				{
					new NdefRecord(NdefRecord.TNF_MIME_MEDIA, NDEF_MIME_TYPE, new byte[0], payload),
				});
		}
		
	}
}
