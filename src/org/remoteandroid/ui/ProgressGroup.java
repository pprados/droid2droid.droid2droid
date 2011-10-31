package org.remoteandroid.ui;

import org.remoteandroid.R;

import android.content.Context;
import android.preference.PreferenceGroup;
import android.util.AttributeSet;
import android.view.View;

public class ProgressGroup
extends PreferenceGroup
{

	private boolean	mProgress	= false;

	private View	oldView		= null;

	public ProgressGroup(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		setLayoutResource(R.layout.preference_progress_category);
	}

	@Override
	public void onBindView(View view)
	{
		super.onBindView(view);
		View textView = view.findViewById(R.id.scanning_text);
		View progressBar = view.findViewById(R.id.scanning_progress);

		int visibility = mProgress ? View.VISIBLE : View.INVISIBLE;
		textView.setVisibility(visibility);
		progressBar.setVisibility(visibility);

		if (oldView != null)
		{
			oldView.findViewById(R.id.scanning_progress).setVisibility(View.GONE);
			oldView.findViewById(R.id.scanning_text).setVisibility(View.GONE);
			oldView.setVisibility(View.GONE);
		}
		oldView = view;
	}

	/**
	 * Turn on/off the progress indicator and text on the right.
	 * 
	 * @param progressOn
	 *            whether or not the progress should be displayed
	 */
	public void setProgress(boolean progressOn)
	{
		mProgress = progressOn;
		notifyChanged();
	}
}
