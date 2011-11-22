package org.remoteandroid.ui.connect;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.QuickContactBadge;

public class ExQuickContactBadge extends QuickContactBadge {
	private long _itemId;

	public ExQuickContactBadge(Context context) {
		super(context);
	}

	public ExQuickContactBadge(Context context, AttributeSet attrs) {
		super(context, attrs, 0);
	}

	public ExQuickContactBadge(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public final void setItemId(long itemId) {
		_itemId = itemId;
	}
}
