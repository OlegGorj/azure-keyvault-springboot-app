package keyvault;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import java.util.UUID;
import java.lang.String;
import java.util.*;
import org.json.*;
import com.jayway.jsonpath.JsonPath;

import com.microsoft.azure.management.Azure;

import com.org.cloud.api.core.service.ManageKeyVault;


@RestController
public class Controller {

	@RequestMapping("/")
	public String index() {
		return "";
	}

	@RequestMapping("/hash")
	public String hash() {
		String s = "some string";
		long hash = UUID.nameUUIDFromBytes(s.getBytes()).getMostSignificantBits();
		return String.format("%d", hash);
	}

	@RequestMapping(path="/keyvault",
			method=RequestMethod.POST,
			consumes = "application/json",
			produces = "application/json")
	public String createKV(@RequestBody String payload) {

		//System.out.println(payload);
		String resourcegroup, region, appcode, clientid;
		try {
			resourcegroup = JsonPath.read(payload, "$.resource-group");
			region = JsonPath.read(payload, "$.region");
			appcode = JsonPath.read(payload, "$.app-code");
			clientid = JsonPath.read(payload, "$.client-id");
		}catch(Exception e){
			return String.format( "[\"error\": \"Incorrect request body: %s\"]", e.getMessage() );
		}

		try {
			String kvName = appcode + "-keyvault";

			System.out.println(">> Attemting to creater keyvault as part of resourcegroup: " + resourcegroup);
			ManageKeyVault kv = new ManageKeyVault(resourcegroup, region, clientid);
			String r = kv.createKeyVault(resourcegroup, region, kvName);

			return kv.getEvents() ;

		} catch (Exception e) {
			return String.format( "[\"error\": \"%s\"]", e.getMessage() );
		}

	}

	@RequestMapping(path="/keyvault/{resourcegroup}",
		method=RequestMethod.GET,
		produces = MediaType.APPLICATION_JSON_VALUE)
	public String getKV(@PathVariable("resourcegroup") String resourcegroup) {

		try {

			System.out.println(">> listing all vaults for resourcegroup: " + resourcegroup);

			ManageKeyVault kv = new ManageKeyVault(resourcegroup);
			return kv.listKeyVaults();

		} catch (Exception e) {
			return String.format( "[\"error\": \"%s\"]", e.getMessage() );
		}
	}

}
