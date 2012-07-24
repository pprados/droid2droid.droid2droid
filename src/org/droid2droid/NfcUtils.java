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
package org.droid2droid;

import static org.droid2droid.internal.Constants.E;
import static org.droid2droid.internal.Constants.NDEF_MIME_TYPE;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.TAG_NFC;

import java.io.IOException;

import org.droid2droid.internal.Messages;
import org.droid2droid.internal.ProtobufConvs;

import android.annotation.TargetApi;
import android.content.Context;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
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

		if (VERSION.SDK_INT>=VERSION_CODES.ICE_CREAM_SANDWICH)
		{
			return new NdefMessage(
				new NdefRecord[]
				{
					new NdefRecord(NdefRecord.TNF_MIME_MEDIA, NDEF_MIME_TYPE, new byte[0], payload),
					//NdefRecord.createUri("www.droid2droid.org")
					NdefRecord.createApplicationRecord("org.droid2droid"),
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
