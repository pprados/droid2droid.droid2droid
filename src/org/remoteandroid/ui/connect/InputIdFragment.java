package org.remoteandroid.ui.connect;

import org.remoteandroid.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

public class InputIdFragment extends AbstractBodyFragment
{
	View mViewer;
	EditText mEdit;
	Runnable mFirstStep=new Runnable()
	{
		public void run() 
		{
			// TODO: Convert code to datas
		}
	};
	
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
					ConnectActivity activity=(ConnectActivity)getActivity();
					activity.tryConnect(mFirstStep);
					return true;
				}
				return false;
			}
			
		});
//		mEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
//		    @Override
//		    public void onFocusChange(View v, boolean hasFocus) {
//		        if (hasFocus) 
//		        {
//		            getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
//		        }
//		    }
//		});
		Button button=(Button)mViewer.findViewById(android.R.id.button1);
		button.setOnClickListener(new Button.OnClickListener()
		{
			
			@Override
			public void onClick(View v)
			{
				ConnectActivity activity=(ConnectActivity)getActivity();
				activity.tryConnect(mFirstStep);
			}
		});
		return mViewer;
	}
	@Override
	public void onResume()
	{
		super.onResume();
		mEdit.requestFocus();
	}
}
