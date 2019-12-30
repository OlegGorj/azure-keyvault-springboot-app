package com.org.cloud.api.core.utils;

import org.json.*;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.sql.Timestamp;
import java.util.Date;

public class Events {

  private  JSONArray eventsJsonArr = new JSONArray();

  //------------------------------------------------------------------------------------------
  public Events() throws JSONException {
    //this.eventsJsonArr.put( new JSONObject().put("error", "false") );
  }

  public String toString() {
    return eventsJsonArr != null ? eventsJsonArr.toString() : "[]";
  }
  public  void addEvent(String root, String val) throws JSONException {
    addEvent(root, "value", val);
  }
  public  void addEvent(String root, String key, String val) throws JSONException {
    long time = new Date().getTime();
    JSONObject jts = new JSONObject().put("timestamp", new Timestamp(time));
    eventsJsonArr.put( new JSONObject().put( root, jts.put( key, val) )  );
  }
  public  void addEvent(String root, JSONObject val) throws JSONException {
    long time = new Date().getTime();
    val.put("timestamp", new Timestamp(time));
    eventsJsonArr.put( new JSONObject().put( root, val ) );
  }


};
