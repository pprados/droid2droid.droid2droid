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
package org.droid2droid.service;

import static org.droid2droid.Droid2DroidManager.QRCODE_URI;
import static org.droid2droid.internal.Constants.TAG_PROVIDER;
import static org.droid2droid.internal.Constants.W;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.droid2droid.ui.expose.ExposeQRCodeFragment;

import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

public final class RemoteAndroidProvider extends ContentProvider
{
	private static final String AUTHORITY="content://org.droid2droid";
	private static final String QRCODE="/qrcode";
	private static final String MIME_TYPE="image/png";
	private static final String[] MIME_TYPES=new String[]{ MIME_TYPE};
	
	@Override
	public boolean onCreate()
	{
		return false;
	}
	
	@Override
	public String[] getStreamTypes(Uri uri, String mimeTypeFilter)
	{
		return 	MIME_TYPES;
	}
	
	@Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) 
    {
		return null;
    }

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs)
	{
		return 0;
	}
	@Override
	public Uri insert(Uri uri, ContentValues values)
	{
		return null;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs)
	{
		return 0;
	}

	@Override
	public String getType(Uri uri)
	{
		return MIME_TYPE;
	}
	@Override
	public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException
	{
		return super.openAssetFile(uri, mode);
	}
	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException
	{
		try
		{
			Bitmap bitmap=ExposeQRCodeFragment.buildQRCode(getContext(),100);
			FileOutputStream out = getContext().openFileOutput("qrcode.png", Context.MODE_PRIVATE);
		    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
		    out.close();

		    FileInputStream f=getContext().openFileInput("qrcode.png");
		    f.close();
		    
		    return ParcelFileDescriptor.open(getContext().getFileStreamPath("qrcode.png"), ParcelFileDescriptor.MODE_READ_ONLY);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return null;
	}
	@TargetApi(11)
	@Override
	public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
			throws FileNotFoundException
	{
		if (!QRCODE_URI.equals(uri))
		{
			if (W) Log.w(TAG_PROVIDER,"Unknown "+uri);
			return null;
		}
		if (mimeTypeFilter!=null && !MIME_TYPE.equals(mimeTypeFilter))
		{
			if (W) Log.w(TAG_PROVIDER,"Unknown mime type "+mimeTypeFilter);
			return null;
		}
		PipeDataWriter<Void> pm=new PipeDataWriter<Void>()
		{
			@Override
			public void writeDataToPipe(ParcelFileDescriptor output, 
					Uri uri, 
					String mimeType,
		            Bundle opts, 
		            Void c)
			{
		        FileOutputStream out = new FileOutputStream(output.getFileDescriptor());
				Bitmap bitmap=ExposeQRCodeFragment.buildQRCode(getContext(),100);
//				FileOutputStream out = getContext().openFileOutput("qrcode.png", Context.MODE_PRIVATE);
			    if (bitmap!=null)
			    	bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
			}
		};
		return new AssetFileDescriptor(
            openPipeHelper(uri, mimeTypeFilter, opts, (Void)null, pm), 0,
            AssetFileDescriptor.UNKNOWN_LENGTH);
	}
    /**
     * Implementation of {@link android.content.ContentProvider.PipeDataWriter}
     * to perform the actual work of converting the data in one of cursors to a
     * stream of data for the client to read.
     */
//    @Override
//    public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
//            Bundle opts, Cursor c) 
//    {
//        // We currently only support conversion-to-text from a single note entry,
//        // so no need for cursor data type checking here.
//        FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
//        PrintWriter pw = null;
//        try {
//            pw = new PrintWriter(new OutputStreamWriter(fout, "UTF-8"));
//            pw.println(c.getString(READ_NOTE_TITLE_INDEX));
//            pw.println("");
//            pw.println(c.getString(READ_NOTE_NOTE_INDEX));
//        } catch (UnsupportedEncodingException e) {
//            Log.w(TAG, "Ooops", e);
//        } finally {
//            c.close();
//            if (pw != null) {
//                pw.flush();
//            }
//            try {
//                fout.close();
//            } catch (IOException e) {
//            }
//        }
//    }	
}    
