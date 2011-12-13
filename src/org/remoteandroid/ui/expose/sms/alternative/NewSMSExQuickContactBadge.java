package org.remoteandroid.ui.expose.sms.alternative;

import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.QuickContact;
import android.util.AttributeSet;
import android.view.View;
import android.widget.QuickContactBadge;

public class NewSMSExQuickContactBadge extends QuickContactBadge
{
	private long _itemId;
	public NewSMSExQuickContactBadge(Context context)
	{
		super(context);
	}

	public NewSMSExQuickContactBadge(Context context, AttributeSet attrs) 
	{
        super(context, attrs, 0);
    }

    public NewSMSExQuickContactBadge(Context context, AttributeSet attrs, int defStyle)
	{
		super(context,attrs,defStyle);
	}

    public final void setItemId(long itemId)
    {
    	_itemId=itemId;
    }
	public void onClick(View v)
	{
//		final Uri contactUri=ProvidersManager.importVolatileContactToAndroid(_itemId, true,getContext());
//		assignContactUri(contactUri);
//		final Uri lookupUri = Contacts.getLookupUri(getContext().getContentResolver(), contactUri);
//		QuickContact.showQuickContact(getContext(), this, lookupUri, QuickContact.MODE_MEDIUM, mExcludeMimes);
	}
}

