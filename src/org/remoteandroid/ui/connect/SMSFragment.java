package org.remoteandroid.ui.connect;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.remoteandroid.R;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
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
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

public class SMSFragment extends AbstractBodyFragment implements TextWatcher,
		OnScrollListener {
	private View mViewer;
	private volatile ArrayList<String> list;
	private volatile ArrayList<String> copyList;
	private ArrayAdapter<String> arrayAdapter;
	private ContentResolver cr;
	private ArrayList<String> selectedContact;
	// private ListView listView;
	private boolean isMultipleChoice = false;
	private final static String smsListingPort = "6800";
	private final byte[] message = "0#RemoteAndroid?ParingRequestFirstPart"
			.getBytes();
	private final byte[] message2 = "1#RemoteAndroid?ParingRequestFirstPart"
			.getBytes();
	private final byte[] message3 = "2#RemoteAndroid?ParingRequestFirstPart"
			.getBytes();
	private static boolean _busy = false;
	static Cache _holder;
	private ContentResolver _contentResolver;
	private volatile ArrayList<String> _phoneNumberList;
	private volatile ArrayList<String> _contactName;
	private EfficientAdapter _efficientAdapter;
	private ArrayList<Long> _listIdContact;
	private HashMap<Long, SoftReference<Bitmap>> _bitmapCache = null;
	private ListView _contactList;

	public class EfficientAdapter extends BaseAdapter {

		private LayoutInflater mInflater;
		private Drawable defaultContactIcon;
		private Context mContext;

		public EfficientAdapter(Context context) {
			Log.e("SMSFragment", "Init EfficientAdapter");
			mContext = context;
			mInflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			defaultContactIcon = getResources().getDrawable(
					R.drawable.default_contact_icon);
		}

		public int getCount() {
			// Log.e("SMSFragment", "get Count = " + _phoneNumberList.size());
			return _phoneNumberList.size();
		}

		public Object getItem(int position) {
			Log.e("SMSFragment", "position item = " + position);
			return position;
		}

		public long getItemId(int position) {
			// Log.e("SMSFragment", "position itemid = " + position);
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			// Log.e("SMSFragment", "In get View");
			if (convertView == null) {
				convertView = mInflater.inflate(R.layout.list_item_icon_text,
						parent, false);

				_holder = new Cache();
				_holder.text = (TextView) convertView.findViewById(R.id.text);
				_holder.icon = (ImageView) convertView.findViewById(R.id.icon);
				_holder.name = (TextView) convertView.findViewById(R.id.name);

				convertView.setTag(_holder);
			} else
				_holder = (Cache) convertView.getTag();

			// Log.e("SMSFragment", "holder init");
			if (!_busy) {
				// if (_bitmapCache.containsKey(_holder.contactId) == true)
				// _holder.icon.setImageBitmap(_bitmapCache.get(_holder.contactId).get());
				// else

				// if (_holder.icon.getTag() != null) {
				Long contactId = _listIdContact.get(position);
				if (_bitmapCache.containsKey(contactId)) {
					_holder.icon.setImageBitmap(_bitmapCache.get(contactId)
							.get());
				} else {
					Uri uri = ContentUris.withAppendedId(
							ContactsContract.Contacts.CONTENT_URI, contactId);
					InputStream input = ContactsContract.Contacts
							.openContactPhotoInputStream(_contentResolver, uri);
					if (input != null) {
						Bitmap contactPhoto = BitmapFactory.decodeStream(input);
						_bitmapCache.put(contactId, new SoftReference<Bitmap>(
								contactPhoto));
						_holder.icon.setImageBitmap(contactPhoto);
					}
				}
				_holder.icon.setTag(null);
				// }

				// _holder.icon.setImageDrawable(mIcon2);
				_holder.icon.setTag(null);
			} else {
				Long contactId = _listIdContact.get(position);
				if (_bitmapCache.containsKey(contactId) == true)
					_holder.icon.setImageBitmap(_bitmapCache.get(contactId)
							.get());
				else
					_holder.icon.setImageDrawable(defaultContactIcon);
				_holder.icon.setTag(this);
			}
			// Log.e("SMSFragment", "_phoneNumberList.get(position) = " +
			// _phoneNumberList.get(position));
			// Log.e("SMSFragment", "_contactName.get(position) = " +
			// _contactName.get(position));
			_holder.text.setText(_phoneNumberList.get(position));
			_holder.name.setText(_contactName.get(position));

			return convertView;
		}

	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		_bitmapCache.clear();
		_bitmapCache = null;

	}

	static class Cache {
		private TextView text;
		private ImageView icon;
		private TextView name;
	}

	public static class SMSReceiver extends BroadcastReceiver {
		private final String ACTION_RECEIVE_SMS = "android.intent.action.DATA_SMS_RECEIVED";

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.e("Action", " = " + intent.getAction());
			if (!intent.getAction().equals(ACTION_RECEIVE_SMS))
				return;
			String uri = intent.getDataString();

			// intent.getByteArrayExtra(name)
			Log.e("Action", uri);
			if (!uri.contains(smsListingPort))
				return;

			Object[] pdus = (Object[]) intent.getExtras().get("pdus");
			SmsMessage[] msgs = new SmsMessage[pdus.length];
			byte[] data = null;

			String sender = "";
			String msgTxt = "";
			for (int i = 0; i < msgs.length; i++) {
				msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
				sender = msgs[i].getOriginatingAddress();
				data = msgs[i].getUserData();
				for (int index = 0; index < data.length; ++index)
					msgTxt += Character.toString((char) data[index]);
			}
			Toast.makeText(context, sender + " - " + msgTxt, Toast.LENGTH_SHORT)
					.show();
		}
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mViewer = inflater.inflate(R.layout.connect_sms, container, false);

		_phoneNumberList = new ArrayList<String>();
		_listIdContact = new ArrayList<Long>();
		_contactName = new ArrayList<String>();
		if (_bitmapCache == null)
			_bitmapCache = new HashMap<Long, SoftReference<Bitmap>>();

		_contentResolver = mViewer.getContext().getContentResolver();

		initViewElements();

		initListView();

		ContactClassAsyncTask contactAsync = new ContactClassAsyncTask();
		contactAsync.execute("");

		// final Button sendBtn = (Button)
		// mViewer.findViewById(R.id.sendMsgBtn);
		// sendBtn.setOnClickListener(new OnClickListener() {
		// @Override
		// public void onClick(View v) {
		// // final SparseBooleanArray checkedItems = listView
		// // .getCheckedItemPositions();
		// // final int checkedItemsCount = checkedItems.size();
		// //
		// // for (int i = 0; i < checkedItemsCount; ++i) {
		// // final int position = checkedItems.keyAt(i);
		// // final boolean isChecked = checkedItems.valueAt(i);
		// //
		// // if (isChecked == true) {
		// // addSelectedContact(position);
		// // }
		// // }
		// // sendSMS(message);
		// // sendSMS(message2);
		// // sendSMS(message3);
		// }
		// });

		return mViewer;
	}

	protected void addSelectedContact(int position) {
		String currentItem = arrayAdapter.getItem(position);
		int pos = currentItem.indexOf("+33");
		if (pos == -1)
			pos = currentItem.indexOf("0");
		currentItem = currentItem.substring(pos, currentItem.length()).replace(
				" ", "");
		selectedContact.add(currentItem);
	}

	public void initViewElements() {
		final EditText editText = (EditText) mViewer
				.findViewById(R.id.numberEditText);
		editText.addTextChangedListener(this);
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEND) {
					String receiver = editText.getText().toString();
					sendSMS(receiver, message);
					sendSMS(receiver, message2);
					sendSMS(receiver, message3);
					return true;
				}
				return false;
			}
		});

		cr = mViewer.getContext().getContentResolver();
		list = new ArrayList<String>();
		copyList = new ArrayList<String>();
		selectedContact = new ArrayList<String>();
	}

	public void initListView() {
		// if (false == isMultipleChoice)
		// arrayAdapter = new ArrayAdapter<String>(mViewer.getContext(),
		// android.R.layout.simple_list_item_single_choice, list);
		// else
		// arrayAdapter = new ArrayAdapter<String>(mViewer.getContext(),
		// android.R.layout.simple_list_item_multiple_choice, list);

		_efficientAdapter = new EfficientAdapter(mViewer.getContext());
		_contactList = (ListView) mViewer.findViewById(R.id.contactListView);
		_contactList.setOnScrollListener(this);

		// listView = (ListView) mViewer.findViewById(R.id.contactListView);
		_contactList.setFastScrollEnabled(true);
		_contactList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		_contactList.setAdapter(_efficientAdapter);
		_contactList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view,
					int position, long arg3) {
				Integer integer = (Integer) adapter.getItemAtPosition(position);
//				view.setBackgroundColor(Color.MAGENTA);
				Log.e("SMSFragment", "contact Name = " + _contactName.get(integer));
				Log.e("SMSFragment", "contact Phone Number = " + _phoneNumberList.get(integer));
				final String selectedPhoneNumber = _phoneNumberList.get(integer);
				AlertDialog.Builder builder = new AlertDialog.Builder(view.getContext());
				builder.setMessage("Voulez-vous envoyer une requête Remote Android à : " + _contactName.get(integer) 
						+ "sur le numéro : " + selectedPhoneNumber)
				       .setCancelable(false)
				       .setPositiveButton("Oui", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				        		sendSMS(selectedPhoneNumber, message);
								sendSMS(selectedPhoneNumber, message2);
								sendSMS(selectedPhoneNumber, message3);
				           }
				       })
				       .setNegativeButton("No", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				                dialog.cancel();
				           }
				       });
				AlertDialog alert = builder.create();
				alert.show();
			}
		});

	}

	protected void sendSMS(byte[] text) {
		// byte[] b = text.getBytes();
		Short sObj2 = 6800;
		for (String destination : selectedContact) {
			Log.e("Destination", " = " + destination);
			SmsManager.getDefault().sendDataMessage(destination, null, sObj2,
					text, null, null);
		}
	}

	protected void sendSMS(String receiver, byte[] text) {
		Short sObj2 = 6800;
		Log.e("Destination", " = " + receiver);
		SmsManager.getDefault().sendDataMessage(receiver, null, sObj2, text,
				null, null);
	}

	class ContactClassAsyncTask extends AsyncTask<String, Void, Void> {

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			// _contactList.setAdapter(null);
		}

		@Override
		protected Void doInBackground(String... filterStr) {
			final Cursor cur;
			if (filterStr[0].equals(""))
				cur = _contentResolver.query(
						ContactsContract.Contacts.CONTENT_URI, null, null,
						null, ContactsContract.Contacts.DISPLAY_NAME + " ASC");
			else {
				final String[] projection = { ContactsContract.Contacts._ID,
						ContactsContract.Contacts.DISPLAY_NAME,
						ContactsContract.Contacts.HAS_PHONE_NUMBER };
				cur = _contentResolver.query(
						ContactsContract.Contacts.CONTENT_URI, projection,
						ContactsContract.Contacts.DISPLAY_NAME + " LIKE ?",
						new String[] { filterStr[0] }, null);
			}

			_phoneNumberList.clear();
			_contactName.clear();
			_listIdContact.clear();
			if (cur == null)
				return null;
			while (cur.moveToNext()) {
				String contactid = cur.getString(cur
						.getColumnIndex(ContactsContract.Contacts._ID));
				String name = cur
						.getString(cur
								.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
				String hasphone = cur
						.getString(cur
								.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER));

				if (hasphone.equals("1"))
					hasphone = "true";

				if (Boolean.parseBoolean(hasphone)) {
					Cursor cphon = _contentResolver.query(
							ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
							null,
							ContactsContract.CommonDataKinds.Phone.CONTACT_ID
									+ "=" + contactid, null, null);
					if (cphon == null)
						continue;
					while (cphon.moveToNext()) {
						String phonenumber = cphon
								.getString(cphon
										.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
						_phoneNumberList.add(phonenumber);
						_contactName.add(name);
						_listIdContact.add(Long.parseLong(contactid));
					}
				}
			}

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			Log.e("SMSFragment", "1 - " + _phoneNumberList.size());
			Log.e("SMSFragment", "2 - " + _contactName.size());
			Log.e("SMSFragment", "3 - " + _listIdContact.size());
			_efficientAdapter.notifyDataSetChanged();
			// _contactList.setAdapter(_efficientAdapter);
		}

	}

	class ContactListSortAsyncTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			orderListByName();
			return null;
		}

	}

	@Override
	public void afterTextChanged(Editable s) {
		Log.e("SMSFragment", s.toString());
		ContactClassAsyncTask contactAsyncTask = new ContactClassAsyncTask();
		contactAsyncTask.execute("%" + s.toString() + "%");
	}

	public void orderListByName() {
		Collections.sort(copyList, String.CASE_INSENSITIVE_ORDER);
		list.clear();
		list.addAll(copyList);
		arrayAdapter.notifyDataSetChanged();
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {

	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		switch (scrollState) {
		case OnScrollListener.SCROLL_STATE_IDLE:
			_busy = false;

			int first = view.getFirstVisiblePosition();
			int count = view.getChildCount();

			for (int i = 0; i < count; i++) {

				_holder.icon = (ImageView) view.getChildAt(i).findViewById(
						R.id.icon);
				if (_holder.icon.getTag() != null) {
					Long contactId = _listIdContact.get(first + i);
					if (_bitmapCache.containsKey(contactId)) {
						_holder.icon.setImageBitmap(_bitmapCache.get(contactId)
								.get());
					} else {
						Uri uri = ContentUris.withAppendedId(
								ContactsContract.Contacts.CONTENT_URI,
								contactId);
						InputStream input = ContactsContract.Contacts
								.openContactPhotoInputStream(_contentResolver,
										uri);
						if (input != null) {
							Bitmap contactPhoto = BitmapFactory
									.decodeStream(input);
							_bitmapCache.put(contactId,
									new SoftReference<Bitmap>(contactPhoto));
							_holder.icon.setImageBitmap(contactPhoto);
						}
					}
					_holder.icon.setTag(null);
				}
			}

			break;
		case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
			_busy = true;
			break;
		case OnScrollListener.SCROLL_STATE_FLING:
			_busy = true;
			break;
		}
	}

}
