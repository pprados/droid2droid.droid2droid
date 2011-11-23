package org.remoteandroid.ui.connect.sms;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.*;
import org.remoteandroid.R;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.connect.SMSFragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
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

public class SMSSendingActivity extends Activity implements TextWatcher, OnScrollListener
{

	private static Cache sHolder;

	private static boolean sBusy = false;

	private static final Short sSendingPort = 6800;

	private ContentResolver mContentResolver;

	private EfficientAdapter mEfficientAdapter;

	private ListView mContactList;

	private ContactClassAsyncTask mContactAsync = null;

	private volatile ArrayList<String> mPhoneNumberList;

	private volatile ArrayList<String> mContactName;

	private ArrayList<Long> mListIdContact;

	private ArrayList<String> mSelectedContact;

	private HashMap<Long, SoftReference<Bitmap>> mBitmapCache = null;

	private byte[] mSendedData;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.connect_sms);

		mPhoneNumberList = new ArrayList<String>();
		mListIdContact = new ArrayList<Long>();
		mContactName = new ArrayList<String>();
		if (mBitmapCache == null)
			mBitmapCache = new HashMap<Long, SoftReference<Bitmap>>();
		mContentResolver = getContentResolver();
		mSelectedContact = new ArrayList<String>();

		initViewElements();

		initListView();
	}

	public void initViewElements()
	{
		final EditText editText = (EditText) findViewById(R.id.numberEditText);
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
						if (receiver.matches("[:digit:]*"))
							sendData(
								mSendedData, receiver);
					return true;
				}
				return false;
			}
		});
	}

	public void initListView()
	{
		mEfficientAdapter = new EfficientAdapter(SMSSendingActivity.this);

		mContactList = (ListView) findViewById(R.id.contactListView);
		mContactList.setOnScrollListener(this);
		mContactList.setFastScrollEnabled(true);
		mContactList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		mContactList.setAdapter(mEfficientAdapter);
		mContactList.setOnItemClickListener(new OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> adapter, View view, int position, long arg3)
			{
				final int itemPosition = (Integer) adapter.getItemAtPosition(position);
				final String selectedPhoneNumber = mPhoneNumberList.get(itemPosition);
				confirmSelection(
					itemPosition, selectedPhoneNumber, view.getContext());
			}
		});
	}

	private void confirmSelection(int itemPosition, final String selectedPhoneNumber, Context context)
	{
		// TODO : Internationalisation
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(
			"Voulez-vous envoyer une requête Remote Android à : " + mContactName.get(itemPosition) + "sur le numéro : "
					+ selectedPhoneNumber).setCancelable(
			false).setPositiveButton(
			"Oui", new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int id)
				{
					if (mSendedData != null)
					{
						sendData(
							mSendedData, selectedPhoneNumber);
					}
				}
			}).setNegativeButton(
			"No", new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int id)
				{
					dialog.cancel();
				}
			});
		final AlertDialog alert = builder.create();
		alert.show();
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		new AsyncTask<Void, Void, byte[]>()
		{
			@Override
			protected byte[] doInBackground(Void... params)
			{
				Messages.Candidates candidates;
				try
				{
					candidates = Trusted.getConnectMessage(SMSSendingActivity.this);
					return candidates.toByteArray();
				}
				catch (UnknownHostException e1)
				{
					if (E)
						Log.e(
							TAG_SMS, "UnknownHostException : " + e1.getMessage());
				}
				catch (SocketException e1)
				{
					if (E)
						Log.e(
							TAG_SMS, "SocketException : " + e1.getMessage());
				}
				return null;
			}

			@Override
			protected void onPostExecute(byte[] data)
			{
				// TODO uncomment this line after testing
				mSendedData = data;
//				mSendedData = new byte[]
//				{ 'A', 'B', 'C', 'D', 'E' };
				if (V)
					Log.v(
						TAG_SMS, "length of byte array = " + data.length);
			}
		}.execute();

		if (mContactAsync == null)
		{
			mContactAsync = new ContactClassAsyncTask();
			mContactAsync.execute("");
		}
	}

	public void sendData(byte[] buf, String receiver)
	{
		int fragmentSize = SMSFragment.MESSAGE_SIZE;
		if (V)
		{
			Log.v(
				TAG_SMS, "buf to send length = " + buf.length);
			Log.v(
				TAG_SMS, "MESSAGE SIZE = " + fragmentSize);
		}
		if (buf.length < fragmentSize)
			fragmentSize = buf.length + 1;
		byte[] fragment = new byte[fragmentSize];
		int fragNumber = 0;
		for (int i = 0; i < buf.length; i += (SMSFragment.MESSAGE_SIZE - 1))
		{
			if (V)
				Log.v(
					TAG_SMS, "fragNumber = " + fragNumber);
			boolean last = (buf.length - i) < (SMSFragment.MESSAGE_SIZE - 1);
			if (V)
				Log.v(
					TAG_SMS, "is last message = " + last);
			int len = Math.min(
				buf.length - i, SMSFragment.MESSAGE_SIZE - 1);
			if (V)
				Log.v(
					TAG_SMS, "len = " + len);
			System.arraycopy(
				buf, i, fragment, 1, len);
			fragment[0] = (byte) ((last ? 0x80 : 0) | fragNumber);
			if (V)
				Log.v(
					TAG_SMS, "fragment[0] = " + fragment[0]);
			sendSMS(
				receiver, fragment);
			++fragNumber;
		}
	}

	protected void sendSMS(byte[] text)
	{
		for (String destination : mSelectedContact)
		{
			sendSMS(
				destination, text);
		}
	}

	protected void sendSMS(String receiver, byte[] text)
	{
		if (V)
			Log.v(
				TAG_SMS, "Destination = " + receiver);
		SmsManager.getDefault().sendDataMessage(
			receiver, null, sSendingPort, text, null, null);
	}

	public class EfficientAdapter extends BaseAdapter
	{

		private LayoutInflater mInflater;

		private Drawable defaultContactIcon;

		private Context mContext;

		public EfficientAdapter(Context context)
		{
			mContext = context;
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			defaultContactIcon = getResources().getDrawable(
				R.drawable.default_contact_icon);
		}

		public int getCount()
		{
			return mPhoneNumberList.size();
		}

		public Object getItem(int position)
		{
			return position;
		}

		public long getItemId(int position)
		{
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent)
		{
			if (convertView == null)
			{
				convertView = mInflater.inflate(
					R.layout.list_item_icon_text, parent, false);

				sHolder = new Cache();
				sHolder.text = (TextView) convertView.findViewById(R.id.text);
				sHolder.icon = (ImageView) convertView.findViewById(R.id.icon);
				sHolder.name = (TextView) convertView.findViewById(R.id.name);

				convertView.setTag(sHolder);
			}
			else
				sHolder = (Cache) convertView.getTag();

			if (!sBusy)
			{
				final Long contactId = mListIdContact.get(position);
				if (mBitmapCache.containsKey(contactId))
				{
					sHolder.icon.setImageBitmap(mBitmapCache.get(
						contactId).get());
				}
				else
				{
					final Uri uri = ContentUris.withAppendedId(
						ContactsContract.Contacts.CONTENT_URI, contactId);
					final InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(
						mContentResolver, uri);
					if (input != null)
					{
						final Bitmap contactPhoto = BitmapFactory.decodeStream(input);
						mBitmapCache.put(
							contactId, new SoftReference<Bitmap>(contactPhoto));
						sHolder.icon.setImageBitmap(contactPhoto);
					}
					else
					{
						sHolder.icon.setImageDrawable(defaultContactIcon);
					}
				}
				sHolder.icon.setTag(null);
			}
			else
			{
				Long contactId = mListIdContact.get(position);
				if (mBitmapCache.containsKey(contactId) == true)
					sHolder.icon.setImageBitmap(mBitmapCache.get(
						contactId).get());
				else
					sHolder.icon.setImageDrawable(defaultContactIcon);
				sHolder.icon.setTag(this);
			}
			sHolder.text.setText(mPhoneNumberList.get(position));
			sHolder.name.setText(mContactName.get(position));

			return convertView;
		}

	}

	@Override
	public void onLowMemory()
	{
		super.onLowMemory();
		mBitmapCache.clear();
		mBitmapCache = null;

	}

	static class Cache
	{
		private TextView text;

		private ImageView icon;

		private TextView name;
	}

	class ContactClassAsyncTask extends AsyncTask<String, Void, Void>
	{

		private ArrayList<String> _tmpPhoneListNumber;

		private ArrayList<String> _tmpContactName;

		private ArrayList<Long> _tmpListIdContact;

		@Override
		protected void onPreExecute()
		{
			super.onPreExecute();
			_tmpContactName = new ArrayList<String>();
			_tmpPhoneListNumber = new ArrayList<String>();
			_tmpListIdContact = new ArrayList<Long>();
		}

		@Override
		protected Void doInBackground(String... filterStr)
		{
			Cursor cur = null;
			if (filterStr[0].equals(""))
				cur = mContentResolver.query(
					ContactsContract.Contacts.CONTENT_URI, null, null, null, ContactsContract.Contacts.DISPLAY_NAME
							+ " ASC");
			else
			{
				final String[] projection =
				{ ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME,
						ContactsContract.Contacts.HAS_PHONE_NUMBER };
				cur = mContentResolver.query(
					ContactsContract.Contacts.CONTENT_URI, projection, ContactsContract.Contacts.DISPLAY_NAME
							+ " LIKE ?", new String[]
					{ filterStr[0] }, null);
				if (V)
				{
					Log.v(
						TAG_SMS, "filtering string = " + filterStr[0]);
					Log.v(
						TAG_SMS, "cursor count = " + cur.getCount());
				}
			}

			if (cur == null)
				return null;

			while (cur.moveToNext())
			{
				final String contactId = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
				final String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
				final String hasphone = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER));

				if (hasphone.equals("1"))
				{
					final Cursor cphon = mContentResolver.query(
						ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
						ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=" + contactId, null, null);
					if (cphon == null)
						continue;
					while (cphon.moveToNext())
					{
						final String phonenumber = cphon.getString(cphon
								.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
						_tmpPhoneListNumber.add(phonenumber);
						_tmpContactName.add(name);
						_tmpListIdContact.add(Long.parseLong(contactId));
					}
				}
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result)
		{
			super.onPostExecute(result);
			mPhoneNumberList.clear();
			mPhoneNumberList.addAll(_tmpPhoneListNumber);

			mContactName.clear();
			mContactName.addAll(_tmpContactName);

			mListIdContact.clear();
			mListIdContact.addAll(_tmpListIdContact);

			mEfficientAdapter.notifyDataSetChanged();
		}

	}

	@Override
	public void afterTextChanged(Editable s)
	{
		if (V)
		Log.v(
			TAG_SMS, s.toString());
		if (mContactAsync != null)
			mContactAsync.cancel(true);
		mContactAsync = new ContactClassAsyncTask();
		mContactAsync.execute("%" + s.toString() + "%");
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

					sHolder.icon = (ImageView) view.getChildAt(
						i).findViewById(
						R.id.icon);

					if (sHolder.icon.getTag() != null)
					{
						Long contactId = mListIdContact.get(first + i);

						if (mBitmapCache.containsKey(contactId))
						{
							sHolder.icon.setImageBitmap(mBitmapCache.get(
								contactId).get());
						}
						else
						{
							Uri uri = ContentUris.withAppendedId(
								ContactsContract.Contacts.CONTENT_URI, contactId);
							InputStream input = ContactsContract.Contacts.openContactPhotoInputStream(
								mContentResolver, uri);
							if (input != null)
							{
								Bitmap contactPhoto = BitmapFactory.decodeStream(input);
								mBitmapCache.put(
									contactId, new SoftReference<Bitmap>(contactPhoto));
								sHolder.icon.setImageBitmap(contactPhoto);
							}
						}
						sHolder.icon.setTag(null);
					}
					else
					{
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
}