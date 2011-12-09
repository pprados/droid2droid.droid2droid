package org.remoteandroid.ui.connect;

import org.remoteandroid.Application;
import org.remoteandroid.R;

import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import static org.remoteandroid.RemoteAndroidInfo.*;
public class EmptyBodyFragment extends AbstractBodyFragment
{
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View v=inflater.inflate(R.layout.connect_empty, container, false);
		TextView txt=(TextView)v.findViewById(R.id.connect_help);
		int feature=Application.sFeature;
		Technology[] techs=Technology.getTechnologies();
		SpannableStringBuilder builder=new SpannableStringBuilder(getText(R.string.connect_empty_help));
		for (int i=1;i<techs.length;++i)
		{
			if ((techs[i].mFeature & feature)==techs[i].mFeature)
			{
				builder.append(getText(techs[i].mEmptyHelp));
			}
		}
		txt.setText(builder);
		return v;
	}
}
