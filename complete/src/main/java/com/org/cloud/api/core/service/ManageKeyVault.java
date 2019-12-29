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
import java.sql.Timestamp;
import java.util.Date;
import org.json.*;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import com.org.cloud.api.core.utils.KubernetisClientUtil;
import com.org.cloud.api.core.utils.Utils;

public class ManageKeyVault {

  Logger logger = LoggerFactory.getLogger(ManageKeyVault.class);

  private  String ResourceGroupName = "";     // resource group name
  private  String RegionName = "";            // region name

  @Value("${azure.keyvault.client-id:''}")
  private String clientId;              // service principal

  private  Azure azure;
  private  JSONArray eventsJsonArr = new JSONArray();

  //------------------------------------------------------------------------------------------
  private void init(){
    logger.info("Env var AZURE_AUTH_LOCATION: " + System.getenv("AZURE_AUTH_LOCATION"));
    this.eventsJsonArr.put( new JSONObject().put("error", "false") );
    addEvent("error", "false");
  }
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

  //------------------------------------------------------------------------------------------
  public void setResourceGroup(String resGroup){
    this.ResourceGroupName = resGroup;
    addEvent("set-resourcegroup",resGroup);
  }
  public void setRegion(String region){
    this.RegionName = region;
    addEvent("set-region",region);
  }
  public void setClientId(String id){
    this.clientId = id;
    addEvent("set-clientid",id);
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
  public ManageKeyVault(String resourcegroup, String region, String clientid) {
    init();
    setResourceGroup(resourcegroup);
    setRegion(region);
    setClientId(clientid);
  }

  // authenticate
  private  Azure Authenticate() throws Exception {
    String fauth = "/Users/oleg.gorodnitchiibm.com/my.azureauth";
    //logger.info("Reading auth file: " + fauth);
    logger.debug("Reading auth file: " + fauth);
    final File credFile = new File(fauth);

    azure = Azure.configure()
            .withLogLevel(LogLevel.BASIC)
            .authenticate(credFile)
            .withDefaultSubscription();
    addEvent("selected-subscriptionId", "value", azure.subscriptionId() );

    if (clientId == "" ){
      clientId = ApplicationTokenCredentials.fromFile(credFile).clientId();
    }
    addEvent("selected-clientId", "value", clientId );

    return azure;
  }

  // create the vault
  public  String createKeyVault(String resourcegroup, String rg, String kvname) {

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
      addEvent("error", e.getMessage());  //addEvent("error", "true");
      return new JSONObject().put("error", e.getMessage()).toString();
    }

    try {
      // Authenticate
      Azure azure = Authenticate(); // TODO move to init
        // Create a key vault with non-empty access policy
        // and authorize an application
        logger.info("Creating a key vault...");
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
            jsonAP.put("secret-permission", Joiner.on(", ").join(accessPolicy.permissions().secrets()) );
            jsonAP.put("certs-permission", Joiner.on(", ").join(accessPolicy.permissions().certificates()) );
            jsonAP.put("storage-permission", Joiner.on(", ").join(accessPolicy.permissions().storage()) );
            accessPolicyJSON.put(jsonAP);
        }
        json.put("access-policy", accessPolicyJSON);
        addEvent("createKeyVault", json );
        logger.info("Created key vault");
        //Utils.print(vault1);
/*
        // Authorize an application
        logger.info("Authorizing the application associated with the current service principal...");
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
        logger.info("Updated key vault");
        Utils.print(vault1);
*/
        // Update a key vault
        logger.info("Update a key vault to enable deployments and add permissions to the application...");
        vault1 = vault1.update()
                .withDeploymentEnabled()
                .withTemplateDeploymentEnabled()
                .updateAccessPolicy( vault1.accessPolicies().get(0).objectId() )
                .allowSecretPermissions( SecretPermissions.GET )
                .allowSecretPermissions( SecretPermissions.LIST )
                .parent()
                .apply();

        addEvent("updateKeyVault", "update keyvault", kvname );
        logger.info("Updated key vault");
        // Print the network security group
        Utils.print(vault1);

      } catch (Exception e) {
          System.err.println(e.getMessage());
      }

    return "";
  }

  // List key vaults
  public  String listKeyVaults() {
    String ret = "";
    try {
          // Authenticate
          Azure azure = Authenticate();
          // Print selected subscription
          logger.info("Selected subscription: " + azure.subscriptionId() );

          JSONArray jsonArray = new JSONArray();
          JSONObject jsonObj = new JSONObject();

          try {
              logger.info("Listing key vaults...");

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

              logger.info("Lising vaults: " + jsonArray );

          } catch (Exception e) {
              System.err.println(e.getMessage());
              JSONObject json = new JSONObject();
              jsonObj.put("error", e.getMessage() );
              return json.toString();
          }
          return jsonArray != null ? jsonArray.toString() : "[]";

    } catch (Exception e) {
          ret =  e.getMessage();
          logger.info(e.getMessage());
          e.printStackTrace();
    }
    return ret;
  }

  /*
  public  boolean runEtoE(Azure azure, String clientId) {
      final String vaultName1 = SdkContext.randomResourceName("vault1", 20);
      //final String vaultName2 = SdkContext.randomResourceName("vault2", 20);
      final String  ResourceGroupName  = SdkContext.randomResourceName("rgNEMV", 24);

      try {
          // Create a key vault with empty access policy
          logger.info("Creating a key vault...");
          Vault vault1 = azure.vaults().define(vaultName1)
                  .withRegion(Region.US_EAST)
                  .withNewResourceGroup( ResourceGroupName )
                  .withEmptyAccessPolicy()
                  .create();

          logger.info("Created key vault");
          Utils.print(vault1);

          // Authorize an application
          logger.info("Authorizing the application associated with the current service principal...");

          vault1 = vault1.update()
                  .defineAccessPolicy()
                      .forServicePrincipal(clientId)
                      .allowKeyAllPermissions()
                      .allowSecretPermissions(SecretPermissions.GET)
                      .allowSecretPermissions(SecretPermissions.LIST)
                      .attach()
                  .apply();

          logger.info("Updated key vault");
          Utils.print(vault1);

          // Update a key vault
          logger.info("Update a key vault to enable deployments and add permissions to the application...");

          vault1 = vault1.update()
                  .withDeploymentEnabled()
                  .withTemplateDeploymentEnabled()
                  .updateAccessPolicy(vault1.accessPolicies().get(0).objectId())
                      .allowSecretAllPermissions()
                      .parent()
                  .apply();

          logger.info("Updated key vault");
          // Print the network security group
          Utils.print(vault1);

          // List key vaults
          logger.info("Listing key vaults...");

          for (Vault vault : azure.vaults().listByResourceGroup( ResourceGroupName )) {
              Utils.print(vault);
          }

          // Delete key vaults
          logger.info("Deleting the key vaults");
          azure.vaults().deleteById(vault1.id());
          logger.info("Deleted the key vaults");

          return true;

      } catch (Exception e) {
          System.err.println(e.getMessage());
      } finally {
          try {
              logger.info("Deleting Resource Group: " +  ResourceGroupName );
              azure.resourceGroups().deleteByName( ResourceGroupName );
              logger.info("Deleted Resource Group: " +  ResourceGroupName );
          } catch (NullPointerException npe) {
              logger.info("Did not create any resources in Azure. No clean up is necessary");
          } catch (Exception g) {
              g.printStackTrace();
          }
      }

      return false;
  }

  public  void main() {
      try {

          //=============================================================
          // Authenticate
          String fauth = "/Users/oleg.gorodnitchiibm.com/my.azureauth"; // System.getenv("AZURE_AUTH_LOCATION");
          logger.info("Reading auth file: " + fauth);

          final File credFile = new File(fauth);

          Azure azure = Azure.configure()
                  .withLogLevel(LogLevel.BASIC)
                  .authenticate(credFile)
                  .withDefaultSubscription();

          // Print selected subscription
          logger.info("Selected subscription: " + azure.subscriptionId());

          runEtoE(azure, ApplicationTokenCredentials.fromFile(credFile).clientId());

      } catch (Exception e) {
          logger.info(e.getMessage());
          e.printStackTrace();
      }
  }
  */

}
