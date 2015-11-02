package org.parksnpeaks.parksnpeaks;

import java.io.*;

import android.support.v4.widget.SwipeRefreshLayout;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.ListFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentPagerAdapter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.text.format.DateFormat;
import android.util.*;
import android.view.Gravity;
//import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageStats;



public class MainActivity extends ActionBarActivity implements ActionBar.TabListener, Runnable, SwipeRefreshLayout.OnRefreshListener {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;
    
    // four SpotFragments
    SpotFragment spotFragments[] = new SpotFragment[4];

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    ViewPager mViewPager;
    Thread 	  downloadThread;
    
    long 	  lastEpoch;
    long 	  latestEpoch;
    
    final int TAB_CNT=4;
    java.util.ArrayList<Spot> allSpots;
    java.util.ArrayList<Spot> parksSpots;
    java.util.ArrayList<Spot> peaksSpots;
    java.util.ArrayList<Spot> alerts;
    
    final int ALL=0;
    final int PEAKS=2;
    final int PARKS=1;
    final int ALERTS=3;
    boolean testMode = false;
    boolean showNonVK = false;
    int updateFreq = 5;
    int spotListLength = 12;

    SharedPreferences settings = null;
    CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    
    android.media.MediaPlayer player;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        updateFreq = Integer.parseInt(settings.getString("UpdateFrequency", "5"));
        System.out.println("Update Frequency is: " + updateFreq);
        spotListLength = Integer.parseInt(settings.getString("SpotListLength", "6"));
        System.out.println("Spot List length is: " + spotListLength);
        System.out.println(settings.getBoolean("FilterVK", true));
        showNonVK = settings.getBoolean("FilterVK", false);
        
        // return time 6 hours past (or thereabouts)
        lastEpoch = (System.currentTimeMillis()/1000) - spotListLength*3600;
        latestEpoch = 0;
        
        allSpots = new java.util.ArrayList<Spot>();
        parksSpots = new java.util.ArrayList<Spot>();
        peaksSpots = new java.util.ArrayList<Spot>();
        alerts = new java.util.ArrayList<Spot>();
        
        //getSpotListFromPnP();
        
        spotFragments[ALL] = new SpotFragment(this, allSpots);
        spotFragments[PEAKS] = new SpotFragment(this, peaksSpots);
        spotFragments[PARKS] = new SpotFragment(this, parksSpots);
        spotFragments[ALERTS] = new SpotFragment(this, alerts);
        spotFragments[ALERTS].setAlerts(true);
        
        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
    
        CookieHandler.setDefault(cookieManager);
        
        // create a Thread that pulls data down at appropriate times
        downloadThread = new Thread(this);
        try {
        	downloadThread.start();
        } catch (IllegalStateException ise) {
        	// do nothing
        }
        
       
        // When swiping between different sections, select the corresponding
        // tab. We can also use ActionBar.Tab#select() to do this if we have
        // a reference to the Tab.
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }
        });

        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
            // Create a tab with text corresponding to the page title defined by
            // the adapter. Also specify this Activity object, which implements
            // the TabListener interface, as the callback (listener) for when
            // this tab is selected.
            actionBar.addTab(
                    actionBar.newTab()
                            .setText(mSectionsPagerAdapter.getPageTitle(i))
                            .setTabListener(this));
        }
        
        player = android.media.MediaPlayer.create(getApplicationContext(), R.raw.kookaburra);
    }
    
    public void onDestroy(Bundle savedInstanceState)
    {
    	savedInstanceState.putLong("lastEpoch", lastEpoch);
    	savedInstanceState.putLong("latestEpoch", latestEpoch);
    	
    	player.release();
    }
    
    public void onPause(Bundle s)
    {
    	player.release();
    }
    
    public void onStop(Bundle s)
    {
    	player.release();
    }
    
    protected void getAlertListFromPnP() {
    	
    	XmlPullParser parser = Xml.newPullParser();
    	HttpURLConnection urlConnection = null;
    	    	
    	// connect to webpage
    	URL url;
    	try
    	{
    		if (!testMode)
    		{
    			String us = "http://www.parksnpeaks.org/app.php?cmd=alerts";
    			System.out.println("URL: " + us);
    			url = new URL(us);
    		}
    		else
    		{
    			url = new URL("http://anryan.drclaw.net/app.php?cmd=testalerts");
    		}
    		
    		urlConnection = (HttpURLConnection)url.openConnection();
    		
    		InputStream in = urlConnection.getInputStream();
        	
        	parser.setInput(in, null);
        	
        	// get the first tag
        	parser.nextTag();        	
        	parser.require(XmlPullParser.START_TAG, null, "alerts");
        
        	alerts.clear();
        	
        	while (parser.next() != XmlPullParser.END_TAG)
        	{
        		if (parser.getEventType() != XmlPullParser.START_TAG)
        			continue;
        		
        		String name = parser.getName();
        		
        		if (name.equals("alert"))
        		{
        			Spot s = readAlert(parser);
        			alerts.add(s);
        		}
        		else
        		{
        			skip(parser);
        		}
        	}

    		
    	}
    	catch (Exception e)
    	{
    		// error handling ftw!
    		System.out.println("Some kind of dodgy XML in there: " + e.getMessage());
    	}
    	finally 
    	{ 
    		//if (urlConnection != null)
    		//	urlConnection.disconnect(); 
    	}
    	

    	
    	this.runOnUiThread(new Runnable() { 
			public void run() 
			{ 
				spotFragments[ALERTS].updateAdapter(alerts);
			}  				
    	});

    }
    
    protected void getSpotListFromPnP() {
    	XmlPullParser parser = Xml.newPullParser();
    	HttpURLConnection urlConnection = null;
    	
    	settings = PreferenceManager.getDefaultSharedPreferences(this);
        updateFreq = Integer.parseInt(settings.getString("UpdateFrequency", "5"));
        System.out.println("Update Frequency is: " + updateFreq);
        spotListLength = Integer.parseInt(settings.getString("SpotListLength", "6"));
        System.out.println("Spot List length is: " + spotListLength);
        System.out.println(settings.getBoolean("FilterVK", true));
        showNonVK = settings.getBoolean("FilterVK", false);
        
    	lastEpoch = System.currentTimeMillis()/1000 - spotListLength*3600;
    	
        
    	// connect to webpage
    	URL url;
    	try
    	{
    		if (!testMode)
    		{
    			String us = "http://www.parksnpeaks.org/app.php?cmd=spots&epoch=" + Long.toString(lastEpoch);
    			System.out.println("URL: " + us);
    			url = new URL(us);
    		}
    		else
    		{
    			url = new URL("http://anryan.drclaw.net/app.php?cmd=test");
    		}
    		
    		urlConnection = (HttpURLConnection)url.openConnection();
    		
    		InputStream in = urlConnection.getInputStream();
        	
        	parser.setInput(in, null);
        	
        	// get the first tag
        	parser.nextTag();        	
        	parser.require(XmlPullParser.START_TAG, null, "spots");
        
        	// haven't seen any problems yet, so clear the view.
        	allSpots.clear();
        	peaksSpots.clear();
        	parksSpots.clear();
        	
        	while (parser.next() != XmlPullParser.END_TAG)
        	{
        		if (parser.getEventType() != XmlPullParser.START_TAG)
        			continue;
        		
        		String name = parser.getName();
        		
        		if (name.equals("spot"))
        		{
        			Spot s = readSpot(parser);
        			
        			if (showNonVK)
        				allSpots.add(s);
        			else if (s.isSota() && s.getSOTADetails().isVK())
        				allSpots.add(s);
        	    	
        			if (s.isSota())
        			{
        				if (showNonVK || s.getSOTADetails().isVK())
        					peaksSpots.add(s);
        			}
        			
        			if (s.isWWFF() || s.isKRMNPA() || s.isSANPCPA())
        				parksSpots.add(s);
        		}
        		else
        		{
        			skip(parser);
        		}
        	}

    		url = new URL("http://parksnpeaks.org/app.php?cmd=epoch");
    		urlConnection = (HttpURLConnection)url.openConnection();
    		InputStream epochStream = urlConnection.getInputStream();
    		
    		BufferedReader isr = new BufferedReader(new InputStreamReader(epochStream));
    		long tmpEp = Long.parseLong(isr.readLine());
    		
    		if (tmpEp > latestEpoch)
    		{
    			System.out.println("Playing sound");
    			latestEpoch = tmpEp;
    			playNewSpotSound();
    		}
        	
    	}
    	catch (IOException ioe)
    	{
    		System.out.println("IOException");
    	}
    	catch (Exception e)
    	{
    		// error handling ftw!
    		System.out.println("Some kind of dodgy XML in there: " + e.getMessage() + " " + e.getClass().getName());
    		this.runOnUiThread(new Runnable() { public void run() { 
    		Context context = getApplicationContext();
    		CharSequence text = "Network error!";
    		int duration = Toast.LENGTH_SHORT;

    		Toast toast = Toast.makeText(context, text, duration);
    		toast.show();
    		
    		}});
    	}
    	finally 
    	{ 
    		//if (urlConnection != null)
    		//	urlConnection.disconnect(); 
    	}
    	
    	/*if (!testMode)
    	{
    	Spot s = new Spot(131113311, "VK3ARR", 1312312414, "7.090", "ssb");
    	s.setSOTA(new SOTADetails("VK3/VT-006", "Mt St Phillack", 10));
    	s.setKRMNPA(new KRMNPADetails("0", "Baw Baw National Park"));
    	s.setWWFF(new WWFFDetails("VKFF-034", "Baw Baw National Park"));
    	
    	allSpots.add(s);
    	peaksSpots.add(s);
    	parksSpots.add(s);
    	
    	s = new Spot(131113315, "VK3HRA", 1312312420, "7.095", "ssb");
    	s.setKRMNPA(new KRMNPADetails("1", "Brisbane Ranges National Park"));
    	
    	allSpots.add(s);
    	parksSpots.add(s);
    	
    	}*/
    	
    	this.runOnUiThread(new Runnable() { 
    					public void run() 
    					{ 
    						spotFragments[PEAKS].updateAdapter(peaksSpots);
    						spotFragments[PARKS].updateAdapter(parksSpots);
    						spotFragments[ALL].updateAdapter(allSpots);
    					} 
    	});
    	
    	
    }
    
    private void playNewSpotSound() {
    	if (player != null)
    		player.start();
	}




	public /*synchronized*/ void update()
    {
    	System.out.println("Called update!\n");
    	getSpotListFromPnP();
    	getAlertListFromPnP();
    }
    
    private Spot readAlert(XmlPullParser parser) throws IOException, XmlPullParserException
    {
    	try
    	{
    		//
    		parser.require(XmlPullParser.START_TAG, null, "alert");
    	}
    	catch (Exception e)
    	{
    		
    	}
    	
    	int id = Integer.parseInt(parser.getAttributeValue(null, "id"));
    	int epoch = Integer.parseInt(parser.getAttributeValue(null, "time"));
    	//System.out.println("Spot epoch: " + epoch);
    	String activator = parser.getAttributeValue(null, "call");
    	String mode = parser.getAttributeValue(null, "mode");
    	String freq = parser.getAttributeValue(null, "freq");
    	String comments = parser.getAttributeValue(null, "comments");
    	Spot s = new Spot(id, activator, epoch, freq, mode);
    	s.setComment(comments);
    	
    	while (parser.next() != XmlPullParser.END_TAG)
    	{
    		if (parser.getEventType() != XmlPullParser.START_TAG)
    			continue;
    		
    		String name = parser.getName();
    		
    		//System.out.println("Name: " + name);
    		
    		if (name.equals("sota"))
    		{
    			// parse SOTADetails
    			SOTADetails sd = new SOTADetails();
    			sd.id = parser.getAttributeValue(null, "summit");
    			sd.name = parser.getAttributeValue(null, "name");
    			
    			s.setSOTA(sd);
    		}
    		else if (name.equals("krmnpa"))
    		{
    			// parse KRMNPA
    			KRMNPADetails kd = new KRMNPADetails();
    			//kd.id = parser.getAttributeValue(null, "id");
    			kd.name = parser.getAttributeValue(null, "name");
    			
    			s.setKRMNPA(kd);
    		}
    		else if (name.equals("wwff"))
    		{
    			// parse WWFF
    			WWFFDetails wd = new WWFFDetails();
    			wd.id = parser.getAttributeValue(null, "id");
    			wd.name = parser.getAttributeValue(null, "name");
    			
    			s.setWWFF(wd);
    		}
    		
    		parser.nextTag();
    	}
    	
    	parser.require(XmlPullParser.END_TAG, null, "alert");
    	
    	return s;
    }
    
    private Spot readSpot(XmlPullParser parser) throws IOException, XmlPullParserException
    {
    	try
    	{
    		parser.require(XmlPullParser.START_TAG, null, "spot");
    	}
    	catch(Exception e)
    	{
    		System.out.println("Erm, error here\n");
    	}
    	
    	
    	int id = Integer.parseInt(parser.getAttributeValue(null, "id"));
    	int epoch = Integer.parseInt(parser.getAttributeValue(null, "time"));
    	//System.out.println("Spot epoch: " + epoch);
    	String activator = parser.getAttributeValue(null, "call");
    	String mode = parser.getAttributeValue(null, "mode");
    	String freq = parser.getAttributeValue(null, "freq");
    	
    	Spot s = new Spot(id, activator, epoch, freq, mode);
    	String comments = parser.getAttributeValue(null, "comments");
    	s.setComment(comments);
    	
    	while (parser.next() != XmlPullParser.END_TAG)
    	{
    		if (parser.getEventType() != XmlPullParser.START_TAG)
    			continue;
    		
    		String name = parser.getName();
    		
    		System.out.println("Name: " + name);
    		
    		if (name.equals("sota"))
    		{
    			// parse SOTADetails
    			SOTADetails sd = new SOTADetails();
    			sd.id = parser.getAttributeValue(null, "summit");
    			sd.name = parser.getAttributeValue(null, "name");
    			
    			s.setSOTA(sd);
    		}
    		else if (name.equals("krmnpa"))
    		{
    			// parse KRMNPA
    			KRMNPADetails kd = new KRMNPADetails();
    			//kd.id = parser.getAttributeValue(null, "id");
    			kd.name = parser.getAttributeValue(null, "name");
    			
    			s.setKRMNPA(kd);
    		}
    		else if (name.equals("wwff"))
    		{
    			// parse WWFF
    			WWFFDetails wd = new WWFFDetails();
    			wd.id = parser.getAttributeValue(null, "id");
    			wd.name = parser.getAttributeValue(null, "name");
    			
    			s.setWWFF(wd);
    		}
    		else if (name.equals("sanpcpa"))
    		{
    			SANPCPADetails sa = new SANPCPADetails();
    			//sa.id = parser.getAttributeValue(null, "id");
    			sa.name = parser.getAttributeValue(null, "name");
    			
    			s.setSANPCPA(sa);
    		}
    		
    		parser.nextTag();
    	}
    	
    	parser.require(XmlPullParser.END_TAG, null, "spot");
    	
    	return s;
    }
    
    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
            case XmlPullParser.END_TAG:
                depth--;
                break;
            case XmlPullParser.START_TAG:
                depth++;
                break;
            }
        }
     }
    
    @Override
    public void run() {
    	
    	
    	while (true)
    	{
    		// check if we have new spots to find
    		System.out.println("Hi there\n");
    		
    		// request new set of spots
    		
    		// update spot list if necessary
    		update();
    		
    		// sleep for 5 minutes
    		try
    		{
    			//downloadThread.wait(5*60000);
    			Thread.sleep(updateFreq*60000);
    		}
    		catch (InterruptedException e)
    		{
    			// who cares?
    			System.out.println("Oh dear" + e.getMessage());
    		}
    	}
    	
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public void onActivityResult(int req, int result, Intent data)
    {
    	//
    	if (req == 1)
    	{
    		// Post Spot result
    		if (result == RESULT_OK)
    		{
    			// get the spot data from the activity
    			SpotThread s = new SpotThread(this, data);
    			try
    			{
    				s.start();
    			}
    			catch(Exception e) { }
    		}
    	}
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
    	
        int id = item.getItemId();
        if (id == R.id.action_settings) 
        {
        	System.out.println("Settings!");
        	SettingsActivity sa = new SettingsActivity(this);
        	PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(sa);
        	
        	Intent intent = new Intent(this, sa.getClass());
        	System.out.println("Settings: starting activity!");
        	System.out.flush();
        	startActivity(intent);
        	
        	return true;
        }
        else if (id == R.id.post_spot)
        {
        	System.out.println("Post spot!");
        			
        	SpotActivity sa = new SpotActivity();
        	Intent intent = new Intent(this, sa.getClass());
        	System.out.println("Spots: starting activity!");
        	System.out.flush();
        	startActivityForResult(intent, 1);
        	
        	//System.out.println("BLAHHHHH!");
        	return true;
        }
        else if (id == R.id.refresh)
        {
        	onRefresh();
        	return true;
        }
        else if (id == R.id.about)
        {
        	TextView t = (TextView)findViewById(R.id.text1);
        	t.setText("Parks n' Peaks App v0.9 beta 4 (C) Andrew Ryan VK3ARR");
        	PopupWindow p = new PopupWindow(t, 150, 150);
        	
        	View parent = findViewById(R.id.pager);
        	p.showAtLocation(parent, Gravity.CENTER, 0, 0);
        	
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        // When the given tab is selected, switch to the corresponding page in
        // the ViewPager.
    	
    	// update the ListView item with the right values
    	//if (tab.getPosition() == 0)
    	//{
    		//
    	//	System.out.println("All Spots!");
    	//}
    	
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }
    
    @Override
	public void onRefresh() {
		UpdateThread t = new UpdateThread(this);
		try {
			t.start();
		}
		catch (Exception e) { System.out.println("Cannot refresh"); }
	}
    
    public class UpdateThread extends Thread
    {
    	MainActivity activity = null;
    	public UpdateThread(MainActivity m) { activity = m; }
    	
    	@Override
    	public void run()
    	{
    		System.out.println("Thread start\n");
    		activity.update();
    	}
    }
    
    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
        		
            return spotFragments[position];
        }

        @Override
        public int getCount() {
            // Show 4 total pages.
            return 4;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Locale l = Locale.getDefault();
            switch (position) {
                case 0:
                    return getString(R.string.title_section1).toUpperCase(l);
                case 1:
                    return getString(R.string.title_section2).toUpperCase(l);
                case 2:
                    return getString(R.string.title_section3).toUpperCase(l);
                case 3:
                	return getString(R.string.title_section4).toUpperCase(l);
            }
            return null;
        }
    }

    /**
     * 
     * 
     */
    private static class SpotAdapter extends BaseAdapter
    {
    	java.util.ArrayList<Spot> spots;
    	LayoutInflater inflater = null;
    	public boolean isAlerts = false;
    	SpotAdapter(Context c, java.util.ArrayList<Spot> s)
    	{
    		spots = s;
    		inflater = (LayoutInflater)c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    	}
    	
    	@Override
    	public View getView(int position, View convertView, ViewGroup parent)
    	{
    		//inflater = (LayoutInflater)parent.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    		View cv = convertView;
    		//System.out.println("****\nPosition: " + position);
    		Spot s = spots.get(position);
    		
    		if (s == null)
    		{
    			System.out.println("Spot is null??");
    			return cv;
    		}
    		
    		
    		//if (cv == null)
    		//{
    			//System.out.println("Spot: " + s.getActivator() + " " + s.getExtendedDetails());
    			if (isAlerts)
    			{
    				cv = inflater.inflate(R.layout.alert, parent, false);
    			}
    			else if (((System.currentTimeMillis()/1000) - s.getEpoch()) <= 1800)
    			{
    				//System.out.println("\tTime: " + Long.toString(System.currentTimeMillis()/1000) + ", epoch: " + s.getEpoch() + " diff = ");
    				cv = inflater.inflate(R.layout.list_layout_recent,  parent, false);
    			}
    			else
    			{
    				//System.out.println("\tRegular spot");
    				cv = inflater.inflate(R.layout.list_layout, parent, false);
    			}
    		//}
    		
    		// update the mode 
    		TextView mode = (isAlerts) ? (TextView)cv.findViewById(R.id.alert_mode) : (TextView)cv.findViewById(R.id.mode);
    		mode.setText(s.getMode());
    		
    		TextView freq = (isAlerts) ? (TextView)cv.findViewById(R.id.alert_freq) : (TextView)cv.findViewById(R.id.freq);
    		freq.setText(s.getFreq());
    		    		
    		TextView call = (isAlerts) ? (TextView)cv.findViewById(R.id.alert_call) : (TextView)cv.findViewById(R.id.call);
    		call.setText(s.getActivator());
    		
    		TextView time = (isAlerts) ? (TextView)cv.findViewById(R.id.alert_time) : (TextView)cv.findViewById(R.id.time);
    		time.setText(s.getTime());
    		
    		TextView comment = (isAlerts) ? (TextView)cv.findViewById(R.id.alert_location) : (TextView)cv.findViewById(R.id.location);
    		comment.setText(s.getExtendedDetails());
    		
    		TextView cmnt = (isAlerts) ? (TextView)cv.findViewById(R.id.alert_comment) : (TextView)cv.findViewById(R.id.comment);
			cmnt.setText(s.getComment());
			
    		
    		if (isAlerts)
    		{
    			System.out.println("ISALERT!!");
    			TextView date = (TextView)cv.findViewById(R.id.alert_date);
    			date.setText(s.getDate());
    		}
    		
    		return cv;
    	}

		@Override
		public int getCount() {
			return spots.size();
		}

		@Override
		public Object getItem(int position) {
			
			return spots.get(position);
		}

		@Override
		public long getItemId(int position) {
			return spots.get(position).getID();
		}
    	
    	
    }
    /**
     * A placeholder fragment containing a listview.
     */
    public static class SpotFragment extends ListFragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
    	SpotAdapter spotAdapter; 
    	Context c;
    	    	
    	public SpotFragment()
    	{
    		spotAdapter = null;
    		c = null;
    	}
        
    	
        public SpotFragment(Context c, java.util.ArrayList<Spot> spots) {
        	//System.out.println("SPOTFRAGMENT const");
        	spotAdapter = new SpotAdapter(c, spots);
        	this.c = c;
        	
        	setListAdapter(spotAdapter);
        	
        }
        
        public void setAlerts(boolean a) { spotAdapter.isAlerts = a; }
        
        public void updateAdapter(java.util.ArrayList<Spot> spots)
        {
        	boolean alerts = spotAdapter.isAlerts;
        	spotAdapter = new SpotAdapter(c, spots);
        	setAlerts(alerts);
        	setListAdapter(spotAdapter);
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
                        
            return rootView;
        }
    }
	
    public static class SettingsActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    
    	SettingsFragment fragment;
    	MainActivity	 activity;
    	
    	public SettingsActivity() { }
    	public SettingsActivity(MainActivity m) { activity = m; }
    	
    	@Override
    	public void onCreate(Bundle savedInstanceState) {
    		super.onCreate(savedInstanceState);
    		System.out.println("Hello PrefAct");
    		fragment = new SettingsFragment();
        	
    		//this.startPreferenceFragment(fragment, true);
    		getFragmentManager().beginTransaction()
            .replace(android.R.id.content, fragment)
            .commit();

    		
        	
    		
    		System.out.println("Fin PrefAct onCreate");
    	}
    	public static class SettingsFragment extends PreferenceFragment {
        	//
        	@Override
        	public void onCreate(Bundle savedInstanceState)
        	{
        		super.onCreate(savedInstanceState);
        		System.out.println("Hello PrefFrag");
        		addPreferencesFromResource(R.xml.preferences);
        		System.out.println("Exit PrefFrag");
            	
        	}

        }
    	
		@Override
		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) 
		{	
			if (activity != null)
			{
				activity.onRefresh();
			}
			/*System.out.println("onSPChanged");
			System.out.flush();
			if (key.equals("UpdateFrequency"))
			{
				//
				Preference item = null;
				
				if (fragment != null)
				{
					item = fragment.findPreference(key);
				}
				else
				{
					System.out.println("fragment is null");
					System.out.flush();
					item = findPreference(key);
					
				}
				
				if (item == null)
				{
					System.out.println("Null item");
				}
				else
				{
					System.out.println("item = " + item.toString());
				}
				System.out.flush();
				item.setSummary(sharedPreferences.getString(key, ""));
			}*/		
		}    	
    }

    public class SpotThread extends Thread
    {
    	Intent data;
    	MainActivity activity;
    	
    	public SpotThread(MainActivity a, Intent d)
    	{
    		activity = a;
    		data = d;
    	}
    	
    	public void run()
    	{
    		CookieHandler.setDefault(cookieManager);
    		loginToPnP();
			System.out.println(data.getStringExtra("mode"));
			System.out.println(data.getStringExtra("location"));
			HttpURLConnection urlConnection = null;
			try
        	{
				String urlString = "cmd=post&mode=" + data.getStringExtra("mode") + "&location=" + Uri.encode(data.getStringExtra("location"));
				urlString += "&freq=" + Uri.encode(data.getStringExtra("freq")) + "&call=" + Uri.encode(data.getStringExtra("call"));
				urlString += "&type=" + Uri.encode(data.getStringExtra("type")) + "&epoch=" + Long.toString(data.getLongExtra("date", 0) / 1000);
				urlString += "&comment=" + Uri.encode(data.getStringExtra("comment")); 
				
								
        		URL url = new URL("http://www.parksnpeaks.org/app.php?" + urlString);
        		urlConnection = (HttpURLConnection)url.openConnection();
        		
        		BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
        		
        		String result = in.readLine();
        		
        		if (result.equals("Failure"))
        		{
        			//
        			System.out.println("Error posting spot!");
        		}
        		else
        		{
        			System.out.println("Result: " + result);
        		}
        		
        		activity.onRefresh();
        	}
        	catch (Exception e)
        	{
        		//
        		System.out.println("Error in postSpot::run(): " + e.getMessage());
        		System.out.println("  class: " + e.getClass().toString());
        	}
        	finally
        	{
        		//
        	}
        	
    	}
    	
        public void loginToPnP()
        {
        	HttpURLConnection urlConnection = null;
        	System.out.println("Login to PnP()");
        	
        	try
        	{
        		URL url = new URL("http://parksnpeaks.org/app.php");
        		
        		urlConnection = (HttpURLConnection)url.openConnection();
        		urlConnection.setDoOutput(true);
        		
        		//urlConnection.setChunkedStreamingMode(0);

        		BufferedOutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
        		String user = settings.getString("pnp_user", "");
        		String pass = settings.getString("pnp_pass", "");
        		String postData = "cmd=login&user_name=" + user + "&user_password=" + pass + "&user_rememberme=1&login=Log+in";
        		out.write(postData.getBytes());
        		out.flush();
        		out.close();
        		BufferedInputStream in = new BufferedInputStream(urlConnection.getInputStream());
        		while (in.available() > 0) 
        		{ 
        			System.out.print(in.read());
        			System.out.flush();
        		}
        		
        		
        		
        	}
        	catch (Exception e)
        	{
        		//
        		System.out.println("Error in loginToPnp(): " + e.getMessage());
        		System.out.println("  class: " + e.getClass().toString());
        	}
        	finally
        	{
        		//
        	}
        }
        
    }

    public static class SpotActivity extends FragmentActivity implements TimePickerDialog.OnTimeSetListener,
    				DatePickerDialog.OnDateSetListener, AdapterView.OnItemSelectedListener
    {
    	//
    	Button timeButton = null;
    	Button dateButton = null;
    	Date   spotDate;
    	
    	public static class DateFragment extends DialogFragment 
    	{
    		//
    		DatePickerDialog.OnDateSetListener odsl = null;
    		
    		public DateFragment(DatePickerDialog.OnDateSetListener o) { odsl = o; }
    		
    		public Dialog onCreateDialog(Bundle savedInstanceState)
    		{
    			final Calendar c = Calendar.getInstance();
    			c.setTimeZone(TimeZone.getTimeZone("UTC"));
    	        int day = c.get(Calendar.DAY_OF_MONTH);
    	        int month = c.get(Calendar.MONTH);
    	        int year = c.get(Calendar.YEAR);
    	        
    	        return new DatePickerDialog(getActivity(), odsl, year, month, day);
    			
    		}			
    	}
    	
    	public static class TimeFragment extends DialogFragment 
    	{
    		//
    		TimePickerDialog.OnTimeSetListener otsl = null;
    		
    		public TimeFragment(TimePickerDialog.OnTimeSetListener o) { otsl = o; }
    		
    		public Dialog onCreateDialog(Bundle savedInstanceState)
    		{
    			final Calendar c = Calendar.getInstance();
    			c.setTimeZone(TimeZone.getTimeZone("UTC"));
    	        int hour = c.get(Calendar.HOUR_OF_DAY);
    	        int minute = c.get(Calendar.MINUTE);

    			return new TimePickerDialog(getActivity(), otsl, hour, minute, DateFormat.is24HourFormat(getActivity()));
    		}			
    	}
    	
    	@Override
    	public void onCreate(Bundle savedInstanceState)
    	{
    		super.onCreate(savedInstanceState);
    		setContentView(R.layout.spot_activity);
    		
    		spotDate = new Date();
    		
    		timeButton = (Button)findViewById(R.id.spot_time);
    		dateButton = (Button)findViewById(R.id.spot_date);
    		
    		Spinner sp = (Spinner)findViewById(R.id.spot_type);
    		sp.setOnItemSelectedListener(this);
    		
    		SimpleDateFormat df = (SimpleDateFormat)SimpleDateFormat.getTimeInstance(java.text.DateFormat.SHORT);
    		df.setTimeZone(TimeZone.getTimeZone("UTC"));
    		df.applyPattern("HH:mm");
    		timeButton.setText(df.format(spotDate));
    		
    		df.applyPattern("dd/MM/yyyy");
    		dateButton.setText(df.format(spotDate));
    	}

		@Override
		public void onDateSet(DatePicker view, int year, int monthOfYear,
				int dayOfMonth) {
			// TODO Auto-generated method stub
			Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
			
			c.setTime(spotDate);
			c.set(Calendar.YEAR, year);
			c.set(Calendar.MONTH, monthOfYear);
			c.set(Calendar.DAY_OF_MONTH, dayOfMonth);
			spotDate = c.getTime();			
			
			SimpleDateFormat df = (SimpleDateFormat)SimpleDateFormat.getTimeInstance(java.text.DateFormat.SHORT);
    		df.setTimeZone(TimeZone.getTimeZone("UTC"));
    		df.applyPattern("dd/MM/yyyy");
    		
    		dateButton.setText(df.format(spotDate));
		}
		
		public void showTimePickerDialog(View v)
		{
			DialogFragment d = new TimeFragment(this);
			d.show(getFragmentManager(), "Hithere");
		}
		
		public void showDatePickerDialog(View v)
		{
			DialogFragment d = new DateFragment(this);
			d.show(getFragmentManager(), "Hithere");
		}

		@Override
		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
			// TODO Auto-generated method stub
			Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
			
			c.setTime(spotDate);
			c.set(Calendar.HOUR_OF_DAY, hourOfDay);
			c.set(Calendar.MINUTE, minute);
			spotDate = c.getTime();			
			
			SimpleDateFormat df = (SimpleDateFormat)SimpleDateFormat.getTimeInstance(java.text.DateFormat.SHORT);
    		df.setTimeZone(TimeZone.getTimeZone("UTC"));
    		df.applyPattern("HH:mm");
    		
    		timeButton.setText(df.format(spotDate));
		}
		
		public void postSpot(View v)
		{
			//
			Intent i = new Intent();
			i.putExtra("date", spotDate.getTime());
			i.putExtra("freq", ((EditText)findViewById(R.id.spot_freq)).getEditableText().toString());
			i.putExtra("mode", ((Spinner)findViewById(R.id.spot_mode)).getSelectedItem().toString());
			i.putExtra("call", ((EditText)findViewById(R.id.spot_call)).getEditableText().toString().toUpperCase());
			i.putExtra("comment", ((EditText)findViewById(R.id.spot_comment)).getEditableText().toString());
			i.putExtra("location", ((Spinner)findViewById(R.id.spot_location)).getSelectedItem().toString());
			i.putExtra("type", ((Spinner)findViewById(R.id.spot_type)).getSelectedItem().toString());
			//System.out.println("i.extra = " + i.getStringExtra("mode"));
			
			setResult(RESULT_OK, i);
			finish();
		}

		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			System.out.println("id = " + id + ", " + view.getId() + "," + parent.getClass().toString());
			if (parent.getId() == R.id.spot_type)
			{
				Spinner list = (Spinner)findViewById(R.id.spot_location);
				if (position == 1)
					list.setAdapter(ArrayAdapter.createFromResource(this, R.array.krmnpa_list_entries, R.layout.support_simple_spinner_dropdown_item));
				else if (position == 2)
					list.setAdapter(ArrayAdapter.createFromResource(this, R.array.sanpcpa_entries, R.layout.support_simple_spinner_dropdown_item));
				else
					list.setAdapter(ArrayAdapter.createFromResource(this, R.array.wwff_list_entries, R.layout.support_simple_spinner_dropdown_item));
				
				System.out.println("id = " + id + ", " + position);
			}
			
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
			// TODO Auto-generated method stub
			
		}
			
    }


}
