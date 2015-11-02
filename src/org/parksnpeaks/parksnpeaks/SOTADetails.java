package org.parksnpeaks.parksnpeaks;

public class SOTADetails {
   public String id;
   public String name;
   int points;
   
   public SOTADetails()
   {
	   id = "";
	   name ="";
	   points = -1;
   }
   
   public boolean isVK()
   {
	   if (id.startsWith("VK"))
		   return true;
	   
	   return false;
   }
   public SOTADetails(String id, String name, int p)
   {
	   this.id = id;
	   this.name = name;
	   points = p;
   }
   
   public String toString() {
	   return "\t\t" + id + " (" + name + ")\n";
   }
}
