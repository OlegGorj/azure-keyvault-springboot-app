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
  public String toString(){
    return eventsJsonArr != null ? eventsJsonArr.toString() : "[]";
  }
  private  void addEvent(String root, String val){
    addEvent(root, "value", val);
  }
  private  void addEvent(String root, String key, String val){
    long time = new Date().getTime();
    //JSONObject jobj = new JSONObject();
    JSONObject jts = new JSONObject().put("timestamp", new Timestamp(time));
    //jts.put( key, val);
    //jobj.put( root, jts.put( key, val) );
    eventsJsonArr.put( new JSONObject().put( root, jts.put( key, val) )  );
  }
  private  void addEvent(String root, JSONObject val){
    long time = new Date().getTime();
    val.put("timestamp", new Timestamp(time));
    eventsJsonArr.put( new JSONObject().put( root, val ) );
  }


};
