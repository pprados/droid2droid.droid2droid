package org.remoteandroid.ui.connect;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import org.remoteandroid.R;
import org.remoteandroid.internal.Base64;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.ui.connect.ConnectActivity.TryConnection;
import org.remoteandroid.ui.expose.TicketExpose;
import static org.remoteandroid.Constants.*;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.*;


import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class InputIdFragment extends AbstractBodyFragment
{
	View mViewer;
	EditText mEdit;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer =inflater.inflate(R.layout.connect_inputid, container, false);
		mEdit=(EditText)mViewer.findViewById(R.id.edit);
		mEdit.setOnEditorActionListener(new OnEditorActionListener()
		{

			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
			{
				if (actionId==EditorInfo.IME_ACTION_DONE)
				{
					doConnect();
					return true;
				}
				return false;
			}
			
		});
		Button button=(Button)mViewer.findViewById(android.R.id.button1);
		button.setOnClickListener(new Button.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				doConnect();
			}

		});
		return mViewer;
	}
	private void doConnect()
	{
		final String ticket=mEdit.getText().toString();
		ConnectActivity activity=(ConnectActivity)getActivity();
		activity.tryConnect(new ConnectActivity.FirstStep()
		{
			public int run(ConnectActivity.TryConnection tryConn) 
			{
				try
				{
					URL url=new URL(TicketExpose.GOOGLE_SHORTEN+ticket);
					HttpURLConnection conn=(HttpURLConnection)url.openConnection();
					conn.setInstanceFollowRedirects(false);
					int responsecode=conn.getResponseCode();
					String loc=conn.getHeaderField("location");
					if (responsecode==HttpURLConnection.HTTP_MOVED_PERM)
					{
						if (loc.startsWith(TicketExpose.BASE_SHORTEN))
						{
							loc=loc.substring(TicketExpose.BASE_SHORTEN.length());
							byte[] bytes=Base64.decode(loc, Base64.URL_SAFE);
							Messages.Candidates candidates=Messages.Candidates.parseFrom(bytes);
							tryConn.setUris(ProtobufConvs.toUris(candidates));
						}
						else
						{
							if (I) Log.i(TAG_CONNECT,PREFIX_LOG+"Shorten response must start with "+TicketExpose.BASE_SHORTEN+" ("+loc+")");
							return R.string.connect_input_message_error_get_format;
						}
					}
					else
					{
						if (I) Log.i(TAG_CONNECT,PREFIX_LOG+"Shorten response must be "+HttpURLConnection.HTTP_MOVED_PERM+" ("+responsecode+")");
						return R.string.connect_input_message_error_get_format;
					}
					return 0;
					
				}
				catch (Exception e)
				{
					if (E) Log.e(TAG_CONNECT,PREFIX_LOG+"Error when retreive shorten ticket ("+e.getMessage()+")");
					return R.string.connect_input_message_error_get_internet;
				}
			}
		},new ArrayList<String>(),((ConnectActivity)getActivity()).isAcceptAnonymous());
	}
	@Override
	public void onResume()
	{
		super.onResume();
		mEdit.requestFocus();
	}
}
