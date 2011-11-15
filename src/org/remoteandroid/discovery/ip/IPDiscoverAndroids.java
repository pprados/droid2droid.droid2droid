package org.remoteandroid.discovery.ip;

import static org.remoteandroid.Constants.ETHERNET_BEFORE_GET_MDNS_INFO_TIMEOUT;
import static org.remoteandroid.Constants.ETHERNET_GET_INFO_MDNS_TIMEOUT;
import static org.remoteandroid.Constants.ETHERNET_LISTEN_PORT;
import static org.remoteandroid.Constants.ETHERNET_TIME_TO_DISCOVER;
import static org.remoteandroid.Constants.ETHERNET_TRY_TIMEOUT;
import static org.remoteandroid.Constants.REMOTEANDROID_SERVICE;
import static org.remoteandroid.Constants.TAG_DISCOVERY;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.PREFIX_LOG;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.internal.Constants.W;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import org.remoteandroid.Application;
import org.remoteandroid.ConnectionType;
import org.remoteandroid.RemoteAndroidInfo;
import org.remoteandroid.RemoteAndroidManager;
import org.remoteandroid.discovery.DiscoverAndroids;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.Messages.Type;
import org.remoteandroid.internal.AbstractRemoteAndroidImpl;
import org.remoteandroid.internal.ProtobufConvs;
import org.remoteandroid.internal.RemoteAndroidInfoImpl;
import org.remoteandroid.internal.socket.Channel;
import org.remoteandroid.internal.Tools;
import org.remoteandroid.pairing.Trusted;
import org.remoteandroid.service.RemoteAndroidManagerStub;
import org.remoteandroid.service.RemoteAndroidService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.util.Log;

//TODO: J'ai un bug si le process commence sans avoit démarré l'agent. Dans ce cas, ce n'est pas découvert.
//TODO: bug sur unpair pendant le scan

// http://code.google.com/p/android/issues/detail?id=15
public class IPDiscoverAndroids implements DiscoverAndroids
{
	private Context mApplicationContext;
	public static volatile JmDNS sDNS;
	static MulticastLock sLock; 

	
	private static BroadcastReceiver sNetworkStateReceiver=new BroadcastReceiver() 
    {
    	
        @Override
        public void onReceive(final Context context, Intent intent) 
        {
            if (W) Log.w(TAG_DISCOVERY, PREFIX_LOG+"Network Type Changed "+intent);
            
            NetworkInfo ni=(NetworkInfo)intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if (ni.getType()==ConnectivityManager.TYPE_WIFI && ni.getState()==NetworkInfo.State.CONNECTED)
            {
            	Application.sThreadPool.execute(new Runnable()
					{
						public void run() 
						{
							startMulticastDNS(context);
						}
					});
            }
            else
            {
            	Application.sThreadPool.execute(new Runnable()
					{
						public void run() 
						{
			            	stopMulticastDNS();
						}
					});
            }
        }
    };

    static final ServiceListener sListener=new ServiceListener()
	{
		
		@Override
		public void serviceResolved(final ServiceEvent event)
		{
			if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP Multicast DNS ... service resolved "+event.getName()+" ("+event.getInfo().getURL()+")");
			final ServiceInfo dnsInfo=event.getInfo();
			final String struuid=dnsInfo.getPropertyString("uuid");
			if (struuid==null)
				return;
			if (Application.getUUID().toString().equals(struuid))
				return; // It's me. Ignore.
			
			// Discover a remote android. Try to connect.
			Application.sThreadPool.execute(new Runnable()
			{
				
				@Override
				public void run()
				{
					RemoteAndroidInfoImpl info=null;
					// Update the current ip address of bonded device
					RemoteAndroidInfoImpl boundedInfo=Trusted.update(UUID.fromString(struuid),dnsInfo.getInetAddresses());
					if (Application.sDiscover.isDiscovering())
					{
						if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP Multicast DNS Ask info for "+dnsInfo.getName()+"...");
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
							if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP "+boundedInfo.getName()+" has the Ip address "+boundedInfo.getInetAddresses());
							Intent intent=new Intent(RemoteAndroidManager.ACTION_DISCOVER_ANDROID);
							intent.putExtra(RemoteAndroidManager.EXTRA_DISCOVER, boundedInfo);
							Application.sAppContext.sendBroadcast(intent,RemoteAndroidManager.PERMISSION_DISCOVER_RECEIVE);
							
						}
					}
				}
			});
		}
		
		@Override
		public void serviceRemoved(final ServiceEvent event)
		{
			if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP Multicast DNS service removed "+event.getName());
			final String name=event.getName();
			// Discover a remote android. Try to connect.
			if (name!=null)
			{
				Application.sThreadPool.execute(new Runnable()
				{
					
					@Override
					public void run()
					{
						// Update the current ip address of bonded device, and informe others apps
						RemoteAndroidInfoImpl info=Trusted.update(name,null); // Remove IP
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
		
		@Override
		public void serviceAdded(final ServiceEvent event)
		{
			if (event.getName().equals(Application.getName()))
					return;
			if (V) Log.v(TAG_DISCOVERY,PREFIX_LOG+"IP Multicast DNS service added "+event.getName()+", wait service info...");
			// Required to force serviceResolved to be called again (after the first search)
			final JmDNS dns=sDNS;
            if (dns!=null)
            {
				Application.sThreadPool.execute(new Runnable()
				{
					
					@Override
					public void run()
					{
						// FIXME: Suivant les telephones, le request service info ne retourne rien. Cas du Samsung I7, HTC HD 
						if (ETHERNET_BEFORE_GET_MDNS_INFO_TIMEOUT!=0)
						{
							try
							{
								Thread.sleep(ETHERNET_BEFORE_GET_MDNS_INFO_TIMEOUT);
							} catch (Exception e ) {}
						}
		            	sDNS.requestServiceInfo(event.getType(), event.getName(), false,ETHERNET_GET_INFO_MDNS_TIMEOUT);					}
				});
            }
		}
	};

    
	public IPDiscoverAndroids(Context context,
			RemoteAndroidManagerStub boss // FIXME: Est-ce utile ? Application.sDiscover à la place
			)
	{
		mApplicationContext=context.getApplicationContext();
	}
	
	public static void initIPDiscover(Context context)
	{
       	context.registerReceiver(sNetworkStateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
	}
	/**
	 * init mDNS
	 * 
	 * @see http://files.multicastdns.org/draft-cheshire-dnsext-multicastdns.txt
	 */
	
	private static void startMulticastDNS(final Context context)
	{
		if (Compatibility.VERSION_SDK_INT>=Compatibility.VERSION_DONUT)
		{
			// Verify wrapper
			new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
			
						if (sDNS!=null) return;
						WifiManager wifi=(WifiManager)context.getSystemService(Context.WIFI_SERVICE);
					    sLock = wifi.createMulticastLock("Multicast DNS");
				        sLock.setReferenceCounted(true);
				        sLock.acquire();
				
					    int intaddr = wifi.getConnectionInfo().getIpAddress();
					    byte[] byteaddr = new byte[] { (byte) (intaddr & 0xff), (byte) (intaddr >> 8 & 0xff),
					               (byte) (intaddr >> 16 & 0xff), (byte) (intaddr >> 24 & 0xff) };
					    InetAddress addr = InetAddress.getByAddress(byteaddr);
					    JmDNS dns=JmDNS.create(addr,Application.getName());
						//sDNS=JmDNS.create();
						if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP Init Multicast DNS done ");
						sDNS=dns;
						if (RemoteAndroidService.isActive())
							registerService();
						// Add to detect binded device
						dns.addServiceListener(REMOTEANDROID_SERVICE,sListener);
					}
					catch (IOException e)
					{
						if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"IP Impossible to start Multicast DNS",e);
					}
				}
			}.run();
		}
	}
	
	public static void stopMulticastDNS()
	{
		JmDNS dns=sDNS;
		if (dns!=null)
		{
			sDNS=null;
			try
			{
				dns.unregisterAllServices();
				dns.removeServiceListener(REMOTEANDROID_SERVICE,sListener);
				dns.close();
			}
			catch (IOException e)
			{
				if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"IP Multicast DNS close error",e);
			}
			if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"IP Multicast DNS closed");
		}
	}
	static ServiceInfo	sServiceInfo = null;
	public static void registerService()
	{
		final JmDNS dns=sDNS;
		if (dns!=null)
		{
			try
			{
				sServiceInfo = 
						ServiceInfo.create(REMOTEANDROID_SERVICE,Application.getName(), ETHERNET_LISTEN_PORT,"Remote android");
				RemoteAndroidInfo info=Trusted.getInfo(Application.sAppContext, ConnectionType.ETHERNET);
				Map<String,String> props=new HashMap<String,String>();
				props.put("uuid", info.getUuid().toString());
				props.put("os", info.getOs());
				props.put("version", Integer.toString(info.getVersion()));
				props.put("capability", Integer.toString(info.getCapability()));
				sServiceInfo.setText(props);
				dns.registerService(sServiceInfo);
				if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP Multicast DNS service "+Application.getName()+REMOTEANDROID_SERVICE+" registered");
			}
			catch (IOException e)
			{
				if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"IP Multicast DNS impossible to register service.",e);
			}
		}
	}
	public static void unregisterService()
	{
		final JmDNS dns=sDNS;
		if (dns!=null && sServiceInfo!=null)
		{
			dns.unregisterService(sServiceInfo);
			sServiceInfo=null;
			if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP Multicast DNS service "+Application.getName()+REMOTEANDROID_SERVICE+" unregistered");
		}
	}
	
	/**
	 * @param max 0: infinite, else number of ip to find
	 */
	@Override
	public boolean startDiscovery(long timeToDiscover,final int flags,final RemoteAndroidManagerStub discover)
	{
		final JmDNS dns=sDNS;
		if (dns==null)
		{
			return false;
		}
		if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP start discovery");
	    boolean isNetwork= 
	          (((ConnectivityManager) mApplicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo()!=null);
		    if (!isNetwork) 
		    {
				if (I) Log.i(TAG_DISCOVERY,PREFIX_LOG+"IP disabled");
		    	return false;
		    }
		// Manage knowns device
		Application.sThreadPool.execute(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						JmDNS dns=sDNS;
						if (dns!=null)
						{
							if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP Multicast DNS service listener");
							dns.addServiceListener(REMOTEANDROID_SERVICE,sListener);
						}
					}
					catch (Exception e)
					{
						if (E) Log.e(TAG_DISCOVERY,PREFIX_LOG+"IP error.",e);
						// TODO
					}
				}
	
			});
		if (timeToDiscover!=RemoteAndroidManager.DISCOVER_INFINITELY)
		{
			if (timeToDiscover==RemoteAndroidManager.DISCOVER_BEST_EFFORT)
			{
				timeToDiscover=ETHERNET_TIME_TO_DISCOVER;
			}
			Application.sScheduledPool.schedule(new Runnable()
			{
				@Override
				public void run()
				{
					cancelDiscovery(discover);
				}
			}, timeToDiscover, TimeUnit.MILLISECONDS);
		}
		return true;
	}

	private static RemoteAndroidInfoImpl checkRemoteAndroid(ServiceInfo dnsinfo)
	{
		if ("remoteandroid".equals(dnsinfo.getApplication()) &&
				!Application.getName().equals(dnsinfo.getName())
		   )
		{	
			String[] urls=dnsinfo.getURLs("tcp");
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
	
	@Override
	public void cancelDiscovery(final RemoteAndroidManagerStub discover)
	{
		JmDNS DNS=sDNS;
		if (DNS!=null)
		{
			DNS.removeServiceListener(REMOTEANDROID_SERVICE, sListener);
			if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP Multicast DNS stop service listener");
		}
		discover.finishDiscover();
		if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP cancel discovery");
	}

	// Try to connect to remote android with IP
	public static RemoteAndroidInfoImpl tryConnectForDiscovering(String uri)
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
				.setIdentity(ProtobufConvs.toIdentity(Application.sDiscover.getInfo(ConnectionType.BT)))
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
				RemoteAndroidInfoImpl info = ProtobufConvs.toRemoteAndroidInfo(resp.getIdentity());
				info.address=address;
				info.isDiscoverEthernet=true;
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
		catch (Exception e)
		{
			if (E && !D) Log.e(TAG_DISCOVERY,PREFIX_LOG+"IP Device "+uri+" error ("+e.getMessage()+")");
			if (D) Log.d(TAG_DISCOVERY,PREFIX_LOG+"IP Device "+uri+" error",e);
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
