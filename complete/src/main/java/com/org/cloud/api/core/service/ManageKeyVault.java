package com.org.cloud.api.core.service;

import com.microsoft.azure.credentials.ApplicationTokenCredentials;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.keyvault.KeyPermissions;
import com.microsoft.azure.management.keyvault.SecretPermissions;
import com.microsoft.azure.management.keyvault.Vault;
import com.microsoft.azure.management.keyvault.AccessPolicy;
import com.microsoft.azure.management.resources.fluentcore.arm.Region;
import com.microsoft.azure.management.resources.fluentcore.utils.SdkContext;
import com.microsoft.rest.interceptors.LoggingInterceptor;
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

import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.credentials.AppServiceMSICredentials;
import com.microsoft.azure.keyvault.KeyVaultClient;
import com.microsoft.azure.keyvault.models.KeyBundle;

import com.org.cloud.api.core.utils.KubernetisClientUtil;
import com.org.cloud.api.core.utils.Utils;
import com.org.cloud.api.core.utils.Events;


public class ManageKeyVault {

  Logger logger = LoggerFactory.getLogger(ManageKeyVault.class);

  private String ResourceGroupName = "";     // resource group name
  private String RegionName = "";            // region name
  private Region kvRegion;
//@Value("${azure.keyvault.client-id:''}")
  private String clientId = "";              // Managed Identity
  private String clientKey = "";             // MI key

  private  Azure azure;

  private Events events = new Events();

  //------------------------------------------------------------------------------------------
  // authenticate
  private  Azure Authenticate() throws Exception {
    String fauth = "azureauth";
    logger.debug("Reading auth file: " + fauth);
    final File credFile = new File(fauth);

    azure = Azure.configure()
            .withLogLevel(LogLevel.BASIC)
            .authenticate(credFile)
            .withDefaultSubscription();
    events.addEvent("selected-subscriptionId", "value", azure.subscriptionId() );
    if (clientId == "" ){
      clientId = ApplicationTokenCredentials.fromFile(credFile).clientId();
    }
    events.addEvent("selected-clientId", "value", clientId );
    return azure;
  }

  // init function
  private void init() {
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
  //------------------------------------------------------------------------------------------
  // create the vault
  public  String createKeyVault(String resourcegroup, String rg, String kvname) {
    try {
      // Authenticate
      this.azure = Authenticate();
    }catch(Exception e){
      System.err.println(e.getMessage());
      events.addEvent("error", "message", e.getMessage());
      return events.toString();
    }
    try {
        /*
        * Create a key vault with non-empty access policy
        * and authorize an application
        */
        logger.info("Creating a key vault...");
        Vault vault1 = azure.vaults().define( kvname )
                .withRegion( this.kvRegion )
                .withNewResourceGroup( resourcegroup )
                .defineAccessPolicy()
                .forServicePrincipal( this.clientId )
                .allowKeyPermissions( KeyPermissions.GET )
                .allowKeyPermissions( KeyPermissions.LIST )
                .allowSecretPermissions( SecretPermissions.GET )
                .allowSecretPermissions( SecretPermissions.LIST )
                .attach()
                .create();
        logger.info("Created key vault : " + kvname);
        Utils.print(vault1);
        /*
        * build events journal
        */
        logger.info("Collecting a key vault journal...");
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
            accessPolicyJSON.put(jsonAP);
        }
        json.put("access-policy", accessPolicyJSON);
        events.addEvent("create-keyvault", json );
        /*
        * update keyvault config to enable deployments
        */
        logger.info("Updating a key vault...");
        logger.info("Update a key vault to enable deployments and add permissions to the application...");
        vault1 = vault1.update()
                .withDeploymentEnabled()
                .withTemplateDeploymentEnabled()
                .updateAccessPolicy( vault1.accessPolicies().get(0).objectId() )
                .allowSecretPermissions( SecretPermissions.GET )
                .allowSecretPermissions( SecretPermissions.LIST )
                .parent()
                .apply();

        events.addEvent("update-keyvault", "update keyvault", kvname );
        logger.info("Updated key vault : " + kvname);
        Utils.print(vault1);


      } catch (Exception e) {
        System.err.println(e.getMessage());
        events.addEvent("error", "message", e.getMessage());
        return events.toString();
      }

    return events.toString();
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


  //------------------------------------------------------------------------------------------
  public void setResourceGroup(String resGroup){
    this.ResourceGroupName = resGroup;
    events.addEvent("set-resourcegroup",resGroup);
  }
  public String setRegion(String rg){

    try{

      if ( rg == null || rg.isEmpty() ) {
        this.RegionName = "useast";
        this.kvRegion = Region.US_EAST; // defaulting to US_EAST
        events.addEvent("set-region", "action", "setting to default US_EAST");
      }else{
        this.RegionName = rg;
        this.kvRegion = Region.fromName(rg);
        events.addEvent("set-region", "action", "setting to " + rg);
      }
    }catch(Exception e){
      logger.error(e.getMessage());
      events.addEvent("error", e.getMessage());
      //return new JSONObject().put("error", e.getMessage()).toString();
      return String.format( "[{\"error\": \"%s\"}]", e.getMessage() );
    }
    return "[]";
  }
  public void setClientId(String id){
    this.clientId = id;
    events.addEvent("set-clientid",id);
  }

}
