package org.remoteandroid.binder;

import static org.remoteandroid.Constants.ACTION_CLEAR_PROPOSED;
import static org.remoteandroid.Constants.LOCK_ASK_DOWNLOAD;
import static org.remoteandroid.Constants.LOCK_WAIT_INSTALL;
import static org.remoteandroid.Constants.NOTIFY_NEW_APK;
import static org.remoteandroid.Constants.PREFERENCES_KNOWN_ACCEPT_ALL;
import static org.remoteandroid.Constants.START_PROGRESS_ACTIVITY_WHEN_DOWNLOAD_APK;
import static org.remoteandroid.Constants.TAG_SERVER_BIND;
import static org.remoteandroid.Constants.TIMEOUT_BETWEEN_SEND_FILE_DATA;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.TAG_INSTALL;
import static org.remoteandroid.internal.Constants.UPDATE_PARCEL;
import static org.remoteandroid.internal.Constants.V;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.remoteandroid.Application;
import org.remoteandroid.CommunicationWithLock;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.R;
import org.remoteandroid.RemoteAndroid;
import org.remoteandroid.install.AskAcceptDownloadApkActivity;
import org.remoteandroid.install.DownloadApkActivity;
import org.remoteandroid.install.InstallApkActivity;
import org.remoteandroid.internal.AbstractProtoBufRemoteAndroid;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.IRemoteAndroid;
import org.remoteandroid.internal.Login;
import org.remoteandroid.internal.NormalizeIntent;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.service.RemoteAndroidService;
import org.remoteandroid.ui.Notifications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;


public abstract class AbstractSrvRemoteAndroid implements IRemoteAndroid
{
	public static final String CANCEL="cancel";
	protected Context mContext;
	private Random mRandom=new Random();
	
    private Notifications mNotifications;
    
	private ConnectionListener mListener;
    interface ConnectionListener
    {
    	public void onStart(AbstractSrvRemoteAndroid daemon);
    	public void onStop(AbstractSrvRemoteAndroid daemon);
    } 
	
	static class RefBinder
	{
		IBinder binder;
		int refcnt;
	}

	public static class ConnectionContext
	{
		public enum State { CONNECTED, CONNECTED_FOR_PAIRING,ANONYMOUS, LOGGING, PAIRED, CLOSED};
		
		// The current state
		public State mState;

		public RemoteAndroidInfoImpl mClientInfo;
		
		public Login mLogin;
		
		public ConnectionType mType;
		
		// All reference to binder for this connexion
		private SparseArray<RefBinder> mActiveBinder = new SparseArray<RefBinder>();
	}
	
	private ReadWriteLock mLock=new ReentrantReadWriteLock();
	private SparseArray<ConnectionContext> mConnectionContext = new SparseArray<ConnectionContext>(); 
	
	protected ConnectionContext getContext(int connid)
	{
		ConnectionContext conContext;
		try
    	{
    		mLock.readLock().lock();
	    	conContext=mConnectionContext.get(connid);
    	}
		finally
		{
			mLock.readLock().unlock();
		}
		return conContext;
	}
    protected void setContext(int connid,ConnectionContext context)
    {
    	try
    	{
    		mLock.writeLock().lock();
    		mConnectionContext.put(connid, context);
    	}
    	finally
    	{
    		mLock.writeLock().unlock();
    	}
    }

	// Current download files
	public static class DownloadFile
	{
		public int id;
		public boolean finish;
		public long uptime;
		public String from;
		public String filename;
		public String label;
		OutputStream out;
		public long progress;
		public long size;
		public long lastNotification;
	}
	
	private static SparseArray<DownloadFile> sCurrentOutput=new SparseArray<DownloadFile>();

	public static synchronized DownloadFile getDowloadFile(int fd)
	{
		return sCurrentOutput.get(fd);
	}
	public static synchronized void removeDownloadFileDescriptor(int fd)
	{
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"Remove download id "+fd);
		sCurrentOutput.remove(fd);
	}
	public static synchronized void appendDownloadFileDescriptor(int fd,DownloadFile value)
	{
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"Add download id="+fd+" form "+value.from);
		sCurrentOutput.append(fd, value);
	}
	@Override
	public void finalize()
	{
		synchronized (sCurrentOutput)
		{
			for (int i=sCurrentOutput.size()-1;i>=0;--i)
			{
				DownloadFile df=getDowloadFile(sCurrentOutput.keyAt(i));
				try
				{
					df.out.close();
					if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"Delete file "+df.filename);
					mContext.deleteFile(df.filename);
				}
				catch (IOException e)
				{
					if (I) Log.i(TAG_INSTALL,PREFIX_LOG+"Error when delete temporary file "+df.filename,e);
				}
			}
		}
	}
	public AbstractSrvRemoteAndroid(Context context,Notifications notifications)
	{
		mContext = context.getApplicationContext();
		mNotifications=notifications;
	}

	public void addListener(ConnectionListener listener)
	{
		mListener=listener;
	}
	public void removeListener(ConnectionListener listener)
	{
		mListener=null;
	}
	
	public void start()
	{
		if (mListener!=null)
			mListener.onStart(this);
	}
	public void stop()
	{
		if (mListener!=null)
			mListener.onStop(this);
	}
	@Override
	public boolean transactRemoteAndroid(int connid,int code, Parcel data, Parcel reply,
			int flags,long timeout)
	{
		int i;
		boolean bool;
		try
		{
			switch (code)
			{
				case AbstractProtoBufRemoteAndroid.BIND_OID:
					ComponentName[] name=new ComponentName[1];
					Intent intent=NormalizeIntent.readIntent(data);
					i=bindOID(connid,intent,data.readInt(),name,timeout);
					reply.writeNoException();
					reply.writeParcelable(name[0], 0);
					reply.writeInt(i);
					return true;
				case AbstractProtoBufRemoteAndroid.FINALIZE_OID:
					finalizeOID(connid,data.readInt(),timeout);
					reply.writeNoException();
					return true;
				case AbstractProtoBufRemoteAndroid.IS_BINDER_ALIVE:
					bool=isBinderAlive(connid,data.readInt(),timeout);
					reply.writeNoException();
					reply.writeByte((byte)(bool?1:0));
					return true;
				case AbstractProtoBufRemoteAndroid.PING_BINDER:
					bool=pingBinder(connid,data.readInt(),timeout);
					reply.writeNoException();
					reply.writeByte((byte)(bool?1:0));
					return true;
			}
			return false;
		}
		catch (Exception e)
		{
			reply.writeException(e);
			return true;
		}
	}
	
	protected boolean transactApk(int connid,int code, Parcel data, Parcel reply,int flags,long timeout)
	{
		boolean bool;
		try
		{
			switch (code)
			{
				case AbstractProtoBufRemoteAndroid.CMD_PROPOSE_APK:
					int i=proposeApk(connid,data.readString(), data.readString(), data.readInt(), data.createByteArray(), data.readLong(), flags, timeout);
					reply.writeNoException();
					reply.writeInt(i);
					return true;
					
				case AbstractProtoBufRemoteAndroid.CMD_SEND_FILE:
					bool=sendFileData(connid,data.readInt(), data.createByteArray(), data.readInt(), data.readLong(), data.readLong(), timeout);
					reply.writeNoException();
					reply.writeByte((byte)(bool?1:0));
					return true;
					
				case AbstractProtoBufRemoteAndroid.CMD_CANCEL_FILE:
					cancelFileData(connid,data.readInt(), timeout);
					reply.writeNoException();
					return true;
					
				case AbstractProtoBufRemoteAndroid.CMD_INSTALL_APK:
					bool=installApk(connid,data.readString(),data.readInt(),flags,timeout);
					reply.writeNoException();
					reply.writeByte((byte)(bool?1:0));
					return true;
			}
			return false;
		}
		catch (Exception e)
		{
			reply.writeException(e);
			if (D) Log.d(TAG_SERVER_BIND,PREFIX_LOG+"Error Apk",e);
			return true;
		}
	}
	protected final IBinder getBinder(int connid,int oid)
	{
		final ConnectionContext context=mConnectionContext.get(connid);
		RefBinder ref=context.mActiveBinder.get(oid);
		if (ref==null) 
			return null;
		return ref.binder;
	}
	
	@Override
	public final int bindOID(int connid,final Intent intent,final int flags,final ComponentName[] name,long timeout)
	{
		if (V) Log.v(TAG_SERVER_BIND, PREFIX_LOG+"bind(" + intent + ")");
		final ConnectionContext context=mConnectionContext.get(connid);
		final AtomicReference<IBinder> ref=new AtomicReference<IBinder>();
		synchronized (ref)
		{
			if (mContext.bindService(
				intent, // Nom du service, à ajouter dans le filtre du remote
				new ServiceConnection() // Handler de connection
				{
					// Connection
					public void onServiceConnected(ComponentName className,
							IBinder service)
					{
						if (V) Log.v(TAG_SERVER_BIND, PREFIX_LOG+"Service connected " + className);
						name[0]=className;
						// Keep service
						ref.set(service);
						synchronized (ref)
						{

							ref.notify();
						}
						if (V) Log.v(TAG_SERVER_BIND, PREFIX_LOG+"notify...");
					}

					// Deconnection
					public void onServiceDisconnected(ComponentName className)
					{
						if (E) Log.e(TAG_SERVER_BIND, PREFIX_LOG+"Service disconnected " + className); //TODO: Signaler au parent
					}
				}, flags))
			{
				if (V) Log.v(TAG_SERVER_BIND, PREFIX_LOG+"bindService internal android for "+intent+" ok");
			}
			else
			{
				if (E) Log.e(TAG_SERVER_BIND, PREFIX_LOG+"bindService "+intent+" KO");
				return -1;
			}
			try
			{
				if (V) Log.v(TAG_SERVER_BIND, PREFIX_LOG+"Enter wait loop...");
				ref.wait(10000); // FIXME: 10s pour attendre la connexion locale d'un service
			}
			catch (InterruptedException e)
			{
				// Ignore
				if (V) Log.v(TAG_SERVER_BIND, PREFIX_LOG+"Wait loop interrupted.");
			}
		}
		final IBinder binder = ref.get(); // Le Binder de l'objet present coté cloud, mais autre process
		if (binder==null) 
		{
			if (E) Log.e(TAG_SERVER_BIND, PREFIX_LOG+"bindService "+intent+" KO");
			return -1;
		}
		if (V) Log.v(TAG_SERVER_BIND, PREFIX_LOG+"Binder in remote android =" + binder);
		int oid = System.identityHashCode(binder);
		synchronized(this)
		{
			if (oid != 0)
			{
				RefBinder refBinder=context.mActiveBinder.get(oid);
				if (refBinder!=null)
				{
					++refBinder.refcnt;
				}
				else
				{
					refBinder=new RefBinder();
					refBinder.binder=binder;
					refBinder.refcnt=1;
					context.mActiveBinder.put(oid, refBinder);
				}
			}
		}
		if (V) Log.v(TAG_SERVER_BIND, PREFIX_LOG+"bind OID con=" + connid+" OID="+oid);
		return oid;
	}
	
	@Override
	public final synchronized void finalizeOID(int connid,int oid,long timeout)
	{
		final ConnectionContext context=mConnectionContext.get(connid);
		Integer ooid=oid;
		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"finalize Con="+connid+" OID="+oid);
		RefBinder refBinder=context.mActiveBinder.get(oid);
		if (refBinder!=null)
		{
			if (--refBinder.refcnt<=0)
			{
				context.mActiveBinder.remove(ooid);
			}
		}
		
	}
	
	public final synchronized void connectionClose(int connid)
	{
		if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"connectionClose="+connid);
		mConnectionContext.remove(connid);

		// TODO: fixer les downloads en cours
		synchronized (sCurrentOutput)
		{
			for (int i=sCurrentOutput.size()-1;i>=0;--i)
			{
				DownloadFile df=sCurrentOutput.valueAt(i);
				try
				{
					if (df!=null) 
					{
						df.out.close();
						if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"Delete file "+df.filename);
						mContext.deleteFile(df.filename);
						mNotifications.finishDownload(df, false);
					}
				}
				catch (IOException e)
				{
					// Ignore
					if (V) Log.v(TAG_SERVER_BIND, PREFIX_LOG+"Close error",e);
				}
			}
		}
	}
	
	@Override
	public final boolean isBinderAlive(int connid,int oid,long timeout) throws RemoteException
	{
		IBinder binder = getBinder(connid,oid);
		if (binder==null) return false;
		return binder.isBinderAlive();
	}

	@Override
	public final boolean pingBinder(int connid,int oid,long timeout) throws RemoteException
	{
		IBinder binder = getBinder(connid,oid);
		if (binder==null) return false;
		return binder.pingBinder();
	}

	@Override
	public boolean transactBinder(int connid,int oid, int code, Parcel data, Parcel reply,
			int flags,long timeout) throws RemoteException
	{
		IBinder binder = getBinder(connid,oid);
		if (binder==null) 
		{
			if (E) Log.e(TAG_SERVER_BIND,PREFIX_LOG+" binder is null for oid="+oid);
			return false;
		}
		boolean rc=binder.transact(code, data, reply, flags);
		updateReply(reply);
		return rc;
	}

	protected final Parcel updateData(Parcel data)
	{
		if (UPDATE_PARCEL)
		{
			// The first info in stream is the enforceInterface.
			// Because the code move between versions, I write a simple string
			// and inject a writeInterfaceToken() in start of the parcel.
			String enforceInterfaceName=data.readString();	// Read the interface name
			if (enforceInterfaceName==null)
				return null;
			// Write the interfaceToken and read the stream
			Parcel d=Parcel.obtain();	
			d.setDataPosition(0);
			d.writeInterfaceToken(enforceInterfaceName);
			byte[] interfaceBuf=d.marshall();
	
			// Read the rest of the stream
			int startDatas=data.dataPosition();
			byte[] bufDatas=data.marshall();
			
			// Now merge the two buffers: enforce interface + data
			byte[] result=new byte[interfaceBuf.length+bufDatas.length-startDatas];
			System.arraycopy(interfaceBuf, 0, result, 0, interfaceBuf.length);
			System.arraycopy(bufDatas, startDatas, result, interfaceBuf.length, bufDatas.length-startDatas);
			
			// Then re-create the parcel with this new stream
			d.unmarshall(result, 0, result.length);
			d.setDataPosition(0);

			data.setDataPosition(0);
			data.recycle();
			return d;
		}
		else
			return data;
	}
	private final Parcel updateReply(Parcel reply)
	{
		return reply;
	}
	
	// -----------------------------------------------------------------------------------------------------
   // Tools pour vérifier la signature. De toute facon, jette ensuite lors de l'installation si ca matche pas.
	int checkSignaturesLP(Signature[] s1, Signature[] s2) {
        if (s1 == null) {
            return s2 == null
                    ? PackageManager.SIGNATURE_NEITHER_SIGNED
                    : PackageManager.SIGNATURE_FIRST_NOT_SIGNED;
        }
        if (s2 == null) {
            return PackageManager.SIGNATURE_SECOND_NOT_SIGNED;
        }
        HashSet<Signature> set1 = new HashSet<Signature>();
        for (Signature sig : s1) {
            set1.add(sig);
        }
        HashSet<Signature> set2 = new HashSet<Signature>();
        for (Signature sig : s2) {
            set2.add(sig);
        }
        // Make sure s2 contains all signatures in s1.
        if (set1.equals(set2)) {
            return PackageManager.SIGNATURE_MATCH;
        }
        return PackageManager.SIGNATURE_NO_MATCH;
    }

	/**
	 *  @return
	 *  	-2: refuse to install non market application
	 *  	-1: refuse to install
	 *  	 0: not necessary
	 *      >0: accept and current id for download
	 */
	@Override
	public synchronized int proposeApk(
			int connid,
			final String label,
			String packageName,
			int version,
			byte[] sign, // FIXME: Use signature of apk ?
			final long len,
			int flags,
			final long timeout) throws RemoteException
	{
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv Client proposeApk("+label+","+packageName+"...");
		final long start=SystemClock.uptimeMillis();
		int securId;
		do
		{
			securId=mRandom.nextInt(9000);
		} while (sCurrentOutput.get(securId)!=null);
		final int id=securId;
		
		try
		{
			// Check packagename format
			if (!packageName.matches("[a-z][a-z\\.]+"))
				throw new IllegalArgumentException();

			// Check market
			if (Settings.Secure.getInt(mContext.getContentResolver(), Settings.Secure.INSTALL_NON_MARKET_APPS, 0)==0)
			{
				if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv refuse do to \"no market apps\"");
				return -2; // Refuse to install application not from market. TODO: Alert ?
			}
			
			// Search old version
			PackageManager pm=mContext.getPackageManager(); 
			try
			{
				if ((flags & RemoteAndroid.INSTALL_REPLACE_EXISTING)==0)
				{
					PackageInfo info=pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
					if (info.versionCode==version) // FIXME: Comparison des versioncode. > ou ==?
					{
						if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv refuse because remote have the same version");
						return 0;
					}
				}
			}
			catch (NameNotFoundException e)
			{
				// Ignore
			}

			// Ask user to confirm the download
			if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv try ask to user for accept the package...");
			final String device=getContext(connid).mClientInfo.name;
			final SharedPreferences preferences=Application.getPreferences();
			ConnectionContext ctx=getContext(connid);
			boolean accept=(getType()==ConnectionType.GSM) 
				? preferences.getBoolean(PREFERENCES_KNOWN_ACCEPT_ALL, false)
				: true;
            final NotificationManager notMgr = mNotifications.mNotificationMgr;
			long delta=(SystemClock.uptimeMillis()-start-3000L)/1000L;
			if (delta<0) delta=0L;
			final Intent intent = new Intent(mContext,AskAcceptDownloadApkActivity.class);
			intent.putExtra(Notifications.EXTRA_ID, id);
			intent.putExtra(AskAcceptDownloadApkActivity.EXTRA_DEVICE, device);
			intent.putExtra(AskAcceptDownloadApkActivity.EXTRA_DESCRIPTION, label);
			intent.putExtra(AskAcceptDownloadApkActivity.EXTRA_SIZE, len);
			intent.putExtra(AskAcceptDownloadApkActivity.EXTRA_TIMEOUT, timeout-(int)(delta)); // 3s below the end
			intent.putExtra(AskAcceptDownloadApkActivity.EXTRA_CHANEL, getType().name()); // Network type
			intent.setFlags(
				Intent.FLAG_ACTIVITY_NEW_TASK
				|Intent.FLAG_FROM_BACKGROUND
				|Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
				|Intent.FLAG_ACTIVITY_NO_ANIMATION
				|Intent.FLAG_ACTIVITY_NO_HISTORY
				|Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET
				);
			if (!accept)
			{
				// Notify the user
				if (NOTIFY_NEW_APK)
				{
					Application.sHandler.post(new Runnable()
					{
						@Override
						public void run()
						{
				            if (notMgr==null)
				            {
								mContext.startActivity(intent);
				            }
							else
							{
								// Si abandon du téléchargement par un clear de l'event...
								if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv cancel because the user clear the event");
								Intent cancelIntent = new Intent(mContext,RemoteAndroidService.class);
								cancelIntent.setAction(ACTION_CLEAR_PROPOSED);
								cancelIntent.putExtra(Notifications.EXTRA_ID, id);
								mNotifications.incomingApk(id,label,intent,cancelIntent);
								Application.sHandler.postAtTime(new Runnable()
								{
									@Override
									public void run()
									{
										notMgr.cancel(id); // Remove the notification after timeout
									}
								}, SystemClock.uptimeMillis()+timeout);
					    		if (Compatibility.VERSION_SDK_INT>Compatibility.VERSION_FROYO) // FROYO
					    		{
					    			final int incoming_apk_toast=0x7f050011; // R.string.incoming_apk_toast; 
					    			Toast.makeText(mContext, mContext.getString(incoming_apk_toast),Toast.LENGTH_LONG).show();
					    		}
					    		else
					    		{
					    			Toast.makeText(mContext, mContext.getString(R.string.incoming_apk_toast_backup),Toast.LENGTH_LONG).show();
					    		}
							}
						}
					});
				}
				else
				{
					Application.sHandler.post(new Runnable()
					{
						@Override
						public void run()
						{
							mContext.startActivity(intent);
						}
					});
				}
				Boolean b=(Boolean)CommunicationWithLock.getResult(LOCK_ASK_DOWNLOAD+id, timeout);
				if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv receive the user response");
				if (b==null)
					accept=false; // Timeout
				else
					accept=b.booleanValue();
				if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv it's "+accept);
			}
//			else
//			{
//	            String title = mContext.getString(R.string.incoming_file_confirm_Notification_title);
//	            String caption = mContext.getString(R.string.incoming_file_confirm_Notification_caption);
//	            Notification n = new Notification();
////	            n.icon = R.drawable.ra_incomming_file_notification;
//	            n.flags |= Notification.FLAG_ONLY_ALERT_ONCE|Notification.FLAG_AUTO_CANCEL;
////	            n.defaults = Notification.DEFAULT_ALL;
//	            n.tickerText = title;
//	            n.when = System.currentTimeMillis();
//            	notMgr.notify(-3, n);
//			}
			if (accept)
			{
				DownloadFile df=new DownloadFile();
				df.id=id;
				df.uptime=SystemClock.uptimeMillis();
				df.from=device;
				df.filename=packageName+mRandom.nextInt(10000)+".apk";
				df.label=label;
				df.progress=0;
				df.size=len;
				if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv prepare the download phase with id "+df.id+" "+df.filename);
				// TODO: utilisation de la carte SD ?
				df.out=mContext.openFileOutput(df.filename, Context.MODE_WORLD_READABLE);
				appendDownloadFileDescriptor(id, df);
				return id;
			}
			else
				return -1;
		}
		catch (IOException e)
		{
			if (E) Log.e(TAG_INSTALL,PREFIX_LOG+"Download file error",e);
			return -1;
		}
	}
	
	@Override
	public boolean sendFileData(int connid,final int id, byte[] data,int len,long pos,final long size,long timeout) throws RemoteException
	{
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv receive portion of file "+id);
		final DownloadFile df=getDowloadFile(id);
		try
		{
			if (df==null)
			{
				if (E) Log.e(TAG_INSTALL,"Invalid id");
				cancelFileData(connid,id,timeout);
				return false;
			}
			df.uptime=SystemClock.uptimeMillis();
			if (df.progress==0)
			{
				// Start the train of time out event to check if the download process is running
				boolean rc=Application.sHandler.postDelayed(new Runnable()
				{
					@Override
					public void run()
					{
						if (!df.finish)
						{
							long now=SystemClock.uptimeMillis();
							if ((now-df.uptime)>TIMEOUT_BETWEEN_SEND_FILE_DATA)
							{
								if (E) Log.e(TAG_INSTALL,PREFIX_LOG+"Timeout between send file data");
								removeDownloadFileDescriptor(id);
								if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv inform download "+df.id+" is finish");
								mNotifications.finishDownload(df, false);
							}
							else
							{
								if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv restart delayed message");
								Application.sHandler.postDelayed(this, TIMEOUT_BETWEEN_SEND_FILE_DATA);
							}
						}
					}
				}, TIMEOUT_BETWEEN_SEND_FILE_DATA);
				Log.d(TAG_INSTALL,PREFIX_LOG+"rc="+rc);
			}			
			
			if ((df.progress==0) && START_PROGRESS_ACTIVITY_WHEN_DOWNLOAD_APK)
			{
				Application.sHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv start activity to show download progress");
						Intent intent = new Intent(mContext,DownloadApkActivity.class);
						intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_FROM_BACKGROUND);
						intent.putExtra(Notifications.EXTRA_ID, id);
						mContext.startActivity(intent);	
					}
				});
			}
			
			df.out.write(data,0,len);
			df.progress+=len;
//			if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv notifiy download progress");
			mNotifications.updateDownload(df);
			if (DownloadApkActivity.sMe!=null)
			{
				DownloadApkActivity activity=DownloadApkActivity.sMe.get();
				if (activity!=null)
				{
					if (activity.mCancel)
					{
						activity.mCancel=false;
						activity.publishFinish();
						if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv inform dialog is finish");
						mNotifications.finishDownload(df,false);
						return false;
					}
//					if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv inform dialog progress");
					activity.publishProgress(id);
				}
			}
			return true;
		}
		catch (IOException e)
		{
			if (E) Log.e(TAG_INSTALL,PREFIX_LOG+"Unexpected error ", e);
			cancelFileData(connid,id,timeout);
			return false;
		}
	} 
	@Override
	public void cancelFileData(int connid,int fd, long timeout) throws RemoteException
	{
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv cancel file "+fd);
		final DownloadFile df=getDowloadFile(fd);
		if (df==null) return;
		try 
		{
			df.out.close();
		}
		catch (IOException e) 
		{
			// Ignore
			if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"Close error",e);
		}
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv inform notification to cancel");
		removeDownloadFileDescriptor(fd);
		mNotifications.finishDownload(df, false);
		if (DownloadApkActivity.sMe!=null)
		{
			DownloadApkActivity activity=DownloadApkActivity.sMe.get();
			if (activity!=null)
			{
				if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv inform dialog to cancel");
				activity.publishCancel();
			}
		}
	}
	// Install one at a time
	@Override
	public synchronized boolean installApk(int connid,String label,int fd,int flags,long timeout) throws RemoteException 
	{
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv installApk("+label+",...)...");
		try 
		{
			final DownloadFile df=getDowloadFile(fd);
			removeDownloadFileDescriptor(fd);	// Then, if cancel arrived, continue to install without problem
			df.finish=true;
			try
			{
				df.out.close();
			}
			catch (IOException e)
			{
				// Ignore
				if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"Close error",e);
			}
			mNotifications.finishDownload(df, true);
			if (START_PROGRESS_ACTIVITY_WHEN_DOWNLOAD_APK && DownloadApkActivity.sMe!=null)
			{
				DownloadApkActivity activity=DownloadApkActivity.sMe.get();
				if (activity!=null)
					activity.publishInstall();
			}
			if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv launch install process");
			boolean rc=installApk(label,new File(mContext.getFilesDir(),df.filename),flags,timeout);
			return rc;
		} 
		finally
		{
			if (DownloadApkActivity.sMe!=null)
			{
				DownloadApkActivity activity=DownloadApkActivity.sMe.get();
				if (activity!=null)
				{
					activity.publishFinish();
				}
			}
			if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv installApk("+label+",...) done.");
		}
	}
    public static final int INSTALL_SUCCEEDED = 1; // =PackageManager.INSTALL_SUCCEEDED
	// http://android.amberfog.com/?p=98
	private boolean installApk(final String label,final File fileName,int flags,final long timeout)
	{
		// Notes: Pour pouvoir compiler ce code, il faut patcher le android.jar d'android pour y ajouter
		// android.content.pm.IPackageInstallObserver.class et android.content.pm.PackageManager*.class
		//
		// Android permissions are separated to four groups:
		// - Regular
		// - Dangerous
		// - System or Signed
		// - Signed
		//Permissions in the first two groups can be granted to any application.
		//The last two can be obtained only by applications which are system - preinstalled in the device's firmware or which are signed with the "platform key", i.e. the same
		// adb push my-installer.apk /system/app 
		
		// If possible, install without ask the user
		// Run only if this application is installed in /system/app
		final SharedPreferences preferences=Application.getPreferences();
		boolean accept=preferences.getBoolean("accept_all", false);
		if (accept && 
				mContext.checkPermission("android.permission.INSTALL_PACKAGES", 
				android.os.Process.myPid(), 
				android.os.Process.myUid())==PackageManager.PERMISSION_GRANTED)	
		{
			// Installation in /system/apps (without user confirmation)
// FIXME: For /system/app compatibility !			
//			IPackageInstallObserver obs=new IPackageInstallObserver.Stub()
//			{
//	
//				@Override
//				public void packageInstalled(String packageName, int returnCode) throws RemoteException
//				{
//					if (D) Log.d(TAG_INSTALL,PREFIX_LOG+"packagename="+packageName+" code="+returnCode);
//					mNotifications.install(label,(returnCode==INSTALL_SUCCEEDED));
//				}
//				
//			};
//			mNotifications.autoinstall(label);
//			mContext.getPackageManager().installPackage(Uri.fromFile(fileName), obs, flags, mContext.getPackageName());
			return true;
		}
		else
		{
			// Else, ask the user to confirm the installation
			IntentFilter filter = new IntentFilter();
		    filter.addAction(Intent.ACTION_PACKAGE_ADDED);
		    filter.addAction(Intent.ACTION_PACKAGE_INSTALL);
		    filter.addDataScheme("package");
			if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv register package broadcast receiver");
		    
		    final BroadcastReceiver packageListener=new BroadcastReceiver()
			{
			    @Override
			    public void onReceive(Context context, Intent intent) 
			    {
			    	final String action=intent.getAction();
			    	if (V) Log.v(TAG_INSTALL, PREFIX_LOG+" onReceive "+action);
			    	if (Intent.ACTION_PACKAGE_ADDED.equals(action))
			    	{
						if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv inform install done");
			    		CommunicationWithLock.putResult(LOCK_WAIT_INSTALL, action);
			    	}
			    }
			};
		    try
		    {
			    Intent i=mContext.registerReceiver(packageListener, filter);
			    if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv post start install apk activity");
			    Application.sHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						InstallApkActivity.startActivity(mContext,fileName);
					}
				});
				if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"Wait LOCK_WAIT_INSTALL");
	    		String event=(String)CommunicationWithLock.getResult(LOCK_WAIT_INSTALL,timeout);
				if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Install finished with "+event);
	    		if (!CANCEL.equals(event)) // no timeout
	    		{
					if (V) Log.v(TAG_SERVER_BIND,PREFIX_LOG+"Install ok");
	    			return true;
	    		}	    			
		    } 
		    finally
		    {
				if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv unregister package broadcast receiver");
			    mContext.unregisterReceiver(packageListener);
				if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"Delete file "+fileName);
			    fileName.delete();
		    }
			if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv Install bad");
		    return false;
		}	    
	}
	
	void uninstall() // TODO: gestion de uninstall
	{
		if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv post uninstall");
		Application.sHandler.post(new Runnable()
		{
			@Override
			public void run()
			{
				if (V) Log.v(TAG_INSTALL,PREFIX_LOG+"srv start uninstall");
				Uri packageURI = Uri.parse("package:com.android.myapp");
				Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
				uninstallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_FROM_BACKGROUND);
				mContext.startActivity(uninstallIntent);
			}
		});
	}
	
    protected abstract ConnectionType getType();
}
