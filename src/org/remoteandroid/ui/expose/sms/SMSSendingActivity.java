package org.remoteandroid.ui.expose.sms;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.*;

import org.remoteandroid.AsyncTaskWithException;
import org.remoteandroid.R;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.connect.SMSFragment;
import org.remoteandroid.ui.connect.ConnectActivity.ConnectDialogFragment;
import org.remoteandroid.ui.connect.ConnectActivity.TryConnection;
import org.remoteandroid.ui.connect.qrcode.FinishListener;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
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
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.telephony.SmsManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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

public class SMSSendingActivity extends FragmentActivity implements TextWatcher, OnScrollListener
{
	static class Cache
	{
		private TextView text;

		private ImageView icon;

		private TextView name;
	}
	
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
	private FragmentManager mFragmentManager;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.expose_sms);

		mFragmentManager=getSupportFragmentManager();
		mListIdContact = new ArrayList<Long>();
		mContactName = new ArrayList<String>();
		if (sBitmapCache == null)
			sBitmapCache = new HashMap<Long, SoftReference<Bitmap>>();
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
						if (receiver.matches("[0123456789#*()\\- ]*"))
							sendData(receiver);
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
				final long id = (Long) adapter.getItemAtPosition(position);
				final PhoneDisambigDialog phoneDialog = new PhoneDisambigDialog(SMSSendingActivity.this, id);
				phoneDialog.show();
			}
		});
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		try
		{
			mSendedData= Trusted.getConnectMessage(SMSSendingActivity.this).toByteArray();
		}
		catch (UnknownHostException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (SocketException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (mContactAsync == null)
		{
			mContactAsync = new ContactClassAsyncTask();
			mContactAsync.execute("");
		}
	}

	public void sendData(final String receiver)
	{
		InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
		final SendSMSDialogFragment dlg=SendSMSDialogFragment.sendSMS(receiver);
		dlg.show(mFragmentManager, "dialog");

		
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
				holder.text = (TextView) convertView.findViewById(R.id.text);
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

	@Override
	public void onLowMemory()
	{
		super.onLowMemory();
		sBitmapCache.clear();

	}

	private static final String[] sProjection =
	{ 
		Contacts._ID,
		Contacts.DISPLAY_NAME 
	};

	private static final int POS_ID=0;
	private static final int POS_DISPLAY_NAME=1;
	
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
	protected void onPause()
	{
		super.onPause();
		sBitmapCache.clear();
	}
	
	
	public static class SendSMSDialogFragment extends DialogFragment
	{
		AsyncTaskWithException<Void, Integer, Void> mTask;
		public static SendSMSDialogFragment sendSMS(final String receiver)
		{
			return new SendSMSDialogFragment(receiver);
		}
 		
		SendSMSDialogFragment(final String receiver)
		{
			mTask=new AsyncTaskWithException<Void, Integer, Void>()
			{
				int mNbStep=0;
				
				//@Override
				protected Void doInBackground(Void... params) throws Exception
				{
					Messages.Candidates candidates = Trusted.getConnectMessage(getActivity());
					byte[] buf = candidates.toByteArray();
					long start=System.currentTimeMillis();
					int fragmentSize = SMSFragment.MESSAGE_SIZE;
					if (buf.length < fragmentSize)
						fragmentSize = buf.length + 1;
					byte[] fragment = new byte[fragmentSize];
					int fragNumber = 0;
					mNbStep=buf.length/(SMSFragment.MESSAGE_SIZE - 1)+1;
					if (V) Log.v(TAG_SMS,"Sending "+mNbStep+" sms...");
					int maxsize=(SMSFragment.MESSAGE_SIZE - 1);
					for (int i = 0; i < buf.length; i += maxsize)
					{
						publishProgress(i/maxsize);
						boolean last = (buf.length - i) < maxsize;
						int len = Math.min(buf.length - i, maxsize);
						System.arraycopy(buf, i, fragment, 1, len);
						byte[] pushFragment=fragment;
						if (last)
						{
							pushFragment=new byte[len+1];
							System.arraycopy(buf, i, pushFragment, 1, len);
						}
						pushFragment[0] = (byte) ((last ? 0x80 : 0) | fragNumber);
						SmsManager.getDefault().sendDataMessage(
							receiver, null, SMS_PORT, pushFragment, null, null);

						++fragNumber;
					}
					if (V) Log.v(TAG_SMS,"Sending "+mNbStep+" sms done.");
					long stop=System.currentTimeMillis();
					publishProgress(mNbStep);
					if (stop-start<1000) // 1s
					{
						try { Thread.sleep(1000-(stop-start)); } catch (Exception e) {}
					}
					return null;
				}
				@Override
				protected void onException(Throwable e)
				{
					if (E && !D) Log.d(TAG_SMS,PREFIX_LOG+"SMS error ("+e.getMessage()+")");
					if (D) Log.d(TAG_SMS,PREFIX_LOG+"SMS error ("+e.getMessage()+")",e);
					dismiss();
					new AlertDialog.Builder(getActivity())
						.setTitle(R.string.expose_sms_error_title)
						.setMessage(R.string.expose_sms_error_message)
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setPositiveButton(android.R.string.ok,null)
						.create()
						.show();
				}
				
				@Override
				protected void onProgressUpdate(Integer... values) 
				{
					if (mNbStep!=0)
					{
						ProgressDialog d=(ProgressDialog)getDialog();
						if (d!=null)
						{
							d.setProgress(values[0]*100/mNbStep);
						}
					}
				}
				
				@Override
				protected void onCancelled()
				{
					dismiss();
				}
				
				@Override
				protected void onPostExecute(Void result)
				{
					getActivity().finish();
				}
			};
		}

		@Override
		public void onCancel(DialogInterface dialog)
		{
			super.onCancel(dialog);
		}
		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState)
		{
			ProgressDialog progressDialog = new ProgressDialog(getActivity());
			progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	        progressDialog.setTitle(R.string.connect_try);
			progressDialog.setMessage(getResources().getText(R.string.expose_sms_send));
            progressDialog.setCancelable(true);
			mTask.execute();
			
            return progressDialog;
		}
		
	}	
	
}