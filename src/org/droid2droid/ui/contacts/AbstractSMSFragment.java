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

import static org.droid2droid.Constants.TAG_SMS;
import static org.droid2droid.internal.Constants.D;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.W;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.droid2droid.R;
import org.droid2droid.RAApplication;
import org.droid2droid.ui.connect.AbstractConnectFragment;

import android.annotation.TargetApi;
import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

//TODO: compatibilité 1.6
public abstract class AbstractSMSFragment extends AbstractConnectFragment 
implements OnScrollListener,
TextWatcher,
PhoneDisambigDialog.CallBack,
LoaderManager.LoaderCallbacks<Cursor>
{
	private static final int LOADER_ID=0;
	private static final String[] CONTACTS_PROJECTION =
	{ 
		Contacts._ID, 
		Contacts.DISPLAY_NAME, 
	};
	private static final int POS_ID = 0;

	private static final int FETCH_IMAGE_MSG = 1;
	private static ExecutorService sImageFetchThreadPool = Executors.newFixedThreadPool(3);

	private ContentResolver mContentResolver;
	private View mMain;
	public TextView mUsage;
	protected EditText mEditText;
	protected ListView mList;
	private byte[] mSendedData;
	private String mCurFilter;
	
	// This is the Adapter being used to display the list's data.
	private SimpleCursorAdapter mAdapter;

	// --------- Manage image download ----------------
	private static HashMap<Long, Reference<Bitmap>> sBitmapCache = new HashMap<Long, Reference<Bitmap>>();;	
	private final ImageFetchHandler mHandler=new ImageFetchHandler();
	private class ImageFetchHandler extends Handler
	{
		@Override
		public void handleMessage(final Message message)
		{
			if (getActivity().isFinishing())
			{
				return;
			}
			switch (message.what)
			{
				case FETCH_IMAGE_MSG:
				{
					final QuickContactBadge imageView = (QuickContactBadge) message.obj;
					final long contactId=(long)message.arg1<<16|message.arg2;
					if (imageView == null)
						break;
					final Reference<Bitmap> photoRef = sBitmapCache.get(contactId);
					if (photoRef == null)
					{
						setDefaultImage(imageView);;
						break;
					}
					final Bitmap photo = photoRef.get();
					
					if (photo == null)
					{
						setDefaultImage(imageView);;
						sBitmapCache.remove(contactId);
						break;
					}
					imageView.setImageBitmap(photo);
					imageView.invalidate();
					break;
				}
			}
		}

		public void clearImageFecthing()
		{
			removeMessages(FETCH_IMAGE_MSG);
		}
	}

	private class ImageDbFetcher implements Runnable
	{
		private final long mContactId;

		private final ImageView mImageView;

		public ImageDbFetcher(final long contactId, final ImageView imageView)
		{
			mContactId = contactId;
			mImageView = imageView;
		}

		@Override
		public void run()
		{
			if (getActivity().isFinishing())
			{
				return;
			}

			if (Thread.interrupted())
			{
				return;
			}

			Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, mContactId);
			try
			{
				InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(mContentResolver, uri);
				if (input != null)
				{
					Bitmap contactPhoto = BitmapFactory.decodeStream(input);
					// TODO: resize bitmap
					sBitmapCache.put(mContactId, 
						new SoftReference<Bitmap>(contactPhoto)
						);
					input.close();
				}
			}
			catch (IOException e)
			{
				if (W) Log.w(TAG_SMS,PREFIX_LOG+"Error when close image ("+e.getMessage()+")");
			}
			
			if (Thread.interrupted())
			{
				return;
			}

			// Update must happen on UI thread
			final Message msg = new Message();
			msg.what = FETCH_IMAGE_MSG;
			msg.obj = mImageView;
			msg.arg1= (int)(mContactId >>> 32);
			msg.arg2= (int)(mContactId);
			mHandler.sendMessage(msg);
		}
	}
	
	private class ContactsAdapter extends SimpleCursorAdapter
	{
		ContactsAdapter(Context context,int layout,Cursor c,String[] from,int[] to,int flags)
		{
			super(context,layout,c,from,to,flags);
		}
		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			return super.getView(position, convertView, parent);
		}
		/** {@inheritDoc} */
		@Override
		public void bindView(final View view, final Context context, final Cursor cursor)
		{
			super.bindView(view, context, cursor);
			long contactId=cursor.getLong(POS_ID);
			QuickContactBadge imageView=((QuickContactBadge)view.findViewById(R.id.icon));
			Bitmap photo=null;
			final Reference<Bitmap> ref = sBitmapCache.get(contactId);
			if (ref != null)
			{
				photo = ref.get();
				if (photo == null) // Lose weak reference
				{
					sBitmapCache.remove(contactId);
					setDefaultImage(imageView);
				}
				else
				{
					imageView.setImageBitmap(photo);
					return;
				}
			}
			else
				setDefaultImage(imageView);
			sImageFetchThreadPool.execute(new ImageDbFetcher(contactId, imageView));
		}
	}
	@TargetApi(11)
	private void setDefaultImage(QuickContactBadge imageView)
	{
		if (VERSION.SDK_INT>=VERSION_CODES.HONEYCOMB)
		{
			imageView.setImageToDefault();
		}
		else
		{
			imageView.setImageResource(R.drawable.ic_contact_list_picture);
		}
	}

	//------------- Manage views --------------
	private static final String[] COLS_VIEW=new String[]{ Contacts.DISPLAY_NAME };
	private static final int[] MAPPING_VIEW=new int[]{ R.id.name };
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		mContentResolver=getActivity().getContentResolver();

		// Create an empty adapter we will use to display the loaded data.
		mAdapter = new ContactsAdapter(
			getActivity(), 
			R.layout.connect_sms_list_item_icon_text, 
			null, 
			COLS_VIEW, 
			MAPPING_VIEW, 
			0);
		mList.setAdapter(mAdapter);

		getLoaderManager().initLoader(LOADER_ID, null, this);
	}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mMain=inflater.inflate(R.layout.expose_sms, container, false);
		
		mUsage=(TextView)mMain.findViewById(R.id.usage);
		
		mEditText = (EditText) mMain.findViewById(R.id.numberEditText);
		mEditText.addTextChangedListener(this);
		mEditText.setOnEditorActionListener(new OnEditorActionListener()
		{
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
			{
				if (actionId == EditorInfo.IME_ACTION_SEND)
				{
					final String receiver = mEditText.getText().toString();
					if (mSendedData != null || receiver != null)
						if (receiver.matches("[0123456789#*()\\- ]*"))
							sendData(receiver);
					return true;
				}
				return false;
			}
		});
		
		mList=(ListView)mMain.findViewById(android.R.id.list);
		mList.setOnScrollListener(this);
		mList.setFastScrollEnabled(true);
		mList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		mList.setOnItemClickListener(new OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int position, long arg3)
			{
				final long id = ((Cursor) adapter.getItemAtPosition(position)).getLong(POS_ID);
				if (id>0)
				{
					RAApplication.hideSoftKeyboard(getActivity());
					final PhoneDisambigDialog phoneDialog = 
							new PhoneDisambigDialog(getActivity(), AbstractSMSFragment.this,id);
					phoneDialog.show();
				}
			}
		});
		return mMain;
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		mHandler.clearImageFecthing();
	}
	
	// --------- Manage loader ----------------
	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args)
	{
		Uri baseUri;
		if (mCurFilter != null)
		{
			baseUri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(mCurFilter));
		}
		else
		{
			baseUri = Contacts.CONTENT_URI;
		}
		// Now create and return a CursorLoader that will take care of
		// creating a Cursor for the data being displayed.
		String select = "((" + Contacts.DISPLAY_NAME + " NOTNULL) AND (" + Contacts.HAS_PHONE_NUMBER + "=1) AND ("
				+ Contacts.DISPLAY_NAME + " != '' ))";
		if (D) Log.d(TAG_SMS,PREFIX_LOG+"baseURI="+baseUri);
		if (D) Log.d(TAG_SMS,PREFIX_LOG+"select="+select);
		return new CursorLoader(getActivity(), baseUri, CONTACTS_PROJECTION, select, null,
				Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data)
	{
		// Swap the new cursor in. (The framework will take care of closing the
		// old cursor once we return.)
		mAdapter.swapCursor(data);

		// The list should now be shown.
		mList.setVisibility(View.VISIBLE);
	}
	
	@Override
	public void onLoaderReset(Loader<Cursor> loader)
	{
		// This is called when the last Cursor provided to onLoadFinished()
		// above is about to be closed. We need to make sure we are no
		// longer using it.
		mAdapter.swapCursor(null);
	}
	
	// --------- Manage scroll ----------------
	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState)
	{
		switch (scrollState)
		{
			case OnScrollListener.SCROLL_STATE_IDLE:
				int first = view.getFirstVisiblePosition();
				int count = view.getChildCount();

				for (int i = 0; i < count; i++)
				{
					long contactId=mAdapter.getItemId(first+i);
					if (contactId!=0)
					{
						QuickContactBadge icon=(QuickContactBadge)mList.getChildAt(i).findViewById(R.id.icon);
						if (sBitmapCache.containsKey(contactId))
						{
							icon.setImageBitmap(sBitmapCache.get(contactId).get());
						}
						else
						{
							setDefaultImage(icon);
							sImageFetchThreadPool.execute(new ImageDbFetcher(contactId, icon));
						}
					}
				}

				break;
		}
	}
	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
	{
	}
	// --------- Manage textchange ----------------
	@Override
	public void afterTextChanged(Editable s)
	{
		String newText=s.toString();
		mCurFilter = !TextUtils.isEmpty(newText) ? newText : null;
		getLoaderManager().restartLoader(LOADER_ID, null, this);
	}

	@Override
	public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3)
	{
		
	}
	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count)
	{
		
	}
	public static void onTrimMemory(int level)
	{
		if (level==ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
		{
			sBitmapCache.clear();
			if (D) Log.d(TAG_SMS,PREFIX_LOG+"Clean contacts pictures");
		}
	}
}
