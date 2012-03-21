package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_NET;
import static org.remoteandroid.RemoteAndroidInfo.FEATURE_SCREEN;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.internal.Base64;
import org.remoteandroid.internal.Constants;
import org.remoteandroid.internal.Messages;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.ui.FeatureTab;
import org.remoteandroid.ui.TabsAdapter;
import org.remoteandroid.ui.expose.ExposeTicketFragment;

import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;

public class ConnectTicketFragment extends AbstractConnectFragment
{
	private static final long ESTIMATION_TICKET_3G=Constants.TIMEOUT_CONNECT_WIFI*2;

	private View mViewer;
	private TextView mUsage;
	EditText mEdit;
	
	public static class Provider extends FeatureTab
	{
		Provider()
		{
			super(FEATURE_SCREEN|FEATURE_NET);
		}
		@Override
		public void createTab(TabsAdapter tabsAdapter, ActionBar actionBar)
		{
			tabsAdapter.addTab(actionBar.newTab()
		        .setText(R.string.connect_ticket), ConnectTicketFragment.class, null);
		}
	}	
	
	@Override
	public void onPageSelected()
	{
		super.onPageSelected();
		if (getActivity()!=null) 
			getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
	}
	@Override
	public void onPageUnselected()
	{
		super.onPageUnselected();
		if (getActivity()==null) return;
		getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
	}
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer =inflater.inflate(R.layout.connect_ticket, container, false);
		mUsage = (TextView)mViewer.findViewById(R.id.usage);
		mEdit=(EditText)mViewer.findViewById(R.id.edit);
		mEdit.setOnEditorActionListener(new OnEditorActionListener()
		{

			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
			{
				if (actionId==EditorInfo.IME_ACTION_DONE)
				{
					showConnect(new String[0], true,null); // FIXME: acceptano
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
				showConnect(new String[0], true,null); // FIXME: acceptano
			}

		});
		return mViewer;
	}

	@Override
	protected void updateStatus(int activeNetwork)
	{
		if (mUsage==null)
			return;
		boolean airplane=Settings.System.getInt(getContentResolver(),Settings.System.AIRPLANE_MODE_ON, 0) != 0;
		if (airplane)
		{
			mUsage.setText(R.string.connect_ticket_help_airplane);
		}
		else
		if ((activeNetwork & (NetworkTools.ACTIVE_BLUETOOTH|NetworkTools.ACTIVE_LOCAL_NETWORK))!=0)
		{
			mUsage.setText(R.string.connect_ticket_help);
		}
		else
		{
			mUsage.setText(R.string.connect_ticket_help_internet);
		}
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}
	
	@Override
	public Object doTryConnect(
			ProgressJobs<?,?> progressJobs,
			ConnectDialogFragment fragment,
			String[] uris,
			Bundle param)
	{
		try
		{
			long[] estimations=new long[]{ESTIMATION_TICKET_3G,ESTIMATION_CONNEXION_3G*3};
			progressJobs.resetCurrentStep();
			progressJobs.setEstimations(estimations);
			progressJobs.incCurrentStep();
			Editable ticket=mEdit.getText();
			URL url=new URL(ExposeTicketFragment.GOOGLE_SHORTEN+ticket);
			HttpURLConnection conn=(HttpURLConnection)url.openConnection();
			conn.setInstanceFollowRedirects(false);
			int responsecode=conn.getResponseCode();
			String loc=conn.getHeaderField("location");
			if (responsecode==HttpURLConnection.HTTP_MOVED_PERM)
			{
				if (loc.startsWith(ExposeTicketFragment.BASE_SHORTEN))
				{
					loc=loc.substring(ExposeTicketFragment.BASE_SHORTEN.length());
					byte[] bytes=Base64.decode(loc, Base64.URL_SAFE);
					Messages.Candidates candidates=Messages.Candidates.parseFrom(bytes);
					uris=ProtobufConvs.toUris(Application.sAppContext,candidates).toArray(new String[0]);
					estimations=new long[uris.length+1];
					Arrays.fill(estimations, ESTIMATION_CONNEXION_3G);
					estimations[0]=ESTIMATION_TICKET_3G;
					progressJobs.setEstimations(estimations);
					return ConnectDialogFragment.tryAllUris(progressJobs, uris, this);
				}
				else
				{
					if (I) Log.i(TAG_CONNECT,PREFIX_LOG+"Shorten response must start with "+ExposeTicketFragment.BASE_SHORTEN+" ("+loc+")");
					return R.string.connect_ticket_message_error_get_format;
				}
			}
			else
			{
				if (I) Log.i(TAG_CONNECT,PREFIX_LOG+"Shorten response must be "+HttpURLConnection.HTTP_MOVED_PERM+" ("+responsecode+")");
				return R.string.connect_ticket_message_error_get_format;
			}
		}
		catch (final Exception e)
		{
			if (E) Log.e(TAG_CONNECT,PREFIX_LOG+"Error when retreive shorten ticket ("+e.getMessage()+")");
			if (D)
			{
				if (D) Log.d(TAG_CONNECT,PREFIX_LOG+"Error when retreive shorten ticket ("+e.getMessage()+")",e);
				Application.sHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						Toast.makeText(Application.sAppContext, e.getMessage(), Toast.LENGTH_LONG).show();
					}
				});
			}
			return R.string.connect_ticket_message_error_get_internet;
		}
	}
}
