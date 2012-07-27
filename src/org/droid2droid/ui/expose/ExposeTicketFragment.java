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
package org.droid2droid.ui.expose;

import static org.droid2droid.Constants.TAG_EXPOSE;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_NET;
import static org.droid2droid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.droid2droid.internal.Constants.D;
import static org.droid2droid.internal.Constants.PREFIX_LOG;
import static org.droid2droid.internal.Constants.V;
import static org.droid2droid.internal.Constants.W;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.droid2droid.AsyncTaskWithException;
import org.droid2droid.R;
import org.droid2droid.internal.Base64;
import org.droid2droid.internal.Messages;
import org.droid2droid.internal.NetworkTools;
import org.droid2droid.pairing.Trusted;
import org.droid2droid.ui.AbstractBodyFragment;
import org.droid2droid.ui.FeatureTab;
import org.droid2droid.ui.TabsAdapter;

import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;


public final class ExposeTicketFragment extends AbstractBodyFragment
{
	public static final String GOOGLE_SHORTEN_API="https://www.googleapis.com/urlshortener/v1/url";
	public static final String GOOGLE_SHORTEN="http://goo.gl/";
	public static final String BASE_SHORTEN="http://www.droid2droid.org/";

	TextView mUsage;
	TextView mTicket;

	public static class Provider extends FeatureTab
	{
		
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET);
		}

		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			Tab tab=actionBar.newTab()
					.setIcon(R.drawable.ic_tab_keyboard)
			        .setText(R.string.expose_ticket);
			tabsAdapter.addTab(tab, ExposeTicketFragment.class, null);
		}
	}
	
	static final Pattern sPattern=Pattern.compile(" *\"(.*)\": *\"(.*)\".*");
	//http://code.google.com/intl/fr-FR/apis/urlshortener/v1/getting_started.html#APIKey
	class ShortenURL extends AsyncTaskWithException<Void, Void, String>
	{
		private final String mMessage;
		public ShortenURL(String message)
		{
			mMessage=message;
		}
		@Override
		protected String doInBackground(Void... params) throws MalformedURLException, IOException
		{
			Messages.Candidates candidates=Trusted.getConnectMessage(getActivity());
			byte[] bytes=candidates.toByteArray();
			String base64=Base64.encodeToString(bytes, Base64.URL_SAFE|Base64.NO_WRAP);
			HttpURLConnection connection=null;
			OutputStreamWriter writer=null;
			BufferedReader reader=null;
			try
			{
				connection=(HttpURLConnection)new URL(GOOGLE_SHORTEN_API)
					.openConnection();
				connection.setDoOutput(true);
				connection.setRequestMethod("POST");
				connection.setRequestProperty("content-type", "application/json");
				connection.setRequestProperty("accept", "application/json");
				writer = new OutputStreamWriter(connection.getOutputStream(),Charset.forName("iso8859-1"));
				final String request="{\"longUrl\": \""+BASE_SHORTEN+base64+"\"}";
				writer.write(request);
				writer.flush();
				if (V) Log.v(TAG_EXPOSE,PREFIX_LOG+"Response code="+connection.getResponseCode());
				reader=new BufferedReader(new InputStreamReader(connection.getInputStream()),8096);
				
				for (;;)
				{
					String line=reader.readLine();
					if (line==null) break;
					Matcher matcher=sPattern.matcher(line);
					if (matcher.find())
					{
						if ("id".equals(matcher.group(1)))
						{
							String shorten=matcher.group(2);
							if (shorten.startsWith(GOOGLE_SHORTEN))
								return shorten.substring(GOOGLE_SHORTEN.length());
							else
								if (W) Log.w(TAG_EXPOSE,PREFIX_LOG+"Url must start with "+GOOGLE_SHORTEN);
						}
					}
				}
			}
			finally
			{
				if (connection!=null) connection.disconnect();
			}
			return null;
		}
		
		@Override
		protected void onException(final Throwable e)
		{
			if ((getActivity()==null) || getActivity().isFinishing()) 
				return;
			if (D) Log.d(TAG_EXPOSE,PREFIX_LOG+"Error when load shorten url",e);
//			if (D)
//			{
//				Application.sHandler.post(new Runnable()
//				{
//					@Override
//					public void run()
//					{
//						Toast.makeText(Application.sAppContext, e.getMessage(), Toast.LENGTH_SHORT).show();
//					}
//				});
//			}
			mUsage.setText(R.string.connect_ticket_message_error_set_internet);
			mTicket.setVisibility(View.INVISIBLE);
		}
		@Override
		protected void onPostExecute(String result)
		{
			if (D) Log.d(TAG_EXPOSE, PREFIX_LOG+"Ticket="+result);
			final String message = String.format(mMessage, result);
			mTicket.setText(Html.fromHtml(message));
		}
	}
	
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View main=inflater.inflate(R.layout.expose_ticket, container, false);
		mUsage=(TextView)main.findViewById(R.id.usage);
		mTicket=(TextView)main.findViewById(R.id.ticket);
		return main;
	}
	@Override
	protected void onUpdateActiveNetwork(int activeNetwork)
	{
		if (mUsage==null) // Not yet initialized
			return;
		boolean airplane=Settings.System.getInt(getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
		if (airplane)
		{
			mUsage.setText(R.string.expose_ticket_help_airplane);
			mTicket.setVisibility(View.INVISIBLE);
		}
		else
		{
			if ((activeNetwork & NetworkTools.ACTIVE_INTERNET_NETWORK)==0)
			{
				mUsage.setText(R.string.expose_ticket_help_internet);
				mTicket.setVisibility(View.INVISIBLE);
			}
			else
			{
				mUsage.setText(R.string.expose_ticket_help);
				mTicket.setVisibility(View.VISIBLE);
				new ShortenURL(getResources().getString(R.string.expose_ticket_message)).execute();
			}
		}
	}
}
