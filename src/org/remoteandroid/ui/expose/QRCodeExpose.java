package org.remoteandroid.ui.expose;

import org.remoteandroid.R;
import org.remoteandroid.ui.connect.qrcode.TestQRCode;

import android.content.Context;
import android.content.Intent;

class QRCodeExpose extends Expose
{
	QRCodeExpose()
	{
		super(R.string.expose_qrcode,KEY_QRCODE);
	}

	@Override
	public void startExposition(Context context)
	{
		context.startActivity(new Intent(context,TestQRCode.class));
	}
}
