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
package org.droid2droid.ui.contacts;

import java.util.ArrayList;
import java.util.List;

import org.droid2droid.R;
import org.droid2droid.ui.Collapser;
import org.droid2droid.ui.Collapser.Collapsible;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;

/**
 * Class used for displaying a dialog with a list of phone numbers of which one
 * will be chosen to make a call or initiate an sms message.
 */
public final class PhoneDisambigDialog implements DialogInterface.OnClickListener,DialogInterface.OnDismissListener
{
	public interface CallBack
	{
		public void sendData(final String receiver);
	}
	private final Context mContext;

	private final CallBack mCallback;
	private final AlertDialog mDialog;

	private final ListAdapter mPhonesAdapter;

	private ArrayList<PhoneItem> mPhoneItemList;

	public PhoneDisambigDialog(Context context, CallBack callback,long id)
	{
		mContext = context;
		mCallback=callback;

		makePhoneItemsList(id);
		Collapser.collapseList(mPhoneItemList);

		mPhonesAdapter = new PhonesAdapter(mContext, mPhoneItemList);

		mDialog=new AlertDialog.Builder(mContext)
				.setAdapter(mPhonesAdapter, this)
				.setTitle(R.string.connect_sms_disambig_title)
				.create();
	}

	/**
	 * Show the dialog.
	 */
	public void show()
	{
		if (mPhoneItemList.size() == 1)
		{
			// If there is only one after collapse, just select it, and close;
			onClick(mDialog, 0);
			return;
		}
		mDialog.show();
	}

	@Override
	public void onClick(DialogInterface dialog, int which)
	{
		if (mPhoneItemList.size() > which && which >= 0)
		{
			PhoneItem phoneItem = mPhoneItemList.get(which);
			String phoneNumber = phoneItem.phoneNumber;
			mCallback.sendData(phoneNumber);
			dialog.dismiss();
		}
		else
		{
			dialog.dismiss();
		}
	}

	@Override
	public void onDismiss(DialogInterface dialog)
	{
	}

	private static final class PhonesAdapter extends ArrayAdapter<PhoneItem>
	{

		public PhonesAdapter(Context context, List<PhoneItem> objects)
		{
			super(context, android.R.layout.simple_dropdown_item_1line,
					android.R.id.text1, objects);
		}
	}

	private final class PhoneItem implements Collapsible<PhoneItem>
	{

		String phoneNumber;

		public PhoneItem(String newPhoneNumber)
		{
			phoneNumber = newPhoneNumber;
		}

		@Override
		public boolean collapseWith(PhoneItem phoneItem)
		{
			if (!shouldCollapseWith(phoneItem))
			{
				return false;
			}
			// Just keep the number and id we already have.
			return true;
		}

		@Override
		public boolean shouldCollapseWith(PhoneItem phoneItem)
		{
			if (VERSION.SDK_INT>=VERSION_CODES.ECLAIR)
			{
				if (PhoneNumberUtils.compare(
					PhoneDisambigDialog.this.mContext, phoneNumber,
					phoneItem.phoneNumber))
				{
					return true;
				}
			}
			else
			{
				if (PhoneNumberUtils.compare(phoneNumber,phoneItem.phoneNumber))
				{
					return true;
				}
			}
			return false;
		}

		@Override
		public String toString()
		{
			return phoneNumber;
		}
	}
	private static final String[] sProjectionPhone =
	{ 
		Phone.NUMBER,  // TODO: Use old API for ECLAIR
	};
	private static final int POS_PHONE_NUMBER=0;


	private void makePhoneItemsList(long id)
	{
		mPhoneItemList = new ArrayList<PhoneItem>(5);
		ContentResolver contentResolver=mContext.getContentResolver();
		final Cursor cphon = contentResolver.query(
			Phone.CONTENT_URI, 
			sProjectionPhone,
			Phone.CONTACT_ID + "=" + id, 
			null, null);
		if (cphon == null)
			return;
		while (cphon.moveToNext())
		{
			final String phonenumber = cphon.getString(POS_PHONE_NUMBER);
			mPhoneItemList.add(new PhoneItem(phonenumber));
		}
		cphon.close();
	}
}