package org.remoteandroid;

import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.PROBE_INTERVAL_MS;
import static org.remoteandroid.internal.Constants.PROBE_SENT;

import android.telephony.SmsMessage;

//TODO: Manage battery low !
public final class Constants
{
	public static final int REMOTE_ANDROID_VERSION=1;
	
	public static final boolean DEBUG=BuildConfig.DEBUG;

	public static final boolean LOGGER_SEVERE					=E;
	public static final boolean LOGGER_WARNING					=W;
	public static final boolean LOGGER_INFO						=false; //I
	public static final boolean LOGGER_FINE						=LOGGER_INFO;
	public static final boolean LOGGER_FINER					=LOGGER_FINE;
	public static final boolean LOGGER_FINEST					=false;//V

	public static final String TAG_DISCOVERY					="Discovery";
	public static final String TAG_MDNS							="MDNS";
	public static final String TAG_SERVER_BIND					="Server";
	public static final String TAG_CONNECT						="Connect";
	public static final String TAG_EXPOSE						="Expose";
	public static final String TAG_SMS							="Sms"; //TAG_CONNECT;
	public static final String TAG_DTMF							="DTMF"; // TAG_CONNECT
	public static final String TAG_QRCODE						="QRCode"; // TAG_CONNECT;
		
	public static final String PREFERENCES_DEVICE_ID			="deviceid";
	public static final String PREFERENCES_NAME					="name";
	public static final String PREFERENCES_BACKNAME				="backname";
	public static final String PREFERENCES_UUID					="uuid";
	public static final String PREFERENCES_PUBLIC_KEY			="public";
	public static final String PREFERENCES_PRIVATE_KEY			="private";

	public static final String PREFERENCES_EXPOSE				="expose";
	public static final String PREFERENCES_ACTIVE				="active";
	public static final String PREFERENCES_ANO_ACTIVE			="ano.active";
	public static final String PREFERENCES_ANO_WIFI_LIST		="ano.select_wifi";
	public static final String PREFERENCES_KNOWN_ACTIVE			="known.active";
	public static final String PREFERENCES_KNOWN_ACCEPT_ALL		="known.accept_all";
	
	public static final int LOCK_ASK_DOWNLOAD							=10000;
	public static final int LOCK_WAIT_INSTALL							=20000;
	public static final int LOCK_ASK_PAIRING							=30000;
	
	public static final boolean STRICT_MODE=false; 
	public static final boolean HACK_UUID=true; // FIXME: false
	
	/** True if can connect device throw internet (and not only intranet). */
	public static final boolean CONNECTION_WITH_INTERNET				=false;
    /**
     * the intent that gets sent when deleting the notifications of outbound and
     * inbound completed transfer
     */
    public static final String 	ACTION_COMPLETE_HIDE 					= "org.remoteaandroid.intent.action.HIDE_COMPLETE";
    public static final String 	ACTION_CLEAR_PROPOSED 					= "org.remoteaandroid.intent.action.CLEAR_PROPOSED";
    public static final String 	ACTION_CLEAR_DOWNLOAD 					= "org.remoteaandroid.intent.action.CLEAR_DOWNLOAD";
    
    /** Timer for refresh progression */
	public static final int		PROGRESS_TIMER=1000;

	/** Expiration delay before purge discovery device. */
	public static final long	PURGE_FRESH_DISCOVERY_MS=PROBE_INTERVAL_MS*(PROBE_SENT+1);
    /** Delay to discover others remote androids. */
	public static final long 	TIME_TO_DISCOVER						=RemoteAndroidManager.DISCOVER_BEST_EFFORT;
	/** Timeout to wait a user response when ask to validate the pairing process. */
	public static final long 	TIMEOUT_ASK_PAIR						=45000L;
	/** Timeout to wait a user response when ask to validate the pairing process. */
	public static final long 	TIMEOUT_PAIR							=20000L; // FIXME: 2000L;

	/** Auto ask pairing if refuse cookie.*/
	public static final boolean PAIR_AUTO_IF_NO_COOKIE					=true;

	public static final boolean PAIR_ANTI_SPOOF							=false; // TODO: PAIR_ANTI_SPOOF must be true
	/** Save pairing devices. */
	public static final boolean PAIR_PERSISTENT							=true;
	/** Automaticaly pairing the bounded BT devices. */
	public static final boolean PAIR_AUTO_PAIR_BT_BOUNDED_DEVICE		=true;
	/** Accept anonymous. */
	public static final boolean PAIR_CHECK_WIFI_ANONYMOUS				=true;

	
	/** Use notification to inform a new APK. Else, show a dialog. */
	public static final boolean NOTIFY_NEW_APK							=false;
    
	/** Show a progress dialog when download an APK. Else, use notification. */
	public static final boolean START_PROGRESS_ACTIVITY_WHEN_DOWNLOAD_APK=true;

	/** Show a notification when the remote android is shared. */
	public static final boolean SHOW_SERVICE_NOTIFICATION				=true;
	
	/** Show a final notification when downloads somes files. */
	public static final boolean SHOW_FINAL_NOTIF_AFTER_DOWNLOAD			=true;
	
	/** Timeout to send a bloc of data. */
	public static final long 	TIMEOUT_BETWEEN_SEND_FILE_DATA			=10000L; // 10s (10_000 ms)

    /** Timeout before expire the cookie */
	public static final long 	TIMEOUT_COOKIE							=15*60L*1000; // 15mn

	
	// Flag to describe the installer (extract from Android sources)
	public static final String EXTRA_INSTALLER_PACKAGE_NAME
    	= "android.intent.extra.INSTALLER_PACKAGE_NAME";
	
	//-----------------------------------------------------------------------------
	/** For debug, force to use fragments in horizontal view. */
	public static final boolean HACK_CONNECT_FORCE_FRAGMENTS			=false;
	
	//-----------------------------------------------------------------------------
	// --- Ethernet parameters ---
    public static boolean 		ETHERNET_CAN_RECEIVE_MULTICAST 			= true;
	/** IP Listen port to accept connection from remotes androids. */
	public static final int 	ETHERNET_LISTEN_PORT					=RemoteAndroidManager.DEFAULT_PORT;
    /** Delay to discover others remote androids. */
	public static final long 	ETHERNET_TIME_TO_DISCOVER				=(D) ? 15000L : 5000L;	// FIXME: 5000L;
	/** Socket timeout for read message. */
    public static final int 	ETHERNET_TRY_TIMEOUT					=150000; 	// FIXME Timeout for try connection
	/** Socket timeout for read message. */
    public static final int 	ETHERNET_SO_TIMEOUT						=3600000; 	// FIXME Timeout for read message
    /** Flush current data before close the socket. */
    public static final boolean ETHERNET_SO_LINGER						=true; 		// Vide les derniers paquets avant la fermeture du socket
    /** Timeout to flush the last datas. */
    public static final int 	ETHERNET_SO_LINGER_TIMEOUT				=50000; 	// FIXME Delay for flush last packets
    /** Keep the socket alive. */
    public static final boolean ETHERNET_KEEP_ALIVE						=true;		// Socket maintenu en vie, même sans trafic
	/** For some model, wait before ask mDNS service info. */
    public static final long 	HACK_ETHERNET_BEFORE_GET_MDNS_INFO_DELAY=300L; 	// Timeout before ask mDNS info (for HTC Desire)
    public static final long 	ETHERNET_WAIT_SERVICE_INFO_TIMEOUT		=1000L;
    /** True if want to reset MDNS when used change from WIFI to another WIFI network. */
    public static final boolean HACK_WIFI_CHANGED_RESTART_MDNS			=true;
    /** Invalide IP in network devices when shutdown wifi and shuton wifi quickly. */
    public static final long 	HACK_WAIT_BEFORE_RESTART_MDNS			=500;
    
	/** Timeout to receive a service info. */
    public static final long 	ETHERNET_GET_INFO_MDNS_TIMEOUT			=500L; // Timeout for try to receive mDNS infos.
    public static final long 	ETHERNET_DELAY_ANTI_REPEAT_DISCOVER		=2000L; // Timeout to refuse same UUID
    public static final boolean ETHERNET_REFUSE_LOCAL_IPV6				= true; 		// Else we must select the interface
	//-----------------------------------------------------------------------------
	// Multicast DNS service
	// http://tools.ietf.org/html/draft-cheshire-dnsext-dns-sd-10
    public final static String REMOTEANDROID_SERVICE 				= "_remoteandroid._tcp.local.";
    
	//-----------------------------------------------------------------------------
	// --- QRCode parameters ---
    public static final boolean QRCODE									=true;
	/** Show the current bitmap to analyze in overlay layer. */
	public static final boolean	QRCODE_SHOW_CURRENT_DECODE				=false;
	/** Vibrate when find QRCode. */
	public static final long QRCODE_VIBRATE_DURATION 					=100L; // 0 for no vibrate
	/** Percent of width for the square to scan QRCode. */
	public static final int QRCODE_PERCENT_WIDTH_PORTRAIT				=60;
	/** Percent of width for the square to scan QRCode. */
	public static final int QRCODE_PERCENT_WIDTH_LANDSCAPE				=75;
	public static final int QRCODE_ALPHA								=128;
	/** Encoding String to binary */
	public static final String QRCODE_BYTE_MODE_ENCODING				="ISO-8859-1";

	/** Minimal size to select. */
	public static final int QRCODE_MINIMAL_CAMERA_RESOLUTION 			= 320*240; // 240*160; // zero for maximum resolution
	public static final long QRCODE_ANIMATION_DELAY 					= 300L; // Ms
	/** When auto-focus event happend, what just a few time before take picture ? */
	public static final long QRCODE_DELAY_AFTER_AUTOFOCUS				=300;
	/** Sometime, camera.autofocus throw a RuntimeException. */
	public static final long QRCODE_DELAY_RETRY_AUTOFOCUS_IF_ERROR		=500; // Not below 100ms
	public static final int QRCODE_MAX_ERROR_AUTOFOCUS					=3; // Stop after X errors
	
	//-----------------------------------------------------------------------------
	// --- SMS parameters ---
	public static final boolean SMS									=true;
	/** Port use to receive technical message. */
	public static final short 	SMS_PORT 							=RemoteAndroidManager.DEFAULT_PORT;
	/** Timeout to wait to receive SMS. */
	public static final int 	SMS_TIMEOUT_WAIT					=60000; // 60s
	public static final int 	SMS_MESSAGE_SIZE					=SmsMessage.MAX_USER_DATA_BYTES-7;

	//-----------------------------------------------------------------------------
	// --- DTMF parameters ---
	public static final boolean SOUND								=true;
	public static final int 	DTMF_VOLUME 						= /*100*/50; // %
	public static final int 	DTMF_TIMEOUT_WAIT					=60000; // 60s
	public static final int 	DTMF_FREQUENCY_DELTA				=2; // +-2
	public static final int 	DTMF_MIN_PRESENCE_START_STOP		=1;
	public static final int 	DTMF_MIN_PRESENCE					=2;
	public static final int 	DTMF_DELAY_EMISSION					=400; // Ms
	public static final int 	DTMF_DELAY_START_STOP				=1000;

	//-----------------------------------------------------------------------------
	// --- NFC parameters ---
	public static final boolean NFC								=true;

	//-----------------------------------------------------------------------------
	// --- WIFI direct ---
	public static final boolean WIFI_DIRECT						=true;
	
	//-----------------------------------------------------------------------------
	// --- Bump ---
	public static final boolean BUMP							=true;
	
	//-----------------------------------------------------------------------------
	// --- Bluetooth ---
	public static final boolean BT								=true;
	
}
