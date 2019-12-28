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

import org.json.*;
import java.io.File;

import com.org.cloud.api.core.utils.KubernetisClientUtil;
import com.org.cloud.api.core.utils.Utils;

public final class ManageKeyVault {

  private static String rgName;

  public ManageKeyVault(String rg) {
    rgName = rg;
  }

  // List key vaults
  public static String listKeyVaults() {
    String ret = "";
      try {
          //=============================================================
          // Authenticate
          String fauth = "/Users/oleg.gorodnitchiibm.com/my.azureauth"; // System.getenv("AZURE_AUTH_LOCATION");
          System.out.println("Reading auth file: " + fauth);
          System.out.println("Env var AZURE_AUTH_LOCATION: " + System.getenv("AZURE_AUTH_LOCATION"));
          final File credFile = new File(fauth);

          Azure azure = Azure.configure()
                  .withLogLevel(LogLevel.BASIC)
                  .authenticate(credFile)
                  .withDefaultSubscription();

          // Print selected subscription
          System.out.println("Selected subscription: " + azure.subscriptionId() );

          //ret = list( azure, ApplicationTokenCredentials.fromFile(credFile).clientId() );
          JSONArray jsonArray = new JSONArray();
          JSONObject jsonObj = new JSONObject();

          try {
              System.out.println("Listing key vaults...");

              for (Vault vault : azure.vaults().listByResourceGroup(rgName)) {
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

  private static Azure Authenticate(){
    String fauth = "/Users/oleg.gorodnitchiibm.com/my.azureauth"; // System.getenv("AZURE_AUTH_LOCATION");
    System.out.println("Reading auth file: " + fauth);
    System.out.println("Env var AZURE_AUTH_LOCATION: " + System.getenv("AZURE_AUTH_LOCATION"));
    final File credFile = new File(fauth);

    Azure azure = Azure.configure()
            .withLogLevel(LogLevel.BASIC)
            .authenticate(credFile)
            .withDefaultSubscription();

    return azure;
  }

  // create the vault
  public static String createKeyVault(String rg, String r) {
    try {
      //=============================================================
      // Authenticate
      Azure azure = Authenticate();
      // Print selected subscription
      System.out.println("Selected subscription: " + azure.subscriptionId() );

        // Create a key vault with empty access policy
        System.out.println("Creating a key vault...");
        Vault vault1 = azure.vaults().define(vaultName1)
                .withRegion(Region.US_EAST)
                .withNewResourceGroup(rgName)
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

      } catch (Exception e) {
          System.err.println(e.getMessage());
      }

    return "";
  }

  public static boolean runEtoE(Azure azure, String clientId) {
      final String vaultName1 = SdkContext.randomResourceName("vault1", 20);
      //final String vaultName2 = SdkContext.randomResourceName("vault2", 20);
      final String rgName = SdkContext.randomResourceName("rgNEMV", 24);

      try {
          // Create a key vault with empty access policy
          System.out.println("Creating a key vault...");
          Vault vault1 = azure.vaults().define(vaultName1)
                  .withRegion(Region.US_EAST)
                  .withNewResourceGroup(rgName)
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

          for (Vault vault : azure.vaults().listByResourceGroup(rgName)) {
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
              System.out.println("Deleting Resource Group: " + rgName);
              azure.resourceGroups().deleteByName(rgName);
              System.out.println("Deleted Resource Group: " + rgName);
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

}
