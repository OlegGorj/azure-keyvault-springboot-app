package com.org.cloud.api.core.service;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.keyvault.KeyPermissions;
import com.microsoft.azure.management.keyvault.SecretPermissions;
import com.microsoft.azure.management.keyvault.Vault;
import com.microsoft.azure.management.keyvault.AccessPolicy;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext;
import com.microsoft.rest.LogLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
//import com.microsoft.azure.shortcuts.resources.Region;
import java.sql.Timestamp;
import java.util.Date;
import org.json.*;
import java.io.File;

import com.org.cloud.api.core.utils.KubernetisClientUtil;
import com.org.cloud.api.core.utils.Utils;

public final class ManageKeyVault {

  private static String ResourceGroupName = "";     // resource group name
  private static String RegionName = "";            // region name
  private static String clientId = "";              // service principal
  private static Azure azure;
  private static JSONArray eventsJsonArr = new JSONArray();


  private void init(){
    System.out.println("Env var AZURE_AUTH_LOCATION: " + System.getenv("AZURE_AUTH_LOCATION"));
    this.eventsJsonArr.put( new JSONObject().put("error", "false") );
    addEvent("error", "false");
  }
  //------------------------------------------------------------------------------------------
  public String getEvents(){
    return eventsJsonArr != null ? eventsJsonArr.toString() : "[]";
  }
  private static void addEvent(String root, String val){
    addEvent(root, "value", val);
  }
  private static void addEvent(String root, String key, String val){
    long time = new Date().getTime();
    //JSONObject jobj = new JSONObject();
    JSONObject jts = new JSONObject().put("timestamp", new Timestamp(time));
    //jts.put( key, val);
    //jobj.put( root, jts.put( key, val) );
    eventsJsonArr.put( new JSONObject().put( root, jts.put( key, val) )  );
  }
  private static void addEvent(String root, JSONObject val){
    long time = new Date().getTime();
    val.put("timestamp", new Timestamp(time));
    eventsJsonArr.put( new JSONObject().put( root, val ) );
  }

  //------------------------------------------------------------------------------------------
  public void setResourceGroup(String resGroup){
    this.ResourceGroupName = resGroup;
    addEvent("setResourceGroup",resGroup);
  }
  public void setRegion(String region){
    this.RegionName = region;
    addEvent("setRegion",region);
  }

  //------------------------------------------------------------------------------------------
  // constructor
  public ManageKeyVault() {
    init();
  }
  public ManageKeyVault(String resourcegroup) {
    init();
    setResourceGroup(resourcegroup);
  }
  public ManageKeyVault(String resourcegroup, String region) {
    init();
    setResourceGroup(resourcegroup);
    setRegion(region);
  }

  // authenticate
  private static Azure Authenticate() throws Exception {
    String fauth = "/Users/oleg.gorodnitchiibm.com/my.azureauth";
    System.out.println("Reading auth file: " + fauth);
    final File credFile = new File(fauth);

    azure = Azure.configure()
            .withLogLevel(LogLevel.BASIC)
            .authenticate(credFile)
            .withDefaultSubscription();
    addEvent("Selected subscriptionId", "value", azure.subscriptionId() );

    clientId = ApplicationTokenCredentials.fromFile(credFile).clientId();
    addEvent("Selected clientId", "value", clientId );

    return azure;
  }

  // List key vaults
  public static String listKeyVaults() {
    String ret = "";
    try {
          // Authenticate
          Azure azure = Authenticate();
          // Print selected subscription
          System.out.println("Selected subscription: " + azure.subscriptionId() );

          JSONArray jsonArray = new JSONArray();
          JSONObject jsonObj = new JSONObject();

          try {
              System.out.println("Listing key vaults...");

              for (Vault vault : azure.vaults().listByResourceGroup( ResourceGroupName )) {
                  Utils.print(vault);
                  jsonObj = new JSONObject();
                  jsonObj.put("keyvault-id", vault.id() );
                  jsonObj.put("keyvault-name", vault.name() );
                  jsonObj.put("resource-group-name", vault.resourceGroupName());
                  jsonObj.put("region", vault.region() );
                  jsonObj.put("keyvault-uri", vault.vaultUri() );

                  JSONObject accessPolicyJsonObj = new JSONObject();
                  for (AccessPolicy accessPolicy : vault.accessPolicies()) {
                      accessPolicyJsonObj.put("access-policy", accessPolicy.objectId() );
                      accessPolicyJsonObj.put("keys-permissions", Joiner.on(", ").join(accessPolicy.permissions().keys()) );
                      accessPolicyJsonObj.put("secrets-policy", Joiner.on(", ").join(accessPolicy.permissions().secrets()) );
                  }

                  jsonObj.put("access-policies", accessPolicyJsonObj );

                  jsonArray.put ( jsonObj );
              }

              System.out.println("Lising vaults: " + jsonArray );

          } catch (Exception e) {
              System.err.println(e.getMessage());
              JSONObject json = new JSONObject();
              jsonObj.put("error", e.getMessage() );
              return json.toString();
          }
          return jsonArray != null ? jsonArray.toString() : "[]";

    } catch (Exception e) {
          ret =  e.getMessage();
          System.out.println(e.getMessage());
          e.printStackTrace();
    }
    return ret;
  }

  // create the vault
  public static String createKeyVault(String resourcegroup, String rg, String kvname) {

    Region r;
    try{

      if ( rg == null || rg.isEmpty() ) {
        r = Region.US_EAST; // defaulting to US_EAST
        addEvent("setRegion", "action", "setting to default US_EAST");
      }else{
        r = Region.fromName(rg);
        addEvent("setRegion", "action", "setting to " + rg);
      }
    }catch(Exception e){
      System.err.println(e.getMessage());
      addEvent("error-msg", e.getMessage());
      //addEvent("error", "true");
      return new JSONObject().put("error", e.getMessage()).toString();
    }

    try {
      // Authenticate
      Azure azure = Authenticate();
      // Print selected subscription
      System.out.println("Selected subscription: " + azure.subscriptionId() );

        // Create a key vault with non-empty access policy
        // and authorize an application

        System.out.println("Creating a key vault...");
        Vault vault1 = azure.vaults().define(kvname)
                .withRegion(r)
                .withNewResourceGroup(resourcegroup)
                .defineAccessPolicy()
                .forServicePrincipal( clientId )
                .allowKeyPermissions( KeyPermissions.GET )
                .allowKeyPermissions( KeyPermissions.LIST )
                .allowSecretPermissions( SecretPermissions.GET )
                .allowSecretPermissions( SecretPermissions.LIST )
                .attach()
                .create();

        JSONObject json = new JSONObject();
          json.put("name", vault1.name());
          json.put("resource-group", vault1.resourceGroupName());
          json.put("region", vault1.region());
          json.put("vault-uri", vault1.vaultUri());
        JSONArray accessPolicyJSON = new JSONArray();
        for (AccessPolicy accessPolicy : vault1.accessPolicies()) {
          JSONObject jsonAP = new JSONObject();
            jsonAP.put("identity", accessPolicy.objectId());
            jsonAP.put("key-permissions", Joiner.on(", ").join(accessPolicy.permissions().keys())  );
            jsonAP.put("secret-permission", Joiner.on(", ").join(accessPolicy.permissions().secrets()));
            accessPolicyJSON.put(jsonAP);
        }
        json.put("access-policy", accessPolicyJSON);
        addEvent("createKeyVault", json );
        System.out.println("Created key vault");
        //Utils.print(vault1);
/*
        // Authorize an application
        System.out.println("Authorizing the application associated with the current service principal...");
        vault1 = vault1.update()
                .defineAccessPolicy()
                .forServicePrincipal( clientId )
                .allowKeyPermissions( KeyPermissions.GET )
                .allowKeyPermissions( KeyPermissions.LIST )
                .allowSecretPermissions( SecretPermissions.GET )
                .allowSecretPermissions( SecretPermissions.LIST )
                .attach()
                .apply();

        addEvent("updateKeyVault", "update keyvault", kvname );
        System.out.println("Updated key vault");
        Utils.print(vault1);
*/
        // Update a key vault
        System.out.println("Update a key vault to enable deployments and add permissions to the application...");
        vault1 = vault1.update()
                .withDeploymentEnabled()
                .withTemplateDeploymentEnabled()
                .updateAccessPolicy( vault1.accessPolicies().get(0).objectId() )
                .allowSecretPermissions( SecretPermissions.GET )
                .allowSecretPermissions( SecretPermissions.LIST )
                .parent()
                .apply();

        addEvent("updateKeyVault", "update keyvault", kvname );
        System.out.println("Updated key vault");
        // Print the network security group
        Utils.print(vault1);

      } catch (Exception e) {
          System.err.println(e.getMessage());
      }

    return "";
  }

  /*
  public static boolean runEtoE(Azure azure, String clientId) {
      final String vaultName1 = SdkContext.randomResourceName("vault1", 20);
      //final String vaultName2 = SdkContext.randomResourceName("vault2", 20);
      final String  ResourceGroupName  = SdkContext.randomResourceName("rgNEMV", 24);

      try {
          // Create a key vault with empty access policy
          System.out.println("Creating a key vault...");
          Vault vault1 = azure.vaults().define(vaultName1)
                  .withRegion(Region.US_EAST)
                  .withNewResourceGroup( ResourceGroupName )
                  .withEmptyAccessPolicy()
                  .create();

          System.out.println("Created key vault");
          Utils.print(vault1);

          // Authorize an application
          System.out.println("Authorizing the application associated with the current service principal...");

          vault1 = vault1.update()
                  .defineAccessPolicy()
                      .forServicePrincipal(clientId)
                      .allowKeyAllPermissions()
                      .allowSecretPermissions(SecretPermissions.GET)
                      .allowSecretPermissions(SecretPermissions.LIST)
                      .attach()
                  .apply();

          System.out.println("Updated key vault");
          Utils.print(vault1);

          // Update a key vault
          System.out.println("Update a key vault to enable deployments and add permissions to the application...");

          vault1 = vault1.update()
                  .withDeploymentEnabled()
                  .withTemplateDeploymentEnabled()
                  .updateAccessPolicy(vault1.accessPolicies().get(0).objectId())
                      .allowSecretAllPermissions()
                      .parent()
                  .apply();

          System.out.println("Updated key vault");
          // Print the network security group
          Utils.print(vault1);

          // List key vaults
          System.out.println("Listing key vaults...");

          for (Vault vault : azure.vaults().listByResourceGroup( ResourceGroupName )) {
              Utils.print(vault);
          }

          // Delete key vaults
          System.out.println("Deleting the key vaults");
          azure.vaults().deleteById(vault1.id());
          System.out.println("Deleted the key vaults");

          return true;

      } catch (Exception e) {
          System.err.println(e.getMessage());
      } finally {
          try {
              System.out.println("Deleting Resource Group: " +  ResourceGroupName );
              azure.resourceGroups().deleteByName( ResourceGroupName );
              System.out.println("Deleted Resource Group: " +  ResourceGroupName );
          } catch (NullPointerException npe) {
              System.out.println("Did not create any resources in Azure. No clean up is necessary");
          } catch (Exception g) {
              g.printStackTrace();
          }
      }

      return false;
  }

  public static void main() {
      try {

          //=============================================================
          // Authenticate
          String fauth = "/Users/oleg.gorodnitchiibm.com/my.azureauth"; // System.getenv("AZURE_AUTH_LOCATION");
          System.out.println("Reading auth file: " + fauth);

          final File credFile = new File(fauth);

          Azure azure = Azure.configure()
                  .withLogLevel(LogLevel.BASIC)
                  .authenticate(credFile)
                  .withDefaultSubscription();

          // Print selected subscription
          System.out.println("Selected subscription: " + azure.subscriptionId());

          runEtoE(azure, ApplicationTokenCredentials.fromFile(credFile).clientId());

      } catch (Exception e) {
          System.out.println(e.getMessage());
          e.printStackTrace();
      }
  }
  */

}
