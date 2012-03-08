package org.remoteandroid.ui.connect.old;

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
public class NfcFragment extends AbstractBodyFragment
{
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View v=inflater.inflate(R.layout.connect_nfc, container, false);
		return v;
	}
}
