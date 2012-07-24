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
package org.droid2droid.ui;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.ListPreference;
import android.preference.Preference;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;

/**
 * A {@link Preference} that displays a list of entries as a dialog and allows multiple selections
 * <p>
 * This preference will store a string into the SharedPreferences. This string will be the values
 * selected from the {@link #setEntryValues(CharSequence[])} array.
 * </p>
 * 
 * @see http://blog.350nice.com/wp/wp-content/uploads/2009/07/listpreferencemultiselect.java
 */
// FIXME: Il manque une ligne blanche au dessus
public final class MultiSelectListPreference extends ListPreference
{
	// Need to make sure the SEPARATOR is unique and weird enough that it doesn't match one of the
	// entries.
	// Not using any fancy symbols because this is interpreted as a regex for splitting strings.
	private static final String	SEPARATOR	= ",";

	private boolean[]			mClickedDialogEntryIndices;

	public MultiSelectListPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		mClickedDialogEntryIndices = new boolean[0];
	}

	@Override
	public void setEntries(CharSequence[] entries)
	{
		super.setEntries(entries);
		mClickedDialogEntryIndices = new boolean[entries.length];
	}

	public MultiSelectListPreference(Context context)
	{
		this(context, null);
	}

	@Override
	protected void onPrepareDialogBuilder(Builder builder)
	{
		CharSequence[] entries = getEntries();
		CharSequence[] entryValues = getEntryValues();

		if (entries == null || entryValues == null || entries.length != entryValues.length)
		{
			throw new IllegalStateException(
					"ListPreference requires an entries array and an entryValues array which are both the same length");
		}

		restoreCheckedEntries();
		builder.setMultiChoiceItems(entries, mClickedDialogEntryIndices,
				new DialogInterface.OnMultiChoiceClickListener()
				{
					@Override
					public void onClick(DialogInterface dialog, int which, boolean val)
					{
						AlertDialog ad=(AlertDialog)dialog;
						 
						// FIXME: Bug si modification de donnée, puis basculement. La sauvegarde le propriété n'est pas faite.
						// FIXME: Pb lors du basculement. La gestion du all ne fonctionne plus
//						ListView lv=ad.getListView();
//						mClickedDialogEntryIndices[which] = val;
//						if (which!=0)
//						{
//							mClickedDialogEntryIndices[0]=false;
//							lv.getCheckedItemPositions().put(0, false);
//						}
//						else
//						{
//							mClickedDialogEntryIndices[0]=val;
//							if (val==true)
//							{
//								SparseBooleanArray ba=lv.getCheckedItemPositions();
//								for (int i=mClickedDialogEntryIndices.length-1;i>0;--i)
//								{
//									ba.put(i, false);
//									mClickedDialogEntryIndices[i]=false;
//								}
//							}
//						}
						setValue(buildValue(getEntryValues()).toString());
						((ArrayAdapter<?>)ad.getListView().getAdapter()).notifyDataSetChanged();
					}
				});
	}

	public static String[] parseStoredValue(CharSequence val)
	{
		if ("".equals(val))
			return null;
		else
			return ((String) val).split(SEPARATOR);
	}

	private void restoreCheckedEntries()
	{
		CharSequence[] entryValues = getEntryValues();

		String[] vals = parseStoredValue(getValue());
		if (vals != null)
		{
			for (int j = 0; j < vals.length; j++)
			{
				String val = vals[j].trim();
				for (int i = 0; i < entryValues.length; i++)
				{
					CharSequence entry = entryValues[i];
					if (entry.equals(val))
					{
						mClickedDialogEntryIndices[i] = true;
//						break;
					}
					else
					{
						mClickedDialogEntryIndices[i] = false;
					}
				}
			}
		}
	}

	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		// super.onDialogClosed(positiveResult);

		CharSequence[] entryValues = getEntryValues();
		if (positiveResult && entryValues != null)
		{
			StringBuilder value = buildValue(entryValues);

			if (callChangeListener(value))
			{
				String val = value.toString();
				if (val.length() > 0)
					val = val.substring(0, val.length() - SEPARATOR.length());
				setValue(val);
			}
		}
	}

	private StringBuilder buildValue(CharSequence[] entryValues)
	{
		StringBuilder value = new StringBuilder();
		for (int i = 0; i < entryValues.length; i++)
		{
			if (mClickedDialogEntryIndices[i])
			{
				value.append(entryValues[i]).append(SEPARATOR);
			}
		}
		return value;
	}

}
