package org.remoteandroid.ui.connect;

import org.remoteandroid.R;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class TechnologiesFragment extends ListFragment
{
	interface Listener
	{
		public void onTechnologieSelected(Technology technology);
	}

	static class Cache
	{
		TextView mContent;
		TextView mContextDescription;
	}
	class TechArrayAdapter extends BaseAdapter
	{
		LayoutInflater mInflater; 
		TechArrayAdapter(Context context)
		{
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}
		public View getView(int position, View convertView, android.view.ViewGroup parent) 
		{
			Cache cache;
			if (convertView==null)
			{
				convertView=mInflater.inflate(R.layout.connect_technology, parent,false);
				cache=new Cache();
				cache.mContent=(TextView)convertView.findViewById(R.id.connect);
				cache.mContextDescription=(TextView)convertView.findViewById(R.id.connect_description);
				convertView.setTag(cache);
			}
			else
				cache=(Cache)convertView.getTag();
			final Technology tech=mTechnologies[position+1];
			cache.mContent.setText(tech.mContent);
			cache.mContextDescription.setText(tech.mDescription);
			return convertView;
		}
		@Override
		public int getCount()
		{
			if (mTechnologies==null)
				return 0;
			return mTechnologies.length-1;
		}
		@Override
		public Object getItem(int position)
		{
			return mTechnologies[position+1];
		}
		@Override
		public long getItemId(int position)
		{
			return position+1;
		}
		
	};
	
	private Technology[] mTechnologies; 
	private Listener	mListener;
	private TechArrayAdapter mAdapter;

	void setTechnologies(Technology[] technologies)
	{
		mTechnologies=technologies;
		if (mAdapter!=null)
			mAdapter.notifyDataSetChanged();
		
	}
	@Override
	public void onAttach(Activity activity)
	{
		try
		{
			super.onAttach(activity);
			mListener = (Listener) activity;
			if (mAdapter==null)
			{
				mAdapter=new TechArrayAdapter(activity.getApplicationContext());
				
			}
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString()
					+ " must implement OnTechnologieListener");
		}
	}

	@Override
	public void onDetach()
	{
		super.onDetach();
		mListener = null;
	}

	public void enabledPersistentSelection()
	{
		getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return super.onCreateView(inflater, container, savedInstanceState);
		
	}
	@Override
	public void onListItemClick(ListView l, View v, int position, long id)
	{
		getListView().setItemChecked(position, true);
		if (mListener != null)
			mListener.onTechnologieSelected(mTechnologies[position+1]);
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setListAdapter(mAdapter);
	}
	
}
