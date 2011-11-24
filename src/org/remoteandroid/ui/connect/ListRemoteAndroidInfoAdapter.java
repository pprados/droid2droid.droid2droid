package org.remoteandroid.ui.connect;

import java.util.ArrayList;
import java.util.List;

import org.remoteandroid.Application;
import org.remoteandroid.ListRemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.ListRemoteAndroidInfo.DiscoverListener;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.*;

public class ListRemoteAndroidInfoAdapter extends BaseAdapter implements DiscoverListener
{
	public interface Filter
	{
		public boolean filter(RemoteAndroidInfo info);
	}
	private ListRemoteAndroidInfo mListInfo;
	private List<RemoteAndroidInfo> mListFilter;
	private Context mContext;
	private Filter mFilter;
	private int mResId;
	private DiscoverListener mListener;
	
	public ListRemoteAndroidInfoAdapter(Context context,ListRemoteAndroidInfo listInfo)
	{
		this(context,listInfo,android.R.layout.two_line_list_item);
	}
	public ListRemoteAndroidInfoAdapter(Context context,ListRemoteAndroidInfo listInfo,int resid)
	{
		this(context,listInfo,resid,null);
	}
	public ListRemoteAndroidInfoAdapter(Context context,ListRemoteAndroidInfo listInfo,Filter filter)
	{
		this(context,listInfo,android.R.layout.two_line_list_item,filter);
	}
	public ListRemoteAndroidInfoAdapter(Context context,ListRemoteAndroidInfo listInfo,int resid,Filter filter)
	{
		mContext=context;
		mListInfo=listInfo;
		mFilter=filter;
		mResId=resid;
		
		if (filter==null)
		{
			mListFilter=mListInfo;
		}
		else
		{
			mListFilter=new ArrayList<RemoteAndroidInfo>();
			for (int i=0;i<mListInfo.size();++i)
			{
				RemoteAndroidInfo info=mListInfo.get(i);
				if (mFilter.filter(info))
				{
					mListFilter.add(info);
				}
			}
		}
		listInfo.setListener(this);
	}

	public void setListener(DiscoverListener listener)
	{
		mListener=listener;
	}
	@Override
	public int getCount()
	{
		return mListFilter.size();
	}

	@Override
	public RemoteAndroidInfo getItem(int position)
	{
		return mListFilter.get(position);
	}

	@Override
	public long getItemId(int position)
	{
		return mListFilter.get(position).hashCode();
	}

	class Tag
	{
		TextView mText1;
		TextView mText2;
	}
	@Override
	public View getView(int position, View view, ViewGroup parent)
	{
		Tag tag;
		if (view==null)
		{
			final LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			view = inflater.inflate(mResId, parent, false);
			tag=new Tag();
			tag.mText1=((TextView)view.findViewById(android.R.id.text1));
			tag.mText2=((TextView)view.findViewById(android.R.id.text2));
			view.setTag(tag);
		}
		else
			tag=(Tag)view.getTag();
		RemoteAndroidInfoImpl info=(RemoteAndroidInfoImpl)mListFilter.get(position);
		tag.mText1.setText(info.getName());
		if (tag.mText2!=null)
		{
			tag.mText2.setText(Application.getTechnologies(info, false));
		}
		return view;
	}
	
	@Override
	public void onDiscoverStart()
	{
		if (mListener!=null)
			mListener.onDiscoverStart();
	}
	@Override
	public void onDiscoverStop()
	{
		if (mListener!=null)
			mListener.onDiscoverStop();
	}
	@Override
	public void onDiscover(RemoteAndroidInfo remoteAndroidInfo, boolean update)
	{
		notifyDataSetChanged();
		if (mFilter!=null)
		{
			for (int i=0;i<mListFilter.size();++i)
			{
				RemoteAndroidInfo info=mListFilter.get(i);
				if (info.getUuid().equals(remoteAndroidInfo.getUuid()))
				{
					if (!mFilter.filter(info))
					{
						// The change exclude the previous record
						mListFilter.remove(info);
						return;
					}
				}
			}
			// Unknown record
			if (mFilter.filter(remoteAndroidInfo))
			{
				// Add new record
				mListFilter.add(remoteAndroidInfo);
			}
			else
			{
				Log.d(TAG_DISCOVERY,PREFIX_LOG+" reject "+remoteAndroidInfo);
			}
		}
		if (mListener!=null)
			mListener.onDiscover(remoteAndroidInfo, update);
	}
}
