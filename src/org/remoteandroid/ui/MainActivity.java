package org.remoteandroid.ui;


import org.remoteandroid.Application;
import org.remoteandroid.R;
import org.remoteandroid.ui.connect.ConnectActivity;
import org.remoteandroid.ui.expose.ExposeActivity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;


// TODO: expose invalide si non actif
public class MainActivity extends SherlockFragmentActivity
implements MainFragment.CallBack
{
	FragmentManager	mFragmentManager;
	MainFragment	mFragment;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		requestWindowFeature(Window.FEATURE_ACTION_BAR);
        requestWindowFeature(Window.FEATURE_CONTEXT_MENU);
		super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);
		setProgressBarIndeterminateVisibility(Boolean.FALSE); // Important: Use Boolean value !
		mFragmentManager = getSupportFragmentManager(); // getSupportFragmentManager();
		mFragment = (MainFragment) mFragmentManager.findFragmentById(R.id.fragment);
		Application.startService();
	}
	@Override
	protected void onResume()
	{
		super.onResume();
		mFragment.setCallBack(this);
	}
	@Override
	protected void onPause()
	{
		super.onPause();
		mFragment.setCallBack(null);
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.main_fragment_menu, menu);
		return true;
	}
	
	@Override
	public void onExpose()
	{
		Intent intent = new Intent(this, ExposeActivity.class);
		startActivity(intent);
	}
	@Override
	public void onConnect()
	{
		//Intent intent = new Intent(RemoteAndroidManager.ACTION_CONNECT_ANDROID); // TODO
		Intent intent = new Intent(this, ConnectActivity.class); // TO remove
		startActivity(intent);
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.config:
				startActivity(new Intent(this, EditPreferenceActivity.class));
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
}
