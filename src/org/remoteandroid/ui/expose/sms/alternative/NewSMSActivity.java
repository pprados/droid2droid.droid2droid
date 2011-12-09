package org.remoteandroid.ui.expose.sms.alternative;


import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.internal.Constants.W;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.remoteandroid.LogMarket;
import org.remoteandroid.R;

import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.provider.Contacts.Intents.UI;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AlphabetIndexer;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

// FIXME BUG: si basculement, perte du bouton "téléphone" 
// TODO: add keyboard shortcut
public final class NewSMSActivity extends ListActivity
{
	private static final String TAG = "SMS"; // FIXME
	public static final String QUERY_MODE_ALL="*=?";

	private Handler _handler=new Handler();


	private int _scrollState;

	private ContactsAdapter _adapter;

	private QueryHandler _queryHandler;
	
	/** Query for current list */
	private String _lastQuery;

	private TextView _errorsText;
	
	private TextView _emptyText;

	private TextView _totalContacts;

	// static arrays for optimize the code
	private static final String[] colDataId = new String[]
	{ BaseColumns._ID };

	private static final String[] colsNormal = new String[]
	{ 
		BaseColumns._ID, Contacts.DISPLAY_NAME, 
		RawContacts.ACCOUNT_TYPE,RawContacts.ACCOUNT_NAME, 
		Phone.TYPE, Phone.NUMBER 
	};

	private static final String[] colsPickContact = new String[]
	{ 
		BaseColumns._ID, Contacts.DISPLAY_NAME,
		RawContacts.ACCOUNT_TYPE,RawContacts.ACCOUNT_NAME, 
	};
	private static final String[] colsPickPhone = new String[]
	{ 
		BaseColumns._ID, Contacts.DISPLAY_NAME, 
		RawContacts.ACCOUNT_TYPE,RawContacts.ACCOUNT_NAME, 
		Phone.RAW_CONTACT_ID, Phone.TYPE, Phone.LABEL, Phone.NUMBER };

	public NewSMSActivity()
	{
	}

	/**
	 * Use with RetainNonConfigurationInstance
	 * @version 1.0
	 * @since 1.0
	 * @author Philippe PRADOS
	 */
	static final class Retain
	{
		public QueryHandler _queryhandler;
		public Cursor _cursor;
		public boolean _onSearchRequest;
	}
	
	@Override
	@SuppressWarnings("deprecation")
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.test_contacts_list_content);
		setProgressBarIndeterminateVisibility(Boolean.FALSE); // Important: Use Boolean value !

		_errorsText=(TextView) findViewById(R.id.errorsText);
		_emptyText = (TextView) findViewById(R.id.emptyText);
		_emptyText.setMovementMethod(LinkMovementMethod.getInstance());
		
		final ListView list = getListView();
		list.setFocusable(true);
		list.setDividerHeight(1);
		list.setOnCreateContextMenuListener(this);

		// We manually save/restore the listview state
		list.setSaveEnabled(false);

		_queryHandler = new QueryHandler(this);
		_adapter = new ContactsAdapter();
		list.setOnItemClickListener(_adapter);

		setListAdapter(_adapter);

		final Intent intent = getIntent();
		String title = intent.getStringExtra(UI.TITLE_EXTRA_KEY);
		if (title != null)
			setTitle(title);

		// Auto open search, but not for main action.
		final String action = intent.getAction();
		if (!Intent.ACTION_MAIN.equals(action) && !Intent.ACTION_SEARCH.equals(action))
		{
			super.onSearchRequested();
		}
	}

	private boolean restoreRetainNonConfigurationInstance()
	{
		if (D) Log.d("LIFE", "...getLastNonConfigurationInstance");
		final Retain retain=(Retain)getLastNonConfigurationInstance();
		if (retain!=null)
		{
			_queryHandler=retain._queryhandler;
			if (_queryHandler._pending)
				setProgressBarIndeterminateVisibility(false);
			_queryHandler._activity=new WeakReference<NewSMSActivity>(this);
			if (retain._cursor!=null)
			{
				_adapter.changeCursor(retain._cursor);
			}
			getListView().requestFocus();
			return true;
		}
		return false;
	}

	@Override
	protected void onRestart()
	{
		super.onRestart();
		if (V) Log.v("LIFE", "onRestart");
//		ProvidersManager.wakeup();

		// The cursor was killed off in onStop(), so we need to get a new one here
		// We do not perform the query if a filter is set on the list because the
		// filter will cause the query to happen anyway
		// if (TextUtils.isEmpty(getListView().getTextFilter()))
		if ((_lastQuery != null) && (_adapter._cursor == null))
		{
			if (D) Log.d(TAG,"Restart previous request "+_lastQuery);
			startQuery();
		}

	}

//	@Override
//	protected void onStart()
//	{
//		super.onStart();
//		if (V) Log.v("LIFE", "onStart");
//	}

	@Override
	protected void onRestoreInstanceState(final Bundle state)
	{
		super.onRestoreInstanceState(state);
		if (V) Log.v("LIFE", "onRestoreInstanceState");
		if (!restoreRetainNonConfigurationInstance())
		{
			_restoreListState = state.getParcelable(STATE_LIST_STATE);
			_lastQuery = state.getString(STATE_LAST_REQUEST);
			if (!_queryHandler._pending && (_lastQuery != null) && (_adapter._cursor == null))
			{
				if (D) Log.d(TAG,"start query for on restore");
				startQuery();
			}
			if (_restoreListHasFocus = state.getBoolean(STATE_FOCUS))
			{
				getListView().requestFocus();
			}
		}
		_adapter.setTotalContactCountView();
		_queryHandler.showError(this);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		if (V) Log.v("LIFE", "onResume");
		_scrollState = OnScrollListener.SCROLL_STATE_IDLE;
		
	}

	 @Override
	 protected void onSaveInstanceState(final Bundle state)
	 {
		 super.onSaveInstanceState(state);
		 if (V) Log.v("LIFE", "onSaveInstanceState");
		 state.putParcelable(STATE_LIST_STATE,
		 getListView().onSaveInstanceState());
		 state.putBoolean(STATE_FOCUS, getListView().hasFocus());
		 state.putString(STATE_LAST_REQUEST, _lastQuery);
		 getListView().getCheckedItemPosition();
	 }

	@Override
	protected void onStop()
	{
		super.onStop();
		if (V) Log.v("LIFE", "onStop");
		
		// We don't want the list to display the empty state, since when we
		// resume it will still
		// be there and show up while the new query is happening. After the
		// async query finished
		// in response to onRestart() setLoading(false) will be called.
		if (_adapter!=null)
		{
			_adapter.setLoading(true);
			_adapter.clearImageFetching();
		}

		// Make sure the search box is closed
		SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
		searchManager.stopSearch();
	}

	@Override
	public Object onRetainNonConfigurationInstance()
	{
		if (V) Log.v("LIFE", "onRetainNonConfigurationInstance " + _adapter._cursor);
		Retain retain=new Retain();
		retain._queryhandler=_queryHandler;
		// Keep cursor when configuration change
		retain._cursor=_adapter._cursor;
		return retain;
	}

	@Override
	protected void onNewIntent(final Intent intent)
	{
		if (V) Log.v("LIFE", "onNewIntent()");
		setIntent(intent);
		if (Intent.ACTION_SEARCH.equals(intent.getAction()))
		{
			final String query = intent.getStringExtra(SearchManager.QUERY);
			_lastQuery = query;
			if (D) Log.d(TAG,"Start query for new intent");
			startQuery();
		}
		else
			LogMarket.wtf(TAG, "onNewIntent without query");
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		if (V) Log.v("LIFE", "onDestroy");
		if (_queryHandler!=null)
		{
			_queryHandler.stop();
		}
	}

	@Override
	public void onBackPressed()
	{
		super.onBackPressed();
	}
	
	@Override
	public void onLowMemory()
	{
		//TODO ProvidersManager.Cache.onLowMemory();
	}

	// ------------- Manage query
	private static final int DIALOG_SEARCH=1;
	@Override
	public Dialog onCreateDialog(int id)
	{
		_queryHandler._progressDialog=ProgressDialog.show(
			this, getString(R.string.search_title), getString(R.string.search_wait), true,true,
			new DialogInterface.OnCancelListener()
			{
				@Override
				public void onCancel(DialogInterface dialog)
				{
					_queryHandler.cancel();
					_emptyText.setText(R.string.help_first_time);
					setProgressBarIndeterminateVisibility(false);
					removeDialog(DIALOG_SEARCH);
				}
			});
		return _queryHandler._progressDialog;
	}
	public final static class QueryHandler implements ProvidersManager.OnQuery
	{
		private ProgressDialog _progressDialog;
		private WeakReference<NewSMSActivity> _activity;
		private volatile boolean _pending;
		private StringBuilder _error=new StringBuilder();
		private boolean _warning;
		
		private QueryHandler(final NewSMSActivity context)
		{
			_activity = new WeakReference<NewSMSActivity>(context);
		}
		public void startQuery(final String[] selectionArgs, boolean cont)
		{
			cancel();
			_pending=true;
			final NewSMSActivity activity = _activity.get();
			String[] projection=colsPickPhone;
			ProvidersManager.query(this, projection, QUERY_MODE_ALL, selectionArgs);
		}
		public void cancel()
		{
			_pending=false;
			_warning=false;
			final NewSMSActivity activity = _activity.get();
			clearError(activity);
			activity.setProgressBarIndeterminateVisibility(false);
			ProvidersManager.cancelQuery();
		}
		public void stop()
		{
			cancel();
		}

		private void showError(NewSMSActivity activity)
		{
			if (_error.length()!=0)
			{
				activity._errorsText.setText(_error);
				activity._errorsText.setVisibility(View.VISIBLE);
			}
			else
				activity._errorsText.setVisibility(View.GONE);
				
		}
		private void showWarning(NewSMSActivity activity,CharSequence msg)
		{
			Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
		}
		private void clearError(NewSMSActivity activity)
		{
			_error.setLength(0);
			activity._errorsText.setText("");
			activity._errorsText.setVisibility(View.GONE);
		}

		@Override
		public synchronized void onQueryComplete(ResultsAndExceptions result,boolean finish)
		{
//			final NewSMSActivity activity = _activity.get();
//			if (activity==null || activity.isFinishing()) 
//				return;
//			final Cursor cursor=result.cursor;
//			// Manage all errors
//			//for (Exception exception:result.exceptions)
//			StringBuilder error=new StringBuilder();
//			synchronized (result)
//			{
//				for (QueryException exception:result.exceptions)
//				{
//					if (exception instanceof QueryWarning)
//					{
//						_warning=true;
//					}
//					else if (exception instanceof QueryError)
//					{
//						QueryError w=(QueryError)exception;
//						String accountName=w.getAccountName();
//						if (accountName!=null)
//							error.append(accountName).append(':');
//						error.append(w.getMessage())
//							.append('\n');
//					}
//					else
//					{
//						String msg=exception.getLocalizedMessage();
//						if (msg==null) msg=exception.getMessage();
//						if (msg==null) msg=exception.toString();
//						error.append(msg).append('\n');
//					}
//				}
//			}
//			if (finish)
//			{
//				_pending=false;
//				activity.setProgressBarIndeterminateVisibility(false);
//				if (cursor==null || cursor.getCount()==0)
//				{
//					activity._emptyText.setText(R.string.noMatchingContacts);
//				}
//				if (_warning)
//				{
//					showWarning(activity,activity.getString(R.string.err_truncated));
//				}
//				if (error.length()!=0) 
//				{
//					error.trimToSize();
//					_error=error;
//					showError(activity);
//				}
//			}
//			else
//				if (_pending) 
//					activity.setProgressBarIndeterminateVisibility(true); // Signal it's continue...
//			
//			
//			if (cursor!=null && activity != null && !activity.isFinishing())
//			{
//				activity._adapter.setLoading(false);
//				activity.getListView().clearTextFilter(); // TODO : No keyboard filter at this time
//				activity._adapter.changeCursor(cursor);
//				// Now that the cursor is populated again, it's possible to
//				// restore the list state
//				if (activity._restoreListState != null)
//				{
//					activity.getListView().onRestoreInstanceState(activity._restoreListState);
//					if (activity._restoreListHasFocus)
//					{
//						activity.getListView().requestFocus();
//					}
//					activity._restoreListHasFocus = false;
//					activity._restoreListState = null;
//				}
//				if ((cursor.getCount()!=0) || finish)
//				{
//					if (_progressDialog != null)
//					{
//						activity.removeDialog(DIALOG_SEARCH);
//						_progressDialog = null;
//					}
//				}
//				activity._adapter.setTotalContactCountView();
//			}
//			else
//			{
//				if (_progressDialog != null)
//				{
//					activity.removeDialog(DIALOG_SEARCH);
//					_progressDialog = null;
//				}
//			}
//			
//			if (error.length()==0)
//				QueryMarket.checkRate(activity);
		}
	}

	// ------------------ Save / Restore state
	private static final String STATE_LIST_STATE = "liststate";

	private static final String STATE_FOCUS = "focused";

	private static final String STATE_LAST_REQUEST = "request";

	private Parcelable _restoreListState = null;

	private boolean _restoreListHasFocus;

	// ------------------- Manage list view
	/** View index cached in tag. */
	static private final class Cache
	{
		private View _header;

		private TextView _headerText;

		private ImageView _callbutton;

		private NewSMSExQuickContactBadge _photoView;

		private ImageView _nonQuickContactPhotoView;

		private TextView _label;

		private TextView _data;

		private TextView _name;

	}

	final class ContactsAdapter extends CursorAdapter implements SectionIndexer, ListAdapter, OnItemClickListener, OnClickListener
	{
		private SectionIndexer _indexer;

		private int[] _sectionPositions;

		static final int SUMMARY_NAME_COLUMN_INDEX = 1;

		private final String _alphabet;

		private boolean _loading;

		private Cursor _cursor;

		private static final int FETCH_IMAGE_MSG = 1;

		public ContactsAdapter()
		{
			super(NewSMSActivity.this, null, false); // no autoRequery

			_alphabet = getString(R.string.fast_scroll_alphabet);
			_handler = new ImageFetchHandler();
			_bitmapCache = new HashMap<Long, SoftReference<Bitmap>>();
			_itemsMissingImages = new HashSet<ImageView>();
		}

		public void setLoading(final boolean loading)
		{
			_loading = loading;
		}

		/** {@inheritDoc} */
		@Override
		public boolean isEmpty()
		{
			return (_loading) ? false : super.isEmpty();
		}

		/** {@inheritDoc} */
		@Override
		public void changeCursor(final Cursor cursor)
		{
			super.changeCursor(_cursor = cursor);
			// Update the indexer for the fast scroll widget
			updateIndexer(cursor);
		}

		/** {@inheritDoc} */
		@Override
		public void bindView(final View view, final Context context, final Cursor cursor)
		{
			if (_cursor == null)
				return;
			final Cache cache = (Cache) view.getTag();
			final int position = _cursor.getPosition();
			if (!cursor.moveToPosition(position))
				throw new IllegalStateException("couldn't move cursor to position " + position);
			bindSectionHeader(view, position);
			bindPhoto(cursor, cache);
			bindData(cursor, cache);
		}

		private void bindData(final Cursor cursor, final Cache cache)
		{
			cache._name.setText(cursor.getString(1 /* Contacts.DISPLAY_NAME */));
			// index
			int type;
			cache._data.setText(cursor.getString(3/*RawContacts.ACCOUNT_NAME*/));
			cache._photoView.setItemId(cursor.getLong(0 /* BaseColumns._ID */));

			bindVisibility(cursor, cache);
		}

		private void bindVisibility(final Cursor cursor, final Cache cache)
		{
			int visibility;
			visibility = View.VISIBLE;
			cache._label.setVisibility(visibility);
			cache._data.setVisibility(visibility);
			boolean withPhone = false;
			int col = cursor.getColumnIndex(Phone.NUMBER);
			if (col != -1)
				withPhone = !cursor.isNull(col);
		}

		/** {@inheritDoc} */
		@Override
		public View newView(final Context context, final Cursor cursor, final ViewGroup parent)
		{
			View rc;
			Cache cache;
			final LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			rc = inflater.inflate(R.layout.test_contacts_list_item_photo, parent, false);
			cache = new Cache();
			rc.setTag(cache);
			cache._header = rc.findViewById(R.id.header);
			cache._headerText = (TextView) rc.findViewById(R.id.header_text);
			cache._photoView = (NewSMSExQuickContactBadge) rc.findViewById(R.id.photo);
			cache._photoView.setId(R.id.photo);

			cache._nonQuickContactPhotoView = (ImageView) rc.findViewById(R.id.noQuickContactPhoto);
			cache._label = (TextView) rc.findViewById(R.id.label);
			cache._data = (TextView) rc.findViewById(R.id.data);
			cache._name = (TextView) rc.findViewById(R.id.name);

			return rc;
		}

		// ----------------- Manage index
		private void bindSectionHeader(final View view, final int position)
		{
			final Cache cache = (Cache) view.getTag();
			final int section = getSectionForPosition(position);
			if (getPositionForSection(section) == position)
			{
				final String title = _indexer.getSections()[section].toString().trim();
				if (!TextUtils.isEmpty(title))
				{
					cache._headerText.setText(title);
					cache._header.setVisibility(View.VISIBLE);
				}
				else
				{
					cache._header.setVisibility(View.GONE);
				}
			}
			else
			{
				cache._header.setVisibility(View.GONE);
			}
		}

		private void updateIndexer(final Cursor cursor)
		{
			if (_indexer == null)
			{
				_indexer = getNewIndexer(cursor);
			}
			else
			{
				if (Locale.getDefault().equals(
					Locale.JAPAN))
				{
					// if (mIndexer instanceof JapaneseContactListIndexer)
					// ((JapaneseContactListIndexer)
					// mIndexer).setCursor(cursor);
					// else
					_indexer = getNewIndexer(cursor);
				}
				else
				{
					if (_indexer instanceof AlphabetIndexer)
					{
						((AlphabetIndexer) _indexer).setCursor(cursor);
					}
					else
					{
						_indexer = getNewIndexer(cursor);
					}
				}
			}

			final int sectionCount = _indexer.getSections().length;
			if (_sectionPositions == null || _sectionPositions.length != sectionCount)
			{
				_sectionPositions = new int[sectionCount];
			}
			for (int i = 0; i < sectionCount; i++)
			{
				_sectionPositions[i] = AdapterView.INVALID_POSITION;
			}
		}

		@Override
		public Object[] getSections()
		{
			return _indexer.getSections();
		}

		@Override
		public int getPositionForSection(final int sectionIndex)
		{

			if (sectionIndex < 0 || sectionIndex >= _sectionPositions.length)
			{
				return -1;
			}

			if (_indexer == null)
			{
				final Cursor cursor = getCursor();
				if (cursor == null)
				{
					// No cursor, the section doesn't exist so just return 0
					return 0;
				}
				_indexer = getNewIndexer(cursor);
			}

			int position = _sectionPositions[sectionIndex];
			if (position == AdapterView.INVALID_POSITION)
			{
				position = _sectionPositions[sectionIndex] = _indexer.getPositionForSection(sectionIndex);
			}

			return position;
		}

		@Override
		public int getSectionForPosition(final int position)
		{
			// The current implementations of SectionIndexers (specifically the Japanese indexer)
			// only work in one direction: given a section they can calculate the position.
			// Here we are using that existing functionality to do the reverse mapping. We are
			// performing binary search in the mSectionPositions array, which itself is populated
			// lazily using the "forward" mapping supported by the indexer.

			int start = 0;
			int end = _sectionPositions.length;
			while (start != end)
			{

				// We are making the binary search slightly asymmetrical,
				// because the
				// user is more likely to be scrolling the list from the top
				// down.
				final int pivot = start + (end - start) / 4;

				final int value = getPositionForSection(pivot);
				if (value <= position)
				{
					start = pivot + 1;
				}
				else
				{
					end = pivot;
				}
			}

			// The variable "start" cannot be 0, as long as the indexer is
			// implemented properly
			// and actually maps position = 0 to section = 0
			return start - 1;
		}

		private SectionIndexer getNewIndexer(final Cursor cursor)
		{
			/*
			 * if
			 * (Locale.getDefault().getLanguage().equals(Locale.JAPAN.getLanguage
			 * ())) { return new JapaneseContactListIndexer(cursor,
			 * SORT_STRING_INDEX); } else {
			 */
			return new AlphabetIndexer(cursor, SUMMARY_NAME_COLUMN_INDEX, _alphabet);
			/* } */
		}

		// ----------------- Manage header line ----------------------------
		private void setTotalContactCountView()
		{
			if (_totalContacts == null)
				return;

			String text = null;
			final int count = getCount();

			text = getQuantityText(count, R.string.listFoundAllContactsZero, R.plurals.listFoundAllContacts);
			assert (text != null);
			_totalContacts.setText(text);
		}

		private String getQuantityText(final int count, final int zeroResourceId, final int pluralResourceId)
		{
			if (count == 0)
				return getString(zeroResourceId);
			else
			{
				final String format = getResources().getQuantityText(
					pluralResourceId, count).toString();
				return String.format(format, count);
			}
		}

		// ---------------------------- Manage user interactions
		/** {@inheritDoc} */
		@Override
		public void onItemClick(final AdapterView<?> adapter, final View view, final int position, final long id)
		{
//			final Intent intent = new Intent();
//			final Uri uri;
//			Cursor cursor = getCursor();
//			cursor.moveToPosition(position);
//			if (D && cursor.getColumnIndex(Data.RAW_CONTACT_ID) != 5)
//				LogMarket.wtf(TAG, "col position error");
//			final long rawid = cursor.getLong(5 /* Data.RAW_CONTACT_ID */);
//			final String filter =Phone.CONTENT_ITEM_TYPE;
//			try
//			{
//				final String value = ((Cache) view.getTag())._data.getText().toString();
//				cursor = getContentResolver().query(
//					Uri.withAppendedPath(
//						uri, Contacts.Data.CONTENT_DIRECTORY), colDataId, 
//						Data.MIMETYPE + "=? and " + Data.DATA1 + "=?", 
//						new String[] { filter, value }, null);
//				if (cursor.moveToFirst())
//				{
//					final long iddata = cursor.getLong(0 /* Data._ID */);
//					Uri result = null;
//					result = ContentUris.withAppendedId(Data.CONTENT_URI, iddata);
//					setResult(RESULT_OK, intent.setData(result));
//				}
//				else
//				{
//					setResult(RESULT_CANCELED);
//				}
//				finish();
//			}
//			finally
//			{
//				if (cursor != null)
//				{
//					cursor.close();
//				}
//			}
		}

		@Override
		public void onClick(final View v)
		{
			final long position = ((Long) v.getTag()).longValue();
			// TODO
		}

		// ----------------- Manage photos ---------------------------------
		private HashMap<Long, SoftReference<Bitmap>> _bitmapCache; // Cache des photos, via account:lookup
		private HashSet<ImageView> _itemsMissingImages; // List de items sans photo (pour le moment)
		private ImageDbFetcher _imageFetcher;

		private ImageFetchHandler _handler;
		private class ImageFetchHandler extends Handler
		{
			@Override
			public void handleMessage(final Message message)
			{
				if (NewSMSActivity.this.isFinishing())
				{
					return;
				}
				switch (message.what)
				{
					case FETCH_IMAGE_MSG:
					{
						final ImageView imageView = (ImageView) message.obj;
						if (imageView == null)
							break;

						final PhotoCache info = (PhotoCache) imageView.getTag();
						if (info == null)
							break;

						final long photoId = info._photoId;
						if (photoId == -1)
							break;

						final SoftReference<Bitmap> photoRef = _bitmapCache.get(photoId);
						if (photoRef == null)
						{
							break;
						}
						final Bitmap photo = photoRef.get();
						if (photo == null)
						{
							_bitmapCache.remove(photoId);
							break;
						}

						// Make sure the photoId on this image view has not
						// changed
						// while we were loading the image.
						synchronized (imageView)
						{
							final PhotoCache updatedInfo = (PhotoCache) imageView.getTag();
							final long currentPhotoId = updatedInfo._photoId;
							if (currentPhotoId == photoId)
							{
								imageView.setImageBitmap(photo);
								_itemsMissingImages.remove(imageView);
							}
						}
						break;
					}
					default:
						LogMarket.wtf(TAG, "Unknown message");
				}
			}

			public void clearImageFecthing()
			{
				removeMessages(FETCH_IMAGE_MSG);
			}
		}

		private class ImageDbFetcher implements Runnable
		{
			private final long _photoId;

			private final ImageView _imageView;

			public ImageDbFetcher(final long photoId, final ImageView imageView)
			{
				_photoId = photoId;
				_imageView = imageView;
			}

			public void run()
			{
				if (NewSMSActivity.this.isFinishing())
				{
					return;
				}

				if (Thread.interrupted())
				{
					return;
				}
				Bitmap photo = null;
				try
				{
					photo = ProvidersManager.getPhoto(_photoId);
				}
				catch (final OutOfMemoryError e)
				{
					// Not enough memory for the photo, do nothing.
					if (W) Log.w(TAG,"ImageFetcher",e);
				}
				if (photo == null)
				{
					return;
				}

				_bitmapCache.put(_photoId, new SoftReference<Bitmap>(photo));

				if (Thread.interrupted())
				{
					return;
				}

				// Update must happen on UI thread
				final Message msg = new Message();
				msg.what = FETCH_IMAGE_MSG;
				msg.obj = _imageView;
				_handler.sendMessage(msg);
			}
		}
		
		private void bindPhoto(final Cursor cursor, final Cache cache)
		{
//			final int SUMMARY_LOOKUP_KEY = 0;
//			// Set the photo, if requested
//			final ContactId photoId=new ContactId(
//				cursor.getString(2/*RawContacts.ACCOUNT_TYPE*/), 
//				cursor.getString(3/*RawContacts.ACCOUNT_NAME*/), 
//				cursor.getString(4/*VolatileRawContact.LOOKUP*/));
//
//			final boolean useQuickContact = true; // FIXME
//			ImageView viewToUse;
//			if (useQuickContact)
//			{
//				viewToUse = cache._photoView;
//				// Build soft lookup reference
//				final long contactId = cursor.getLong(0 /* BaseColumns._ID */);
//				final String lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY);
//				cache._photoView.assignContactUri(Contacts.getLookupUri(contactId, lookupKey));
//				cache._photoView.setVisibility(View.VISIBLE);
//				cache._nonQuickContactPhotoView.setVisibility(View.INVISIBLE);
//			}
//			else
//			{
//				viewToUse = cache._nonQuickContactPhotoView;
//				cache._photoView.setVisibility(View.INVISIBLE);
//				cache._nonQuickContactPhotoView.setVisibility(View.VISIBLE);
//			}
//
//			final int position = cursor.getPosition();
//			viewToUse.setTag(new PhotoCache(position, photoId));
//
//			Bitmap photo = null;
//
//			// Look for the cached bitmap
//			final SoftReference<Bitmap> ref = _bitmapCache.get(photoId);
//			if (ref != null)
//			{
//				photo = ref.get();
//				if (photo == null) // Lose weak reference
//					_bitmapCache.remove(photoId);
//			}
//
//			// Bind the photo, or use the fallback no photo resource
//			if (photo != null)
//			{
//				viewToUse.setImageBitmap(photo);
//			}
//			else
//			{
//				// Cache miss
//				viewToUse.setImageResource(R.drawable.ic_contact_list_picture);
//
//				// Add it to a set of images that are populated asynchronously.
//				_itemsMissingImages.add(viewToUse);
//
//				if (_scrollState != OnScrollListener.SCROLL_STATE_FLING)
//				{
//					// Scrolling is idle or slow, go get the image right now.
//					sendFetchImageMessage(viewToUse);
//				}
//			}
		}

		public void onScrollStateChanged(final AbsListView view, final int scrollState)
		{
			_scrollState = scrollState;
			if (scrollState == OnScrollListener.SCROLL_STATE_FLING)
			{
				clearImageFetching(); // If we are in a fling, stop loading images.
			}
			else
			{
				processMissingImageItems(view);
			}
		}

		private void processMissingImageItems(final AbsListView view)
		{
			for (final ImageView iv : _itemsMissingImages)
			{
				sendFetchImageMessage(iv);
			}
		}
		// Start background load image
		private void sendFetchImageMessage(final ImageView view)
		{
			final PhotoCache info = (PhotoCache) view.getTag();
			if (info == null)
			{
				return;
			}
			final long photoId = info._photoId;
			if (photoId == -1)
				return;
			
			_imageFetcher = new ImageDbFetcher(photoId, view);
			synchronized (NewSMSActivity.this)
			{
				// can't sync on sImageFetchThreadPool.
				if (_imageFetchThreadPool == null)
				{
					// Don't use more than 3 threads at a time to update. The thread pool will be
					// shared by all contact items.
					_imageFetchThreadPool = Executors.newFixedThreadPool(3);
				}
				_imageFetchThreadPool.execute(_imageFetcher);
			}
		}

		/**
		 * Stop the image fetching for ALL contacts, if one is in progress we'll
		 * not query the database.
		 * 
		 */
		public void clearImageFetching()
		{
			synchronized (NewSMSActivity.this)
			{
				if (_imageFetchThreadPool != null)
				{
					_imageFetchThreadPool.shutdownNow();
					_imageFetchThreadPool = null;
				}
			}

			if (_handler != null)
			{
				_handler.clearImageFecthing();
			}
		}

	}
	/** Executor for photos. */
	private static ExecutorService _imageFetchThreadPool;

	// In photo tag
	final static class PhotoCache
	{
		public int _position;
		public long _photoId;

		public PhotoCache(final int position, long id)
		{
			_position = position;
			_photoId=id;
		}
	}

	/** {@inheritDoc} */
	String _authToken;

	// -------------------- Commands
	private void startQuery()
	{
		if (D) Log.d(TAG, "startQuery(\""+_lastQuery+"\")");
		getListView().requestFocus();
		getListView().setSelection(0);
		// Hide soft keyboard, if visible
		((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(
			getListView().getWindowToken(), 0);

		// Hide search UI
		((SearchManager) getSystemService(Context.SEARCH_SERVICE)).stopSearch();

		_emptyText.setText(R.string.currentQuery);
		if (_lastQuery.length() != 0)
		{
			// Delay for clean windows before
			_queryHandler._pending=true; // Because onResume() is call just after onRestoreInstance and the message must be empty
			_handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					_queryHandler.startQuery(new String[] { _lastQuery }, false);
				}
			});
		}
	}
	
	static class ResultsAndExceptions
	{
		
	}
	static class ProvidersManager
	{
		public static interface OnQuery
		{
			void onQueryComplete(ResultsAndExceptions result,boolean finish);
		}
		
		public static void query(
				final OnQuery callback,
				final String[] projection,
				final String selection, 
				final String... selectionArgs)
			{
			
			}
		public static final void cancelQuery()
		{
			
		}
		public static Bitmap getPhoto(long id)
		{
			return null;
		}
	}
}

