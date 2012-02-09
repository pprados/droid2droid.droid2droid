package org.remoteandroid.ui.contacts;

import static org.remoteandroid.Constants.TAG_SMS;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_TELEPHONY;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.ui.TabsAdapter;
import org.remoteandroid.ui.connect.old.AbstractBodyFragment;
import org.remoteandroid.ui.contacts.PhoneDisambigDialog;
import org.remoteandroid.ui.contacts.SMSSendingActivity.SendSMSDialogFragment;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.text.Editable;
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
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

//TODO: Use AsyncLoader, et loader partiel
public class AbstractSMSFragment extends AbstractBodyFragment
implements TextWatcher, OnScrollListener, PhoneDisambigDialog.CallBack
{
	private static final String[] sProjection =
	{ 
		Contacts._ID,
		Contacts.DISPLAY_NAME 
	};

	private static final int POS_ID=0;
	private static final int POS_DISPLAY_NAME=1;
	
	static class Cache
	{
		private TextView text;

		private ImageView icon;

		private TextView name;
	}
	
	
	class ContactClassAsyncTask extends AsyncTask<String, Void, Void>
	{

		private ArrayList<String> mTmpPhoneListNumber;

		private ArrayList<String> mTmpContactName;

		private ArrayList<Long> mTmpListIdContact;

		@Override
		protected void onPreExecute()
		{
			super.onPreExecute();
			mTmpContactName = new ArrayList<String>();
			mTmpPhoneListNumber = new ArrayList<String>();
			mTmpListIdContact = new ArrayList<Long>();
		}

		@Override
		protected Void doInBackground(String... filterStr)
		{
			Cursor cur = null;
			if (filterStr[0].equals(""))
			{
				cur = mContentResolver.query(
					Contacts.CONTENT_URI,
						sProjection,
						Contacts.HAS_PHONE_NUMBER+"=1", 
						null, 
						Contacts.DISPLAY_NAME + " ASC");
			}
			else
			{
				cur = mContentResolver.query(
					Contacts.CONTENT_URI, 
					sProjection, 
					Contacts.DISPLAY_NAME	+ " LIKE ? "+
					"AND "+ContactsContract.Contacts.HAS_PHONE_NUMBER+"=1", 
					new String[] { filterStr[0] }, null);
			}

			if (cur == null)
				return null;

			while (cur.moveToNext())
			{
				final long contactId = cur.getLong(POS_ID);
				final String name= cur.getString(POS_DISPLAY_NAME);
				mTmpContactName.add(name);
				mTmpListIdContact.add(contactId);
			}
			cur.close();
			return null;
		}

		@Override
		protected void onPostExecute(Void result)
		{
			super.onPostExecute(result);

			mContactName=mTmpContactName;
			mListIdContact=mTmpListIdContact;
			mEfficientAdapter.notifyDataSetChanged();
		}

	}
	
	public class EfficientAdapter extends BaseAdapter
	{

		private LayoutInflater mInflater;

		private Drawable defaultContactIcon;

		public EfficientAdapter(Context context)
		{
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			defaultContactIcon = getResources().getDrawable(R.drawable.default_contact_icon);
		}

		@Override
		public int getCount()
		{
			return mListIdContact.size();
		}

		@Override
		public Object getItem(int position)
		{
			return mListIdContact.get(position);
		}

		@Override
		public long getItemId(int position)
		{
			return mListIdContact.get(position);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			Cache holder;
			if (convertView == null)
			{
				convertView = mInflater.inflate(R.layout.connect_sms_list_item_icon_text, parent, false);

				holder = new Cache();
				holder.icon = (ImageView) convertView.findViewById(R.id.icon);
				holder.name = (TextView) convertView.findViewById(R.id.name);

				convertView.setTag(holder);
			}
			else
				holder = (Cache) convertView.getTag();

			if (!sBusy)
			{
				final Long contactId = mListIdContact.get(position);
				if (sBitmapCache.containsKey(contactId))
				{
					holder.icon.setImageBitmap(sBitmapCache.get(contactId).get());
				}
				else
				{
					// TODO: Use old API for ECLAIR
					final Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
					final InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(mContentResolver, uri);
					if (input != null)
					{
						final Bitmap contactPhoto = BitmapFactory.decodeStream(input);
						sBitmapCache.put(contactId, new SoftReference<Bitmap>(contactPhoto));
						holder.icon.setImageBitmap(contactPhoto);
					}
					else
					{
						holder.icon.setImageDrawable(defaultContactIcon);
					}
				}
				holder.icon.setTag(null);
			}
			else
			{
				Long contactId = mListIdContact.get(position);
				if (sBitmapCache.containsKey(contactId) == true)
					holder.icon.setImageBitmap(sBitmapCache.get(contactId).get());
				else
					holder.icon.setImageDrawable(defaultContactIcon);
				holder.icon.setTag(this);
			}
			holder.name.setText(mContactName.get(position));

			return convertView;
		}

	}
	
	
	private View 			mMain;
	private boolean sBusy = false;

	private ContentResolver mContentResolver;

	private EfficientAdapter mEfficientAdapter;

	private ListView mContactList;

	private ContactClassAsyncTask mContactAsync = null;

	private ArrayList<String> mContactName;

	private ArrayList<Long> mListIdContact;

	private ArrayList<String> mSelectedContact;

	private static HashMap<Long, SoftReference<Bitmap>> sBitmapCache = null;

	private byte[] mSendedData;
	protected FragmentManager mFragmentManager;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mListIdContact = new ArrayList<Long>();
		mContactName = new ArrayList<String>();
		if (sBitmapCache == null)
			sBitmapCache = new HashMap<Long, SoftReference<Bitmap>>();
		mContentResolver = getActivity().getContentResolver();
		mSelectedContact = new ArrayList<String>();
	}
	
	private void initViewElements()
	{
		final EditText editText = (EditText) mMain.findViewById(R.id.numberEditText);
		editText.addTextChangedListener(this);
		editText.setOnEditorActionListener(new OnEditorActionListener()
		{
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
			{
				if (actionId == EditorInfo.IME_ACTION_SEND)
				{
					final String receiver = editText.getText().toString();
					if (mSendedData != null || receiver != null)
						if (receiver.matches("[0123456789#*()\\- ]*"))
							sendData(receiver);
					return true;
				}
				return false;
			}
		});
	}

	private void initListView()
	{
		mEfficientAdapter = new EfficientAdapter(getActivity());

		mContactList = (ListView) mMain.findViewById(R.id.contactListView);
		mContactList.setOnScrollListener(this);
		mContactList.setFastScrollEnabled(true);
		mContactList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		mContactList.setAdapter(mEfficientAdapter);
		mContactList.setOnItemClickListener(new OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int position, long arg3)
			{
				final long id = (Long) adapter.getItemAtPosition(position);
				
				final PhoneDisambigDialog phoneDialog = 
						new PhoneDisambigDialog(getActivity(), AbstractSMSFragment.this,id);
				phoneDialog.show();
			}
		});
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mMain=inflater.inflate(R.layout.expose_sms, container, false);
		initViewElements();
		initListView();
		return mMain;
	}
	
	@Override
	public void onLowMemory()
	{
		super.onLowMemory();
		sBitmapCache.clear();

	}

	@Override
	public void afterTextChanged(Editable s)
	{
		if (V) Log.v(TAG_SMS, s.toString());
		if (mContactAsync != null)
			mContactAsync.cancel(true);
		mContactAsync = new ContactClassAsyncTask();
		
		mContactAsync.execute("%" + s.toString().replaceAll(" +", "%") + "%"); // FIXME: SQLI
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after)
	{
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count)
	{

	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
	{
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState)
	{
		switch (scrollState)
		{
			case OnScrollListener.SCROLL_STATE_IDLE:
				sBusy = false;

				int first = view.getFirstVisiblePosition();
				int count = view.getChildCount();

				for (int i = 0; i < count; i++)
				{

					Cache holder=(Cache)view.getChildAt(i).getTag();
					holder.icon = (ImageView) view.getChildAt(i).findViewById(R.id.icon);

					if (holder.icon.getTag() != null)
					{
						Long contactId = mListIdContact.get(first + i);

						if (sBitmapCache.containsKey(contactId))
						{
							holder.icon.setImageBitmap(sBitmapCache.get(
								contactId).get());
						}
						else
						{
							Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
							try
							{
								InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(mContentResolver, uri);
								if (input != null)
								{
									Bitmap contactPhoto = BitmapFactory.decodeStream(input);
									sBitmapCache.put(contactId, new SoftReference<Bitmap>(contactPhoto));
									holder.icon.setImageBitmap(contactPhoto);
									input.close();
								}
							}
							catch (IOException e)
							{
								if (W) Log.w(TAG_SMS,PREFIX_LOG+"Error when close image ("+e.getMessage()+")");
							}
						}
						holder.icon.setTag(null);
					}
				}

				break;
			case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
				sBusy = true;
				break;
			case OnScrollListener.SCROLL_STATE_FLING:
				sBusy = true;
				break;
		}
	}
	@Override
	public void onPause()
	{
		super.onPause();
		sBitmapCache.clear();
	}
	
	public void sendData(final String receiver)
	{
		Application.hideSoftKeyboard(getActivity());
		final SendSMSDialogFragment dlg=SendSMSDialogFragment.sendSMS(receiver);
		dlg.show(mFragmentManager, "dialog");

		
	}
	
}
