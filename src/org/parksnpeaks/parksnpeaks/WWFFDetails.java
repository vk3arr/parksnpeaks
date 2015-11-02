package org.parksnpeaks.parksnpeaks;

public class WWFFDetails {
   public String id;
   public String name;
   
   public WWFFDetails() { }
   public WWFFDetails(String id, String name)
   {
	   this.id = id;
	   this.name = name;
   }
   
   public String getID()
   {
	   return id;
   }
   
   public String toString() {
	   return "" + id + " (" + name + ")\n";
   }
}
