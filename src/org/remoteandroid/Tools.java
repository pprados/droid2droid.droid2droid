package org.remoteandroid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Tools
{
	public static double getBogoMips()
	{
		BufferedReader in=null;
		try
		{
			ProcessBuilder pb = new ProcessBuilder("/system/bin/cat", "/proc/cpuinfo");
			Process process = pb.start();
			in = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			while ((line=in.readLine())!=null)
			{
				if (line.startsWith("BogoMIPS"))
				{
					return Double.parseDouble(line.substring(line.indexOf(':')+1));
				}
			}
		}
		catch (Exception e)
		{
			// Ignore
		}
		finally
		{
			if (in!=null)
			{
				try
				{
					in.close();
				}
				catch (IOException e)
				{
					// Ignore
				}
			}
			
		}
		return 0.0;
	}

	public static long getCPUFreq()
	{
		BufferedReader in=null;
		try
		{
			ProcessBuilder pb = new ProcessBuilder("/system/bin/cat", "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
			Process process = pb.start();
			in = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			return Long.parseLong(in.readLine());
		}
		catch (Exception e)
		{
			// Ignore
		}
		finally
		{
			if (in!=null)
			{
				try
				{
					in.close();
				}
				catch (IOException e)
				{
					// Ignore
				}
			}
			
		}
		return 0;
	}


}
