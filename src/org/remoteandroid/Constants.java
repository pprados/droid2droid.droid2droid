package org.remoteandroid;

import org.remoteandroid.RemoteAndroidManager;
import static org.remoteandroid.internal.Constants.*;

import android.bluetooth.BluetoothClass;

public class Constants
{
	public static final boolean DEBUG=true;

	public static final boolean LOGGER_SEVERE=false;
	public static final boolean LOGGER_WARNING=LOGGER_SEVERE;
	public static final boolean LOGGER_INFO=LOGGER_WARNING;
	public static final boolean LOGGER_FINE=LOGGER_INFO;
	public static final boolean LOGGER_FINER=LOGGER_FINE;
	public static final boolean LOGGER_FINEST=false;//LOGGER_FINER;

	public static final String TAG_DISCOVERY					="Discovery";
	public static final String TAG_SERVER_BIND					="Server";
	public static final String TAG_CONNECT						="Connect";
	public static final String TAG_EXPOSE						="Expose";
		
	public static final boolean PREFERENCES_IN_ONE_SCREEN		=true;
	
	public static final String PREFERENCES_DEVICE_ID			="deviceid";
	public static final String PREFERENCES_NAME					="name";
	public static final String PREFERENCES_BACKNAME				="backname";
	public static final String PREFERENCES_UUID					="uuid";
	public static final String PREFERENCES_PUBLIC_KEY			="public";
	public static final String PREFERENCES_PRIVATE_KEY			="private";

	public static final String PREFERENCES_ACTIVE				="active";
	public static final String PREFERENCES_ANO_ACTIVE			="ano.active";
	public static final String PREFERENCES_ANO_WIFI_LIST		="ano.select_wifi";
	public static final String PREFERENCES_KNOWN_ACTIVE			="known.active";
	public static final String PREFERENCES_KNOWN_ACCEPT_ALL		="known.accept_all";
	
	public static final int LOCK_ASK_DOWNLOAD=10000;
	public static final int LOCK_WAIT_INSTALL=20000;
	public static final int LOCK_ASK_PAIRING=30000;
	
	public static final boolean STRICT_MODE=false; 
	
    /**
     * the intent that gets sent when deleting the notifications of outbound and
     * inbound completed transfer
     */
    public static final String ACTION_COMPLETE_HIDE 			= "org.remoteaandroid.intent.action.HIDE_COMPLETE";
    public static final String ACTION_CLEAR_PROPOSED 			= "org.remoteaandroid.intent.action.CLEAR_PROPOSED";
    public static final String ACTION_CLEAR_DOWNLOAD 			= "org.remoteaandroid.intent.action.CLEAR_DOWNLOAD";
    
	/** Expiration delay before purge discovery device. */
	public long PURGE_FRESH_DISCOVERY_MS=PROBE_INTERVAL_MS*(PROBE_SENT+1);
    /** Delay to discover others remote androids. */
	public static final long TIME_TO_DISCOVER					=RemoteAndroidManager.DISCOVER_BEST_EFFORT;
	/** Timeout to wait a user response when ask to validate the pairing process. */
	public static final long TIMEOUT_ASK_PAIR					=45000L;
	/** Timeout to wait a user response when ask to validate the pairing process. */
	public static final long TIMEOUT_PAIR						=2000L;

	public static final boolean PAIR_ANTI_SPOOF					=false; // TODO: PAIR_ANTI_SPOOF must be true
	/** Save pairing devices. */
	public static final boolean PAIR_PERSISTENT					=true;
	/** Automaticaly pairing the bounded BT devices. */
	public static final boolean PAIR_AUTO_PAIR_BT_BOUNDED_DEVICE=false; // FIXME: true
	/** Accept anonymous. */
	public static final boolean PAIR_CHECK_ANONYMOUS=true;

	
	/** Use notification to inform a new APK. Else, show a dialog. */
	public static final boolean NOTIFY_NEW_APK=false;
    
	/** Show a progress dialog when download an APK. Else, use notification. */
	public static final boolean START_PROGRESS_ACTIVITY_WHEN_DOWNLOAD_APK=true;

	/** Show a notification when the remote android is shared. */
	public static final boolean MODE_WITH_SERVICE_NOTIFICATION=true;
	
	/** Show a final notification when downloads somes files. */
	public static final boolean MODE_WITH_FINAL_NOTIF=true;
	
	/** Timeout to send a bloc of data. */
	public static final long TIMEOUT_BETWEEN_SEND_FILE_DATA	=10000L; // 10s (10_000 ms)

    /** Timeout before expire the cookie */
	public static final long TIMEOUT_COOKIE				=15*60L*1000; // 15mn

	
	// Flag to describe the installer (extract from Android sources)
	public static final String EXTRA_INSTALLER_PACKAGE_NAME
    	= "android.intent.extra.INSTALLER_PACKAGE_NAME";
	
	//-----------------------------------------------------------------------------
	/** For debug, force to use fragments in horizontal view. */
	public static final boolean CONNECT_FORCE_FRAGMENTS=true;
	/** For debug, use classic exception. */ 
	public static final boolean QRCODE_DEBUG=true;
	/** Show the current bitmap to analyse. */
	public static final boolean	QRCODE_SHOW_CURRENT_DECODE	= true;
	/** Force auto-focus before analyse bitmap. */
	public static final boolean QRCODE_AUTOFOCUS=true;
	/** Re-ask autofocus after each analyse. */
	public static final boolean QRCODE_REPEAT_AUTOFOCUS=false;
	/** Interval between autofocus. */
	public static final long	QRCODE_AUTOFOCUS_INTERVAL_MS	= 2000L;
	
	
	//-----------------------------------------------------------------------------
	/** Try to connect to anonymous devices. */
	public static final boolean BT_DISCOVER_ANONYMOUS=false;
	/** Start an anonymous server ? */
	public static final boolean BT_LISTEN_ANONYMOUS=false;
	/** Discover anonymous in parallel ? */
	public static /*final*/ boolean BT_DISCOVER_ANONYMOUS_IN_PARALLELE=true; // true. False for xoom
	/** Retry x times if error with BT socket before stop.*/
	public static final int BT_MAX_LOOP_BEFORE_STOP=1;
	/** Try to connect to all known device in parallel. */
	public static /*final*/ boolean BT_DISCOVERY_IN_PARALLEL=true; // BUG sur Desire HD
	/** When the bluetooth is on, agent informe my presence. */
	public static final boolean BT_INFORM_PRESENCE=false;
	/** Try to connect to all known device in parallel. */
	public static /*final*/ boolean BT_INFORM_PRESENCE_IN_PARALLEL=true; // true
	/** Wait 2s for flush data before close BT socket. */
	public static final boolean BT_WAIT_BEFORE_CLOSE_SOCKET=true; // true. for flush last packet 

	
	public static /*final*/ long BT_HACK_DELAY_STARTUP=0L; // 300L for HTC Desire HD
	public static final boolean BT_HACK_RETRY_IF_UNABLE_TO_START_SERVICE_DISCOVERY=false;
	public static final boolean BT_HACK_WAIT_BEFORE_TRY_ANOTHER_CONNECTION=false; // Ne semble pas necessaire de toute façon
	public static final boolean BT_HACK_CREATE_LISTEN_IN_MAIN_THREAD=false;

	/** Classes of devices to try with an anonmymous BT connection. */
	public static int[] BT_DEVICE_CLASSES=
	{ 
		BluetoothClass.Device.PHONE_SMART, 
	};
	/** Major classes of devices to try with an anonmymous BT connection. */
	public static int[] BT_MAJOR_DEVICE_CLASSES=
	{
		BluetoothClass.Device.Major.COMPUTER,
	};

	//-----------------------------------------------------------------------------
	// mDNS service
	// http://tools.ietf.org/html/draft-cheshire-dnsext-dns-sd-10
    public final static String REMOTEANDROID_SERVICE = "_remoteandroid._tcp.local.";
    
    public static boolean ETHERNET_CAN_RECEIVE_MULTICAST = true;
	/** IP Listen port to accept connection from remotes androids. */
	public static final int ETHERNET_LISTEN_PORT		=RemoteAndroidManager.DEFAULT_PORT;
    /** Delay to discover others remote androids. */
	public static final long ETHERNET_TIME_TO_DISCOVER	=(D) ? 20000L : 5000L;	// FIXME: 5000L;
	/** Socket timeout for read message. */
    public static final int ETHERNET_TRY_TIMEOUT		=150000; 	// Timeout for try connection
	/** Socket timeout for read message. */
    public static final int ETHERNET_SO_TIMEOUT			=3600000; 	// Timeout for read message
    /** Flush current data before close the socket. */
    public static final boolean ETHERNET_SO_LINGER		=true; 		// Vide les derniers paquets avant la fermeture du socket
    /** Timeout to flush the last datas. */
    public static final int ETHERNET_SO_LINGER_TIMEOUT	=5000; 		// Temps pour vider les derniers paquets
    /** Keep the socket alive. */
    public static final boolean ETHERNET_KEEP_ALIVE		=true;		// Socket maintenu en vie, même sans trafic
	/** For some model, wait before ask mDNS service info. */
    public static final long ETHERNET_BEFORE_GET_MDNS_INFO_TIMEOUT	=300L; // Timeout before ask mDNS info (for HTC Desire)
	/** Timeout to receive a service info. */
    public static final long ETHERNET_GET_INFO_MDNS_TIMEOUT	=500L; // Timeout for try to receive mDNS infos.

    /** Fix bug in Xoom ? */
    public static final boolean ETHERNET_ONLY_IPV4 = true; // Xoom: No route to host
    
	/** Size of UDP buffer. */
	public static final int UDP_BUFFER_LENGTH=1024; // FIXME Buffer length pour UDP
	
}
