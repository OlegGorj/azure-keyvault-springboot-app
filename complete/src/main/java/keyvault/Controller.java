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

		System.out.println(payload);

		try {

			String resourcegroup = JsonPath.read(payload, "$.resource-group");
			if ( resourcegroup == null || resourcegroup.isEmpty() ) {
				return String.format("json format error. missing resource group");
			}

			String region = JsonPath.read(payload, "$.region");
			String kvName = JsonPath.read(payload, "$.app-code") + "-keyvault";

			System.out.println(">> Attemting to creater keyvault as part of resourcegroup: " + resourcegroup);
			ManageKeyVault kv = new ManageKeyVault(resourcegroup);
			String r = kv.createKeyVault(resourcegroup, region, kvName);

			//System.out.println(">> events are: \n" + kv.getEvents() );

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
