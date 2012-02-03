package org.remoteandroid.discovery.ip;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.Constants.ETHERNET_DELAY_ANTI_REPEAT_DISCOVER;
import static org.remoteandroid.Constants.ETHERNET_GET_INFO_MDNS_TIMEOUT;
import static org.remoteandroid.Constants.ETHERNET_LISTEN_PORT;
import static org.remoteandroid.Constants.ETHERNET_TIME_TO_DISCOVER;
import static org.remoteandroid.Constants.ETHERNET_TRY_TIMEOUT;
import static org.remoteandroid.Constants.REMOTEANDROID_SERVICE;
import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.internal.Constants.W;

import java.io.EOFException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.jmdns.JmDNS;
import javax.jmdns.JmmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import org.remoteandroid.Application;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.binder.ip.NetSocketRemoteAndroid;
import org.remoteandroid.discovery.DiscoverAndroids;
import org.remoteandroid.internal.AbstractRemoteAndroidImpl;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.NetworkTools;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.Tools;
import org.remoteandroid.internal.socket.Channel;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.service.RemoteAndroidManagerStub;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

//TODO: bug sur unpair pendant le scan
//TODO: UPnp IGD http://teleal.org/projects/cling/

// http://code.google.com/p/android/issues/detail?id=15
public class IPDiscoverAndroids implements DiscoverAndroids
{
	// FIXME: Utiliser Jmmdns pour s'enregistrer sur toutes les interfaces http://sourceforge.net/projects/jmdns/forums/forum/324612/topic/4729651
	static class MultipleJmDNS
	{
		private ArrayList<JmDNS> mJmdns    = new ArrayList<JmDNS>();
		MultipleJmDNS(String name) throws IOException
		{
			for (Enumeration<NetworkInterface> networks=NetworkInterface.getNetworkInterfaces();networks.hasMoreElements();)
			{
				NetworkInterface network=networks.nextElement();
				if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.GINGERBREAD)
				{
					if (network.isLoopback() || !network.isUp())
						continue;
				}
				else
				{
					if (network.getName().startsWith("sit")) // vpn ?
						continue;
					if (network.getName().startsWith("dummy")) // ipv6 in ipv4
						continue;
					if (network.getName().startsWith("lo")) // ipv6 in ipv4
						continue;
				}
				for (Enumeration<InetAddress> addrs=network.getInetAddresses();addrs.hasMoreElements();)
				{
					final InetAddress addr=addrs.nextElement();
					if (ETHERNET_ONLY_IPV4 && !(addr instanceof Inet4Address))
						continue;
					if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"mDNS add "+addr);
					mJmdns.add(JmDNS.create(addr));
				}
			}
		}

		void addServiceListener(String type, ServiceListener listener)
		{
			for (int i=mJmdns.size()-1;i>=0;--i)
			{
				mJmdns.get(i).addServiceListener(type, listener);
			}
		}
		void requestServiceInfo(String type, String name, boolean persistent, long timeout)
		{
			for (int i=mJmdns.size()-1;i>=0;--i)
			{
				mJmdns.get(i).requestServiceInfo(type, name, persistent, timeout);
			}
		}
		void removeServiceListener(String type, ServiceListener listener)
		{
			for (int i=mJmdns.size()-1;i>=0;--i)
			{
				mJmdns.get(i).removeServiceListener(type, listener);
			}
		}
		void registerService(ServiceInfo info) throws IOException
		{
			for (int i=mJmdns.size()-1;i>=0;--i)
			{
				mJmdns.get(i).registerService(info.clone());
			}
		}
		void unregisterService(ServiceInfo info)
		{
			for (int i=mJmdns.size()-1;i>=0;--i)
			{
				mJmdns.get(i).unregisterAllServices(); // FIXME: 
			}
		}
		void close() throws IOException
		{
			for (int i=mJmdns.size()-1;i>=0;--i)
			{
				mJmdns.get(i).close(); 
			}
		}
	}
	public static volatile MultipleJmDNS sDNS;
	static MulticastLock sLock; 
	static volatile boolean sIsDiscovering;
	static ServiceInfo	sServiceInfo = null;

	static int sOldNetworkType=-1;
	private static BroadcastReceiver sNetworkStateReceiver=new BroadcastReceiver() 
    {
    	
        @Override
        public void onReceive(final Context context, Intent intent) 
        {
            // deprecated
            //NetworkInfo ni2=(NetworkInfo)intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
        	ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        	boolean disconnected=intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY,false);
			if (disconnected)
				asyncStopMulticastDNS();
			
            NetworkInfo ni=cm.getActiveNetworkInfo();
            if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"Network update: current="+ni);
            if (ni==null)
            {
            	asyncStopMulticastDNS();
            	sOldNetworkType=-1;
            }
            else
            {
	            switch (ni.getType())
	            {
	            	case ConnectivityManager.TYPE_MOBILE:
	            	case ConnectivityManager.TYPE_MOBILE_DUN:
	            	case ConnectivityManager.TYPE_MOBILE_HIPRI:
	            	case ConnectivityManager.TYPE_MOBILE_MMS:
	            	case ConnectivityManager.TYPE_MOBILE_SUPL:
	            	case ConnectivityManager.TYPE_WIMAX:
		            	asyncStopMulticastDNS();
		            	sOldNetworkType=ni.getType();
		            	break;
	            	case ConnectivityManager.TYPE_BLUETOOTH:
	            	case ConnectivityManager.TYPE_WIFI:
	            	case ConnectivityManager.TYPE_ETHERNET:
		            	if (ni.getState()==NetworkInfo.State.CONNECTED)
		            	{
		            		if (HACK_WIFI_CHANGED_RESTART_MDNS || sOldNetworkType!=ni.getType())
		            		{
	    						asyncStopMulticastDNS();
	    						asyncStartMulticastDNS();
		            		}
		            		else
		            		{
		            			asyncUnregisterService();
		            			asyncRegisterService();
		            		}
		            	}
		            	sOldNetworkType=ni.getType();
		            	break;
		            default:
		            	if (W) Log.w(TAG_DISCOVERY,PREFIX_LOG+"Unknown network type "+ni.getType());
		            	break;
	            		
	            }
            }
        }
    };

	static HashMap<String,Long> mPending=new HashMap<String,Long>();
	static void clearPending()
	{
		mPending.clear();
	}
    static final ServiceListener sListener=new ServiceListener()
	{
    	// For HACK_ORDER_SERVICE_RESOLVED
    	ExecutorService mSingleThread=Executors.newSingleThreadExecutor();
    	Object mLock=new Object();
    	ServiceEvent mEvent;
    	
		@Override
		public void serviceAdded(final ServiceEvent event)
		{
			if (event.getName().equals(Application.getName()))
					return;
			if (V) Log.v(TAG_MDNS,PREFIX_LOG+"IP MDNS service added "+event.getName()+", wait service info...");
			// Required to force serviceResolved to be called again (after the first search)
           	event.getDNS().requestServiceInfo(event.getType(), event.getName(), false,ETHERNET_GET_INFO_MDNS_TIMEOUT);
		}
		@Override
		public void serviceResolved(final ServiceEvent event)
		{
			if (V) Log.v(TAG_MDNS,PREFIX_LOG+"IP MDNS ... service resolved "+event.getName()+" ("+event.getInfo().getURL()+")");
			final ServiceInfo dnsInfo=event.getInfo();
			final String struuid=dnsInfo.getPropertyString("uuid");
			if (struuid==null)
				return;
			if (Application.getUUID().toString().equals(struuid))
			{
				if (V) Log.v(TAG_MDNS,PREFIX_LOG+"IP MDNS It's me. Ignore.");
				return; // It's me. Ignore.
			}
			RemoteAndroidInfoImpl info=Trusted.getBonded(struuid);
			if (!sIsDiscovering && info==null)
			{
				if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP MDNS ignore "+dnsInfo.getName()+" because is not bounded.");
				return;
			}
			// Discover a remote android. Try to connect.
			Long timeout=mPending.get(struuid);
			if (timeout!=null && System.currentTimeMillis()>timeout)
			{
				timeout=null;
			}
			if (timeout==null)
			{
				timeout=System.currentTimeMillis()+ETHERNET_DELAY_ANTI_REPEAT_DISCOVER;
				mPending.put(struuid, timeout);
				Application.sThreadPool.execute(new Runnable()
				{
					
					@Override
					public void run()
					{
						RemoteAndroidInfoImpl info=null;
						// Update the current ip address of bonded device
						RemoteAndroidInfoImpl boundedInfo=Trusted.update(UUID.fromString(struuid),dnsInfo.getInetAddresses());
						if (sIsDiscovering)
						{
							if (D) Log.d(TAG_MDNS,PREFIX_LOG+"IP MDNS Ask info for "+dnsInfo.getName()+"...");
							info=checkRemoteAndroid(dnsInfo);
							if (info!=null)
							{
								if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP Connection anonymously to "+dnsInfo.getName()+" done");
								info.isDiscoverEthernet=true;
								Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
								intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
								Application.sAppContext.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
							}
					    	else
					    		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP device "+dnsInfo.getName()+" not found now");
						}
						else
						{
							if (boundedInfo!=null)
							{
								boundedInfo.isDiscoverEthernet=true;
								if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP "+boundedInfo.getName()+" has Ips address.");
								Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
								intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, boundedInfo);
								Application.sAppContext.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
								
							}
							else
								if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP Discover "+dnsInfo.getName()+" but ignore because it's not a bounded device.");
						}
					}
				});
			}
			else
				if (D) Log.d(TAG_MDNS,PREFIX_LOG+"IP MDNS Ignore pending "+dnsInfo.getName());
		}
		
		@Override
		public void serviceRemoved(final ServiceEvent event)
		{
			if (V) Log.v(TAG_MDNS,PREFIX_LOG+"IP MDNS service removed "+event.getName());
			final String name=event.getName();
			// Discover a remote android. Try to connect.
			if (name!=null)
			{
				Application.sSingleThread.execute(new Runnable()
				{
					
					@Override
					public void run()
					{
						// Update the current ip address of bonded device, and informe others apps
						RemoteAndroidInfoImpl info=Trusted.update(name,null); // Remove IP. FIXME: BUG de null pointer exception
						if (info!=null)
						{
							if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP "+info.getName()+" is stopped.");
							info.isDiscoverEthernet=false;
							Intent intentDiscover=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
							intentDiscover.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
							Application.sAppContext.sendBroadcast(intentDiscover,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
							
						}
					}
				});
			}
		}
		
	};

    
	public IPDiscoverAndroids(Context context,
			RemoteAndroidManagerStub boss // FIXME: Est-ce utile ? Application.sDiscover à la place
			)
	{
	}
	
	public static void initIPDiscover(Context context)
	{
       	context.registerReceiver(sNetworkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}
	/**
	 * @param max 0: infinite, else number of ip to find
	 */
	@Override
	public boolean startDiscovery(final long timeToDiscover,final int flags,final RemoteAndroidManagerStub discover)
	{
		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP start discover...");
		final MultipleJmDNS dns=sDNS;
		if (dns==null)
		{
			if (W) Log.w(TAG_MDNS,PREFIX_LOG+"IP Discover refused because MDNS not started!");
			return false;
		}
	    boolean isNetwork= (NetworkTools.getActiveNetwork(Application.sAppContext) & NetworkTools.ACTIVE_LOCAL_NETWORK)!=0;
	    if (!isNetwork) 
	    {
			if (W) Log.w(TAG_MDNS,PREFIX_LOG+"IP Discover refused because network is disabled");
	    	return false;
	    }
		try
		{
			sIsDiscovering=true;
			if (D) Log.d(TAG_MDNS,PREFIX_LOG+"IP MDNS service listener...");
			dns.addServiceListener(REMOTEANDROID_SERVICE,sListener);
			long timeTo=timeToDiscover;
			if (timeTo!=RemoteAndroidManager.DISCOVER_INFINITELY)
			{
				if (timeTo==RemoteAndroidManager.DISCOVER_BEST_EFFORT)
				{
					timeTo=ETHERNET_TIME_TO_DISCOVER;
				}
				Application.sScheduledPool.schedule(new Runnable()
				{
					@Override
					public void run()
					{
						if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP MDNS delay expired.");
						cancelDiscovery(discover);
					}
				}, timeTo, TimeUnit.MILLISECONDS);
			}
		}
		catch (Exception e)
		{
			if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"IP error.",e);
			sIsDiscovering=false;
		}
		return true;
	}

	@Override
	public void cancelDiscovery(final RemoteAndroidManagerStub discover)
	{
		if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP cancel discover...");
		sIsDiscovering=false;
		clearPending();
		asyncUnregisterListener();
		discover.finishDiscover();
		if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP cancel discovery");
	}

	// ---------------------------------------------------------------------
	/**
	 * init mDNS
	 * 
	 * @see http://files.multicastdns.org/draft-cheshire-dnsext-multicastdns.txt
	 */
	private static volatile boolean isStarting=false;
	private static volatile boolean isStopping=false;
	private static synchronized void asyncStartMulticastDNS()
	{
    	if (!ETHERNET) return;
    	Application.sSingleThread.execute(new Runnable()
			{
				public void run() 
				{
					try
					{
						if (sDNS!=null || isStarting) 
						{
							if (W) Log.w(TAG_MDNS,PREFIX_LOG+"IP MDNS allready started !");
							return;
						}
						isStarting=true;
						if (D) Log.d(TAG_MDNS,PREFIX_LOG+"IP MDNS starting...");
						
						// Register with invalide IP in network devices when shutdown wifi and shuton wifi quickly
						if (HACK_WAIT_BEFORE_RESTART_MDNS!=0)
						{
							try { Thread.sleep(HACK_WAIT_BEFORE_RESTART_MDNS); } catch (InterruptedException e) {}
						}
						
					    InetAddress addr=null;
					    Inet6Address addripv6=null;
						for (Enumeration<NetworkInterface> networks=NetworkInterface.getNetworkInterfaces();networks.hasMoreElements();)
						{
							NetworkInterface network=networks.nextElement();
							if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.GINGERBREAD)
							{
								if (network.isLoopback() || network.isVirtual() || !network.isUp())
									continue;
							}
							else
							{
								if (network.getName().startsWith("sit")) // vpn ?
									continue;
								if (network.getName().startsWith("dummy")) // ipv6 in ipv4
									continue;
								if (network.getName().startsWith("lo")) // ipv6 in ipv4
									continue;
							}
							for (Enumeration<InetAddress> addrs=network.getInetAddresses();addrs.hasMoreElements();)
							{
								addr=addrs.nextElement();
								if (addr instanceof Inet6Address)
								{
									addripv6=(Inet6Address)addr;
									continue;
								}
								break; // FIXME: If IPV4 and IPV6 ?
							}
						}
						
					    // No network. Sorry
					    if (addr==null && addripv6==null)
					    {
					    	if (W) Log.w(TAG_MDNS,PREFIX_LOG+"No network !");
					    	return;
					    }
					    if (DEBUG)
					    {
							if (addr.getAddress()[0]==10)
							{
								Log.d(TAG_MDNS,"Error when change to new network");
								for (Enumeration<NetworkInterface> networks=NetworkInterface.getNetworkInterfaces();networks.hasMoreElements();)
									Log.e(TAG_MDNS,""+networks.nextElement());
							}
					    }
						final WifiManager wifi=(WifiManager)Application.sAppContext.getSystemService(Context.WIFI_SERVICE);
					    sLock = wifi.createMulticastLock("Multicast DNS");
				        sLock.setReferenceCounted(true);
				        sLock.acquire();
				
				        MultipleJmDNS dns=new MultipleJmDNS(Application.getName());
						// Add to detect binded device
						if (D) Log.d(TAG_MDNS,PREFIX_LOG+"IP MDNS Register listener");						
						dns.addServiceListener(REMOTEANDROID_SERVICE,sListener);
						if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"IP MDNS started ");
						sDNS=dns;
						isStarting=false;
						if (NetSocketRemoteAndroid.isStarted())
						{
							if (D) Log.d(TAG_MDNS,PREFIX_LOG+"IP MDNS Register service");
							registerService();
						}
					}
					catch (IOException e)
					{
						if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP MDNS start failed",e);
						isStarting=false;
					}
				}
			});
	}
	
	private static synchronized void asyncStopMulticastDNS()
	{
    	if (!ETHERNET) return;
    	Application.sSingleThread.execute(new Runnable()
			{
				public void run() 
				{
					final MultipleJmDNS dns=sDNS;
					if (sDNS==null || isStopping) 
					{
						return;
					}
					isStopping=true;
					if (D) Log.d(TAG_MDNS,PREFIX_LOG+"IP MDNS closing...");
					try
					{
						sDNS.close();
					}
					catch (IOException e)
					{
						//Ignore
						if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP MDNS closing error",e);
					}
					if (D) Log.d(TAG_MDNS,PREFIX_LOG+"IP MDNS closed");
					sDNS=null;
			        sLock.release();
					// Signal all IP of bounded device are failed
					if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"Signal all ip of bounded device are failed...");
					for (RemoteAndroidInfo info:Trusted.getBonded())
					{
						Trusted.update(info.getUuid(),null);
						Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
						intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, info);
						intent.putExtra(RemoteAndroidManager.EXTRA_UPDATE, info);
						Application.sAppContext.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
					}
					if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"Signal all ip of bounded device are failed done");
					sServiceInfo=null;
					isStopping=false;
				}

			});
	}
	
	private static void registerService()
	{
		try
		{	
			MultipleJmDNS dns=sDNS;
			if (dns!=null && sServiceInfo==null)
			{
				sServiceInfo = 
						ServiceInfo.create(REMOTEANDROID_SERVICE,Application.getName(), ETHERNET_LISTEN_PORT,"Remote android");//FIXME: variable port number
				RemoteAndroidInfo info=Trusted.getInfo(Application.sAppContext);
				Map<String,String> props=new HashMap<String,String>();
				props.put("uuid", info.getUuid().toString());
				props.put("os", info.getOs());
				props.put("osversion", Integer.toString(info.getVersion()));
				props.put("feature", Long.toString(info.getFeature()));
				props.put("raversion", Integer.toString(REMOTE_ANDROID_VERSION));
				sServiceInfo.setText(props);
				dns.registerService(sServiceInfo);
				if (D) Log.d(TAG_MDNS,PREFIX_LOG+"IP MDNS service "+Application.getName()+REMOTEANDROID_SERVICE+" registered");
			}
		}
		catch (IOException e)
		{
			if (W) Log.w(TAG_MDNS,PREFIX_LOG+"Problem when register service",e);
		}
		
	}
	public static void asyncUnregisterService()
	{
		Application.sSingleThread.execute(new Runnable()
		{
			@Override
			public void run()
			{
				final MultipleJmDNS dns=sDNS;
				if (dns!=null && sServiceInfo!=null)
				{
					if (D) Log.d(TAG_MDNS,PREFIX_LOG+"Unregister service");						
					dns.unregisterService(sServiceInfo);
					sServiceInfo=null;
					if (D) Log.d(TAG_MDNS,PREFIX_LOG+"IP MDNS service "+Application.getName()+REMOTEANDROID_SERVICE+" unregistered");
				}
				else
				{
					if (W) Log.w(TAG_MDNS,PREFIX_LOG+"IP MDNS allready unregistered !");
				}
			}
		});
	}
	public static void asyncRegisterService()
	{
		Application.sSingleThread.execute(new Runnable()
		{
			@Override
			public void run()
			{
				registerService();
			}
		});
	}
	
	private static void asyncUnregisterListener()
	{
		Application.sSingleThread.execute(new Runnable()
		{
			@Override
			public void run()
			{
				final MultipleJmDNS dns=sDNS;
				if (dns!=null && sServiceInfo!=null)
				{
					dns.removeServiceListener(REMOTEANDROID_SERVICE, sListener);
					if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP MDNS remove listener");
				}
				else
				{
					if (W) Log.w(TAG_MDNS,PREFIX_LOG+"IP MDNS listener allready unregistered !");
				}
			}
		});
	}
	
	private static RemoteAndroidInfoImpl checkRemoteAndroid(ServiceInfo dnsinfo)
	{
		if ("remoteandroid".equals(dnsinfo.getApplication()) &&
				!Application.getName().equals(dnsinfo.getName())
		   )
		{	
			String[] urls=dnsinfo.getURLs("tcp");
			for (int i=0;i<urls.length;++i)
			{
				String url=urls[i];
				int nb=0;
				int idx=-1;
				while ((idx=url.indexOf(':',idx+1))!=-1)
					++nb;
				if (nb>2) // It's ipv6
				{
					int last=url.lastIndexOf(':');
					url=url.substring(0,6)+ // tcp://
							'['+
							url.substring(6,last)+
							']'+
							url.substring(last);
					urls[i]=url;
				}
			}
			if (urls.length>0)
			{
				String uri=urls[0];
				try
				{
					return tryConnectForDiscovering(uri);
				}
				catch (Exception e)
				{
					if (Application.hackNullException(e)!=null)
						e.printStackTrace(); // TODO
				}
			}
			else
				if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"IP not found url "+dnsinfo);
		}
		return null;
	}
	
	// Try to connect to remote android with IP
	private static RemoteAndroidInfoImpl tryConnectForDiscovering(String uri)
		throws SecurityException
	{
		Socket socket=null;
		try
		{
			if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP Try to connect to "+uri+"...");
			final Uri u=Uri.parse(uri);
			final InetAddress address=InetAddress.getByName(Tools.uriGetHostIPV6(u));
			int port=Tools.uriGetPortIPV6(u);
			if (port==-1) port=RemoteAndroidManager.DEFAULT_PORT;
			socket=new Socket(address,port);
			socket.setTcpNoDelay(true);
			socket.setSoTimeout(ETHERNET_TRY_TIMEOUT); // FIXME: Pas toujours ethernet !
			if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP device "+uri+" connected. Ask info...");
			// Ask remote android info
			final long threadid = Thread.currentThread().getId();
			Msg msg = Msg.newBuilder()
				.setType(Type.CONNECT_FOR_DISCOVERING)
				.setThreadid(threadid)
				.setIdentity(ProtobufConvs.toIdentity(Application.sDiscover.getInfo()))
				.build();
			Channel.writeMsg(msg, socket.getOutputStream());
	
			// Read protobuf message
			byte[] buf=new byte[Channel.INITIAL_BUFSIZE];
			int ss=socket.getInputStream().read(buf,0,4);
			if (ss!=4)
			{
				throw new EOFException("Communication closed");
			}
			int length=(buf[0] << 24)
	        	+ ((buf[1] & 0xFF) << 16)
	        	+ ((buf[2] & 0xFF) << 8)
	        	+ (buf[3] & 0xFF);
			if (length<0)
				return null; // Hack protocol ?
			if (length+4>buf.length)
			{
				buf=new byte[length+4];
			}
			int pos=4;
			int l=length;
			do
			{
				int s=socket.getInputStream().read(buf,pos,l);
				if (s==-1)
					throw new EOFException();
				pos+=s;
				l-=s;
			} while (l>0);
			Msg resp=(Msg)Channel.sPrototype.newBuilderForType().mergeFrom(buf, 4, length).build();
			if (resp.getRc())
			{
				if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP device "+uri+" return info.");
				RemoteAndroidInfoImpl info = ProtobufConvs.toRemoteAndroidInfo(Application.sAppContext,resp.getIdentity());
				info.isDiscoverEthernet=true;
				info.isBonded=Trusted.isBonded(info);
				// I find it !
				//if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"IP Device "+info.getName()+" found ("+uri+")");
				return info;
			}
			else
			{
				if (resp.getStatus()==AbstractRemoteAndroidImpl.STATUS_REFUSE_ANONYMOUS)
				{
					throw new SecurityException(); // FIXME: Meilleur message ou type ?
				}
				return null;
			}
		}
		catch (final Exception e)
		{
			if (E && !D) Log.e(TAG_DISCOVERY,PREFIX_LOG+"IP Device "+uri+" error ("+e.getMessage()+")");
			if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP Device "+uri+" error",e);
			if (D) 
			{
				Application.sHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						Toast.makeText(Application.sAppContext, e.getMessage(), Toast.LENGTH_LONG).show();
					}
				});
			}
			return null;
		}
		finally
		{
			try
			{
				if (socket!=null)
					socket.close();
				if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP device "+uri+" connection closed.");
			}
			catch (IOException e)
			{
				// Ignore
				if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP Error when close socket",e);
			}
		}
	}

}
