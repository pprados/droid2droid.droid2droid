package org.remoteandroid.ui;

import static org.remoteandroid.Constants.SHOW_FINAL_NOTIF_AFTER_DOWNLOAD;

import java.security.PrivilegedAction;

import org.remoteandroid.Constants;
import org.remoteandroid.R;
import org.remoteandroid.binder.AbstractSrvRemoteAndroid.DownloadFile;
import org.remoteandroid.install.DownloadApkActivity;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.service.RemoteAndroidService;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;
public final class Notifications
{
	public static final String LABEL_NOTIF_DOWNLOAD="download";
	private final Context mContext;
    public NotificationManager mNotificationMgr;
    int mInboundSuccNumber;
    int mInboundFailNumber;
    
    public static final String EXTRA_ID="id";
	public static final int ONGOING_NOTIFICATION=-1000001;
    public static int NOTIFICATION_ID_INBOUND=-1000006;
    public static int NOTIFICATION_INSTALL=-1000007;
    
    /**
     * Helper function to build the progress text.
     */
    public static String formatProgressText(long totalBytes, long currentBytes) 
    {
        if (totalBytes <= 0) 
        {
            return "0%";
        }
        long progress = currentBytes * 100 / totalBytes;
        return new StringBuilder()
        	.append(progress)
        	.append('%')
        	.toString();
    }
    
    public Notifications(Context context)
    {
    	mContext=context;
        mNotificationMgr = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void serviceShow(final Service service)
    {
		Intent notificationIntent = new Intent(mContext, EditPreferenceActivity.class);
		Notification notification=null;
		// deprecated
        if (Build.VERSION.SDK_INT>Build.VERSION_CODES.ECLAIR_0_1)
		{
			new Runnable()
			{
				@Override
				public void run() 
				{
			        mNotificationMgr.cancel(LABEL_NOTIF_DOWNLOAD,NOTIFICATION_ID_INBOUND);
				}
			}.run();
		}
		else
			mNotificationMgr.cancel(NOTIFICATION_ID_INBOUND);
		if (Build.VERSION.SDK_INT<Build.VERSION_CODES.HONEYCOMB)
		{
	    	notification = new Notification(
		    		R.drawable.ic_stat_remoteandroid, 
		    		mContext.getText(R.string.service_start_ticker),
		            System.currentTimeMillis());
		}		
		else
		{
			notification=new PrivilegedAction<Notification>()
			{
				@TargetApi(11)
				@Override
				public Notification run()
				{
					return new Notification.Builder(mContext)
						.setSmallIcon(R.drawable.ic_stat_remoteandroid)
						.setTicker(mContext.getText(R.string.service_start_ticker))
						.setWhen(System.currentTimeMillis())
						.getNotification();
				}
			}.run();
		}
		notification.setLatestEventInfo(mContext, 
				mContext.getText(R.string.service_start_title),
				mContext.getText(R.string.service_start_message), 
				PendingIntent.getActivity(mContext, 0, notificationIntent, 0));
    	final Notification fnotification=notification;
    	new Runnable()
    	{
    		@Override
			public void run() 
    		{
    			service.startForeground(ONGOING_NOTIFICATION, fnotification);
    		}
    	}.run();
    }
    
    //----------------
    // TODO: For 1.6 compatibility
//    private NotificationManager mNM;
//    private Method mSetForeground;
//    private Method mStartForeground;
//    private Method mStopForeground;
//    private Object[] sSetForegroundArgs = new Object[1];
//    private Object[] mStartForegroundArgs = new Object[2];
//    private Object[] mStopForegroundArgs = new Object[1];
//    
//    void initStartForegroundCompat()
//    {
//    	mNM = (NotificationManager)mContext.getSystemService(Context.NOTIFICATION_SERVICE);
//	    try 
//	    {
//	        mStartForeground = getClass().getMethod("startForeground",mStartForegroundSignature);
//	        mStopForeground = getClass().getMethod("stopForeground",mStopForegroundSignature);
//	        return;
//	    } 
//	    catch (NoSuchMethodException e) 
//	    {
//	        // Running on an older platform.
//	        mStartForeground = mStopForeground = null;
//	    }
//   	    try 
//   	    {
//    	        mSetForeground = getClass().getMethod("setForeground",mSetForegroundSignature);
//    	} 
//   	    catch (NoSuchMethodException e) 
//    	{
//            // Should not happen.
//   	        throw new IllegalStateException("OS doesn't have Service.startForeground OR Service.setForeground!");
//    	}
//    }
//    
//    private void invokeMethod(Method method, Object[] args) 
//    {
//        try 
//        {
//            method.invoke(this, args);
//        } 
//        catch (InvocationTargetException e) 
//        {
//            // Should not happen.
//            if (W) Log.w(TAG_SERVER_BIND,"Unable to invoke method", e);
//        } 
//        catch (IllegalAccessException e) 
//        {
//            // Should not happen.
//            if (W) Log.w(TAG_SERVER_BIND, "Unable to invoke method", e);
//        }
//    }
//
//    /**
//     * This is a wrapper around the new startForeground method, using the older
//     * APIs if it is not available.
//     */
//    void startForegroundCompat(int id, Notification notification) 
//    {
//        // If we have the new startForeground API, then use it.
//        if (mStartForeground != null) {
//            mStartForegroundArgs[0] = Integer.valueOf(id);
//            mStartForegroundArgs[1] = notification;
//            invokeMethod(mStartForeground, mStartForegroundArgs);
//            return;
//        }
//
//        // Fall back on the old API.
//        mSetForegroundArgs[0] = Boolean.TRUE;
//        invokeMethod(mSetForeground, mSetForegroundArgs);
//        mNM.notify(id, notification);
//    }
//
//    /**
//     * This is a wrapper around the new stopForeground method, using the older
//     * APIs if it is not available.
//     */
//    void stopForegroundCompat(int id) 
//    {
//        // If we have the new stopForeground API, then use it.
//        if (mStopForeground != null) {
//            mStopForegroundArgs[0] = Boolean.TRUE;
//            invokeMethod(mStopForeground, mStopForegroundArgs);
//            return;
//        }
//
//        // Fall back on the old API.  Note to cancel BEFORE changing the
//        // foreground state, since we could be killed at that point.
//        mNM.cancel(id);
//        mSetForegroundArgs[0] = Boolean.FALSE;
//        invokeMethod(mSetForeground, mSetForegroundArgs);
//    }
    //----------------
    
    public void serviceCancel()
    {
    	mNotificationMgr.cancel(NOTIFICATION_ID_INBOUND);
    	
    }
    
    public void incomingApk(int id,String label,Intent intent,Intent cancelIntent)
    {
        String title = mContext.getString(R.string.incoming_apk_confirm_title,label);
        String caption = mContext.getString(R.string.incoming_apk_confirm_caption);
        Notification notification = new Notification();
        notification.icon = R.drawable.ic_stat_notify_incomming_file;
        notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE|Notification.FLAG_AUTO_CANCEL;
        notification.defaults = Notification.DEFAULT_ALL;
        notification.tickerText = title;
        notification.when = System.currentTimeMillis();
        notification.setLatestEventInfo(mContext, title, caption, PendingIntent.getActivity(mContext, 0, intent, 0));
        notification.deleteIntent=PendingIntent.getService(mContext, 0, cancelIntent, 0);
        mNotificationMgr.notify(id, notification);
    }
    
    public void autoinstall(String label)
    {
        Notification notification = new Notification();
        notification.icon = android.R.drawable.stat_sys_download;

        notification.setLatestEventInfo(mContext, 
        		label, // Title
        		mContext.getString(R.string.autoinstall_caption),
        		PendingIntent.getService(mContext, 0, null, 0));
        notification.when = System.currentTimeMillis();
        notification.flags=Notification.FLAG_AUTO_CANCEL;
        notification.tickerText=mContext.getString(R.string.autoinstall_ticket,label);
		mNotificationMgr.notify(NOTIFICATION_INSTALL, notification);
    	
    }
    
    public void install(String label,boolean success)
    {
        Notification notification = new Notification();

        if (success)
        {
	        notification.setLatestEventInfo(mContext, 
	        		label, // Title
	        		mContext.getString(R.string.installed_success_caption),
	        		PendingIntent.getService(mContext, 0, null, 0));
            notification.icon = R.drawable.ic_stat_notify_install_complete;
	        notification.when = System.currentTimeMillis();
	        notification.flags=Notification.FLAG_AUTO_CANCEL;
	        notification.tickerText=mContext.getString(R.string.installed_success_ticket,label);
			mNotificationMgr.notify(NOTIFICATION_INSTALL, notification);
        }
        else
        {
	        notification.icon = android.R.drawable.stat_sys_warning;
	        notification.setLatestEventInfo(mContext, 
	        		label, 
	        		mContext.getString(R.string.installed_fail_caption),
	        		PendingIntent.getService(mContext, 0, null, 0));
	        
	        notification.when = System.currentTimeMillis();
	        notification.flags=Notification.FLAG_AUTO_CANCEL;
	        notification.tickerText=mContext.getString(R.string.installed_fail_ticket,label);
			mNotificationMgr.notify(NOTIFICATION_INSTALL, notification);
        }
    }
    public void clearDownloads()
    {
    	mInboundFailNumber=mInboundSuccNumber=0;
        if (mNotificationMgr == null)
        	return;
        if (Compatibility.VERSION_SDK_INT>Compatibility.VERSION_ECLAIR_0_1)
		{
			new Runnable()
			{
				@Override
				public void run() 
				{
			        mNotificationMgr.cancel(LABEL_NOTIF_DOWNLOAD,NOTIFICATION_ID_INBOUND);
				}
			}.run();
		}
		else
			mNotificationMgr.cancel(NOTIFICATION_ID_INBOUND);
    }
    
    public void finishDownload(final DownloadFile df,boolean status)
    {
        if (mNotificationMgr == null)
        	return;
        if (Compatibility.VERSION_SDK_INT>Compatibility.VERSION_ECLAIR_0_1)
		{
			new Runnable()
			{
				@Override
				public void run() 
				{
			        mNotificationMgr.cancel(LABEL_NOTIF_DOWNLOAD,df.id);
				}
			}.run();
		}
		else
			mNotificationMgr.cancel(df.id);
    	
    	if (status)
    		mInboundSuccNumber++;
    	else
    		mInboundFailNumber++;
    	if (!SHOW_FINAL_NOTIF_AFTER_DOWNLOAD)
    	{
    		if (mInboundFailNumber==0)
    			return;
    	}
        long timeStamp=System.currentTimeMillis();

        Intent intent = new Intent(mContext,RemoteAndroidService.class);
        intent.setAction(Constants.ACTION_COMPLETE_HIDE);
        Notification inNoti = new Notification();
        if (status)
        {
            inNoti.icon = android.R.drawable.stat_sys_download_done;
        	inNoti.tickerText=mContext.getString(R.string.finish_download_ok_ticket,df.label);
        }
        else
        {
            inNoti.icon = android.R.drawable.stat_sys_warning;
        	inNoti.tickerText=mContext.getString(R.string.finish_download_fail_ticket,df.label);
        }
        inNoti.setLatestEventInfo(mContext, 
        		mContext.getString(R.string.finish_download_title), 
        		mContext.getString(R.string.finish_download_caption, mInboundSuccNumber,mInboundFailNumber),
        		PendingIntent.getService(mContext, 0, intent, 0));
        inNoti.deleteIntent = PendingIntent.getService(mContext, 0, intent, 0);
        inNoti.when = timeStamp;
        inNoti.flags=Notification.FLAG_AUTO_CANCEL;
        mNotificationMgr.notify(NOTIFICATION_ID_INBOUND, inNoti);
    	
    }
    public void updateDownload(final DownloadFile df)
    {
        if (mNotificationMgr == null)
        	return;
    	final long now=System.currentTimeMillis();
	    if ((df.lastNotification!=0) && ((now-df.lastNotification)<1000)) // Too quickly
	    	return;
    	df.lastNotification=now;
        
	    RemoteViews expandedView = new RemoteViews(mContext.getPackageName(),R.layout.status_bar_ongoing_event_progress_bar);
	    expandedView.setTextViewText(R.id.description, mContext.getString(R.string.update_download_title,df.label));
	    int total=1000;
        int progress = (int)(df.progress * total / df.size);
	    expandedView.setProgressBar(R.id.progress_bar, total,progress, total == -1);
	    expandedView.setTextViewText(R.id.progress_text,formatProgressText(df.size,df.progress));
	    expandedView.setImageViewResource(R.id.appIcon,android.R.drawable.stat_sys_download);

	    // Intent when clic notification
	    Intent intent = new Intent(mContext,DownloadApkActivity.class);
		intent.putExtra(EXTRA_ID, df.id);

		// Build the notification object
	    final Notification notification = new Notification();
	    notification.icon = android.R.drawable.stat_sys_download;
	    notification.flags = Notification.FLAG_ONGOING_EVENT;
	    notification.contentView = expandedView;
	    notification.tickerText=mContext.getString(R.string.update_download_ticket,df.label);
	    notification.contentIntent = PendingIntent.getActivity(mContext, 0, intent,  PendingIntent.FLAG_UPDATE_CURRENT);

        if (Compatibility.VERSION_SDK_INT>Compatibility.VERSION_ECLAIR_0_1)
		{
			new Runnable()
			{
				@Override
				public void run() 
				{
				    mNotificationMgr.notify(LABEL_NOTIF_DOWNLOAD,df.id, notification);
				}
			}.run();
		}
		else
			mNotificationMgr.cancel(df.id);
    }
}
