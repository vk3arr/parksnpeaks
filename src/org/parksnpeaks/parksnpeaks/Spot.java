package org.parksnpeaks.parksnpeaks;

import java.lang.String;
import java.util.Date;
import java.util.Locale;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class Spot {
	private int id;
	private long epoch;
	private String freq;
	private String mode;
	private String activator = "";
	private String comment = "";
	private String location = "";
	private SOTADetails sota = null;
	private KRMNPADetails krmnpa = null;
	private WWFFDetails wwff = null;
	private SANPCPADetails sanpcpa = null;
	
	public Spot(int i, String a, int e, String f, String m)
	{
		id = i;
		epoch = e;
		freq = f;
		mode = m;
		activator = a;
	}
	
	public boolean isSota()   { return sota != null;   }
	public boolean isKRMNPA() { return krmnpa != null; }
	public boolean isWWFF()   { return wwff != null;   }
	public boolean isSANPCPA(){ return sanpcpa != null;}
	
	public void setSOTA(SOTADetails s) { sota = s; }
	public void setWWFF(WWFFDetails w) { wwff = w; }
	public void setKRMNPA(KRMNPADetails k) { krmnpa = k; }
	public void setSANPCPA(SANPCPADetails k) { sanpcpa = k; }
	public void setComment(String s) { comment = s; }
	public long getEpoch() { return epoch;          }
	public int getID()     { return id;             }
	public String getActivator() { return activator; }
	
	public String getFreqMode() { return freq + "-" + mode; }
	public String getComment() { return comment; }
	public String getTime() 
	{
		long ep = epoch*1000;
		//System.out.println("spot Epoch " + epoch + " " + ep);
		
		Date d = new Date(epoch*1000);
		SimpleDateFormat df = (SimpleDateFormat)SimpleDateFormat.getTimeInstance(DateFormat.SHORT);
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		df.applyPattern("HH:mm");
		return df.format(d);
	}
	public String getDate() 
	{
		Date d = new Date(epoch*1000);
		SimpleDateFormat df = (SimpleDateFormat)SimpleDateFormat.getDateInstance(DateFormat.SHORT);
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		df.applyPattern("dd/MM");
		return df.format(d);
	}
	public String getFreq()  { return freq; }
	public String getMode()  { return mode; }
	
	public SOTADetails getSOTADetails() { return sota; }
	
	public String getExtendedDetails()
	{
		String s = new String();
		if (isSota())
			s += "S: " + sota.id + " ";
		
		if (isKRMNPA())
			s += "K: " + krmnpa.name + " ";
		
		if (isWWFF())
			s += "W: " + wwff.id + " ";
		
		if (isSANPCPA())
			s += "VK5: " + sanpcpa.name + " ";
		
		return s.trim();
	}
	
	public String toString() {
		String s = getTime() + "\t" + activator + "\t\t" + freq + "-" + mode.toUpperCase(Locale.ENGLISH) + "\n";
		
		if (isSota())
			s += sota.toString();
		
		if (isKRMNPA())
			s += krmnpa.toString();
		
		if (isWWFF())
			s += wwff.toString();
		
		return s;
	}
}