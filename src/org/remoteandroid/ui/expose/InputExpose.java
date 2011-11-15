package org.remoteandroid.ui.expose;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.*;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.remoteandroid.AsyncTaskWithException;
import org.remoteandroid.R;
import org.remoteandroid.internal.Base64;
import org.remoteandroid.ui.connect.ConnectMessages;
import org.remoteandroid.ui.connect.ConnectionCandidats;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.text.Html;
import android.util.Log;

public class InputExpose extends Expose
{
	private static final String APIKEY="AIzaSyDL67vMUDoqKRAM0g6pyrPaWSz-QpepvjM";
	public static final String GOOGLE_SHORTEN_API="https://www.googleapis.com/urlshortener/v1/url";
	public static final String GOOGLE_SHORTEN="http://goo.gl/";
	public static final String BASE_SHORTEN="http://www.remoteandroid.org/";
	
	InputExpose()
	{
		super(R.string.expose_input,KEY_INPUT);
	}
	private AlertDialog mAlertDialog;
	private ShortenURL mShortenURL;
	private Activity mActivity;
	
	@Override
	public void startExposition(Activity activity)
	{
		mActivity=activity;
		final String message = String.format(activity.getResources().getString(R.string.connect_input_message), "...");
		mAlertDialog=new AlertDialog.Builder(activity)
			.setIcon(android.R.drawable.ic_dialog_info)
			.setTitle(R.string.connect_input_expose_title)
			.setMessage(Html.fromHtml(message))
			.setPositiveButton(android.R.string.ok, null)
			.create();
		mShortenURL=new ShortenURL();
		mShortenURL.execute();
		mAlertDialog.show();
	}
	static final Pattern sPattern=Pattern.compile(" *\"(.*)\": *\"(.*)\".*");
	//http://code.google.com/intl/fr-FR/apis/urlshortener/v1/getting_started.html#APIKey
	class ShortenURL extends AsyncTaskWithException<Void, Void, String>
	{
		
		@Override
		protected String doInBackground(Void... params) throws MalformedURLException, IOException
		{
			ConnectMessages.Candidates candidates=ConnectionCandidats.getConnectMessage(mAlertDialog.getContext());
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
				reader=new BufferedReader(new InputStreamReader(connection.getInputStream()));
				
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
		protected void onException(Throwable e)
		{
			mAlertDialog.cancel();
			if (mActivity.isFinishing()) 
				return;
			if (D) Log.d(TAG_EXPOSE,PREFIX_LOG+"Error when load shorten url",e);
			mAlertDialog=new AlertDialog.Builder(mActivity)
				.setIcon(android.R.drawable.ic_dialog_info)
				.setTitle(R.string.connect_input_expose_title)
				.setMessage(R.string.connect_input_message_error_set_internet)
				.setPositiveButton(android.R.string.ok, null)
				.create();
			mAlertDialog.show(); // FIXME: BUG en cas d'arret de l'application. Unable to add window

		}
		@Override
		protected void onPostExecute(String result)
		{
			if (D) Log.d(TAG_EXPOSE, PREFIX_LOG+"Ticket="+result);
			final String message = String.format(mAlertDialog.getContext().getResources().getString(R.string.connect_input_message), result);
			mAlertDialog.setMessage(Html.fromHtml(message));
		}
	}
}
