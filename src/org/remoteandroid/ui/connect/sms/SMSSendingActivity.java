package org.remoteandroid.ui.connect.sms;

import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;

import org.remoteandroid.R;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.pairing.Trusted;

import android.app.Activity;
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
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

public class SMSSendingActivity extends Activity implements TextWatcher,
		OnScrollListener {

	private static Cache _holder;
	private static boolean _busy = false;
	private static final Short _sendingPort = 6800;

	private ContentResolver _contentResolver;

	private EfficientAdapter _efficientAdapter;
	private ListView _contactList;
	private ContactClassAsyncTask _contactAsync = null;

	private volatile ArrayList<String> _phoneNumberList;
	private volatile ArrayList<String> _contactName;
	private ArrayList<Long> _listIdContact;
	private ArrayList<String> _selectedContact;
	private HashMap<Long, SoftReference<Bitmap>> _bitmapCache = null;
	
	private static final int SMS_HEADER = 10;

	private byte[] _sendedData;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.connect_sms);

		_phoneNumberList = new ArrayList<String>();
		_listIdContact = new ArrayList<Long>();
		_contactName = new ArrayList<String>();
		if (_bitmapCache == null)
			_bitmapCache = new HashMap<Long, SoftReference<Bitmap>>();
		_contentResolver = getContentResolver();
		_selectedContact = new ArrayList<String>();

		initViewElements();

		initListView();
	}

	public void initViewElements() {
		final EditText editText = (EditText) findViewById(R.id.numberEditText);
		editText.addTextChangedListener(this);
		editText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEND) {
					final String receiver = editText.getText().toString();
					if (_sendedData != null || receiver != null) {
						// TODO : Checker le format de la saisie
						sendData(_sendedData, receiver);
					} else {
						// TODO : Gestion des erreurs
					}
					return true;
				}
				return false;
			}
		});
	}

	public void initListView() {
		_efficientAdapter = new EfficientAdapter(SMSSendingActivity.this);

		_contactList = (ListView) findViewById(R.id.contactListView);
		_contactList.setOnScrollListener(this);
		_contactList.setFastScrollEnabled(true);
		_contactList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		_contactList.setAdapter(_efficientAdapter);
		_contactList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapter, View view,
					int position, long arg3) {
				final int itemPosition = (Integer) adapter
						.getItemAtPosition(position);
				final String selectedPhoneNumber = _phoneNumberList
						.get(itemPosition);
				confirmSelection(itemPosition, selectedPhoneNumber,
						view.getContext());
			}
		});
	}

	private void confirmSelection(int itemPosition,
			final String selectedPhoneNumber, Context context) {
		// TODO : Internationalisation
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(
				"Voulez-vous envoyer une requête Remote Android à : "
						+ _contactName.get(itemPosition) + "sur le numéro : "
						+ selectedPhoneNumber)
				.setCancelable(false)
				.setPositiveButton("Oui",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								if (_sendedData != null) {
									sendData(_sendedData, selectedPhoneNumber);
								}
							}
						})
				.setNegativeButton("No", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		final AlertDialog alert = builder.create();
		alert.show();
	}

	@Override
	protected void onResume() {
		super.onResume();

		new AsyncTask<Void, Void, byte[]>() {
			@Override
			protected byte[] doInBackground(Void... params) {
				Messages.Candidates candidates;
				try {
					candidates = Trusted
							.getConnectMessage(SMSSendingActivity.this);
					return candidates.toByteArray();
				} catch (UnknownHostException e1) {
					// TODO Gérer cette erreur
					e1.printStackTrace();
				} catch (SocketException e1) {
					// TODO Gérer cette erreur
					e1.printStackTrace();
				}
				return null;
			}

			@Override
			protected void onPostExecute(byte[] data) {
				//TODO uncomment this line after testing
				//_sendedData = data;
				_sendedData = new byte[SmsMessage.MAX_USER_DATA_BYTES - SMS_HEADER];
				_sendedData[0] = -128;
				byte[] array = {-83, 1, -112, -64, 74, 10};
				int j = 0;
				for (int i = 1; i < _sendedData.length; ++i) {
					if (j == 6)
						j = 0;
					_sendedData[i] = array[j];
					++j;
				}
				Log.e("SMSSendingActivity", "length = " + data.length);
			}
		}.execute();

		if (_contactAsync == null) {
			Log.e("SMSSendingActivity", "contact Async is null");
			_contactAsync = new ContactClassAsyncTask();
			_contactAsync.execute("");
		}
	}

	public void sendData(byte[] buf, String receiver) {
		int fragmentSize =  SmsMessage.MAX_USER_DATA_BYTES - SMS_HEADER;
		Log.e("SMSending", "buf length = " + buf.length);
		Log.e("SMSending", "fragmentSize = " + fragmentSize);
		if (buf.length < fragmentSize)
			fragmentSize = buf.length + 1;
		byte[] fragment = new byte[fragmentSize];
		int fragNumber = 0;
		for (int i = 0; i < buf.length; i += (SmsMessage.MAX_USER_DATA_BYTES - 1 - SMS_HEADER)) {
			Log.e("SMSSendingActivity", "fragNumber = " + fragNumber);
			boolean last = (buf.length - i) < (SmsMessage.MAX_USER_DATA_BYTES - 1 - SMS_HEADER);
			Log.e("SMSSendingActivity", "last = " + last);
			int len = Math.min(buf.length - i, SmsMessage.MAX_USER_DATA_BYTES - 1 - SMS_HEADER);
			Log.e("SMSSendingActivity", "len = " + len);
			System.arraycopy(buf, i, fragment, 1, len);
			fragment[0] = (byte) ((last ? 0x80 : 0) | fragNumber);
			Log.e("SMSSendingActivity", "fragment[0] = " + fragment[0]);
			sendSMS(receiver, fragment);
			++fragNumber;
		}
	}

	protected void sendSMS(byte[] text) {
		for (String destination : _selectedContact) {
			sendSMS(destination, text);
		}
	}

	protected void sendSMS(String receiver, byte[] text) {
		Log.e("Destination", " = " + receiver);
		SmsManager.getDefault().sendDataMessage(receiver, null, _sendingPort,
				text, null, null);
	}

	public class EfficientAdapter extends BaseAdapter {

		private LayoutInflater mInflater;
		private Drawable defaultContactIcon;
		private Context mContext;

		public EfficientAdapter(Context context) {
			mContext = context;
			mInflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			defaultContactIcon = getResources().getDrawable(
					R.drawable.default_contact_icon);
		}

		public int getCount() {
			return _phoneNumberList.size();
		}

		public Object getItem(int position) {
			return position;
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
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

			if (!_busy) {
				final Long contactId = _listIdContact.get(position);
				if (_bitmapCache.containsKey(contactId)) {
					_holder.icon.setImageBitmap(_bitmapCache.get(contactId)
							.get());
				} else {
					final Uri uri = ContentUris.withAppendedId(
							ContactsContract.Contacts.CONTENT_URI, contactId);
					final InputStream input = ContactsContract.Contacts
							.openContactPhotoInputStream(_contentResolver, uri);
					if (input != null) {
						final Bitmap contactPhoto = BitmapFactory
								.decodeStream(input);
						_bitmapCache.put(contactId, new SoftReference<Bitmap>(
								contactPhoto));
						_holder.icon.setImageBitmap(contactPhoto);
					} else {
						_holder.icon.setImageDrawable(defaultContactIcon);
					}
				}
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

	class ContactClassAsyncTask extends AsyncTask<String, Void, Void> {

		private ArrayList<String> _tmpPhoneListNumber;
		private ArrayList<String> _tmpContactName;
		private ArrayList<Long> _tmpListIdContact;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			_tmpContactName = new ArrayList<String>();
			_tmpPhoneListNumber = new ArrayList<String>();
			_tmpListIdContact = new ArrayList<Long>();
		}

		@Override
		protected Void doInBackground(String... filterStr) {
			Cursor cur = null;
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
				Log.e("SMSSendingActivity", "filtering string = "
						+ filterStr[0]);
				Log.e("SMSSendingActivity", "cursor count = " + cur.getCount());
			}

			if (cur == null)
				return null;

			while (cur.moveToNext()) {
				final String contactId = cur.getString(cur
						.getColumnIndex(ContactsContract.Contacts._ID));
				final String name = cur
						.getString(cur
								.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
				final String hasphone = cur
						.getString(cur
								.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER));

				if (hasphone.equals("1")) {
					final Cursor cphon = _contentResolver.query(
							ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
							null,
							ContactsContract.CommonDataKinds.Phone.CONTACT_ID
									+ "=" + contactId, null, null);
					if (cphon == null)
						continue;
					while (cphon.moveToNext()) {
						final String phonenumber = cphon
								.getString(cphon
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
		protected void onPostExecute(Void result) {
			super.onPostExecute(result);
			_phoneNumberList.clear();
			_phoneNumberList.addAll(_tmpPhoneListNumber);

			_contactName.clear();
			_contactName.addAll(_tmpContactName);

			_listIdContact.clear();
			_listIdContact.addAll(_tmpListIdContact);

			_efficientAdapter.notifyDataSetChanged();
		}

	}

	@Override
	public void afterTextChanged(Editable s) {
		Log.e("SMSFragment", s.toString());
		if (_contactAsync != null)
			_contactAsync.cancel(true);
		_contactAsync = new ContactClassAsyncTask();
		_contactAsync.execute("%" + s.toString() + "%");
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
				} else {
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

	// TODO : Register receiver programmaticly in reception view
//	public static class SMSReceiver extends BroadcastReceiver {
//		private final String ACTION_RECEIVE_SMS = "android.intent.action.DATA_SMS_RECEIVED";
//
//		@Override
//		public void onReceive(Context context, Intent intent) {
//			Log.e("Action", " = " + intent.getAction());
//			if (!intent.getAction().equals(ACTION_RECEIVE_SMS))
//				return;
//			String uri = intent.getDataString();
//
//			Log.e("Action", uri);
//			if (!uri.contains(_sendingPort.toString()))
//				return;
//
//			Object[] pdus = (Object[]) intent.getExtras().get("pdus");
//			SmsMessage[] msgs = new SmsMessage[pdus.length];
//			byte[] data = null;
//
//			String sender = "";
//			String msgTxt = "";
//			for (int i = 0; i < msgs.length; i++) {
//				msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
//				sender = msgs[i].getOriginatingAddress();
//				data = msgs[i].getUserData();
//				for (int index = 0; index < data.length; ++index)
//					msgTxt += Character.toString((char) data[index]);
//			}
//
//			Toast.makeText(context, sender + " - " + msgTxt, Toast.LENGTH_SHORT)
//					.show();
//		}
//	}

}


