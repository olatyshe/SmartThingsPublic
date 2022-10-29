/*
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy
 *  of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations
 *  under the License.
 */

preferences {
        input "token", "text", title: "Whistle Token", description: "Whistle Login Token", required: true 
        input "email", "text", title: "Email", description: "Whistle Login Email", required: true 
        input "password", "text", title: "Password", description: "Whistle Login Password", required: true 
        input "petID", "text", title: "Whistle Pet ID", description: "Whistle Pet ID #", required: true
        input "homeID", "text", title: "Whistle Home ID", description: "Whistle Home ID #", required: true
        input "refreshRate", "enum", title: "Data Refresh Rate", defaultValue: 0, options:[0: "Never", 1: "Every Minute", 2: "Every 2 Minutes", 5: "Every 5 Minutes", 10: "Every 10 Minutes", 20: "Every 20 Minutes"], displayDuringSetup: true
		}

metadata {
	definition (name: "Whistle Presence", namespace: "swamplynx", author: "SwampLynx") {
		capability "Presence Sensor"
        capability "Motion Sensor"
		capability "Occupancy Sensor"
		capability "Sensor"
        capability "Battery"
        capability "Refresh"
        capability "Polling"
	}

	simulator {
		status "present": "presence: 1"
		status "not present": "presence: 0"
		status "occupied": "occupancy: 1"
		status "unoccupied": "occupancy: 0"
	}

	tiles {
		standardTile("presence", "device.presence", width: 2, height: 2, canChangeBackground: true) {
			state("present", labelIcon:"st.presence.tile.mobile-present", backgroundColor:"#00A0DC")
			state("not present", labelIcon:"st.presence.tile.mobile-not-present", backgroundColor:"#ffffff")
		}
                   
        valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 1, height: 1) {
			state("battery", label:'${currentValue}% battery', unit:"")
		}
        

        
        standardTile("refresh", "device.weather", decoration: "flat", width: 1, height: 1) {
            state "default", label: "", action: "refresh", icon:"st.secondary.refresh"
        }
		main (["presence", "battery", "refresh"])
		details (["presence", "battery", "refresh"])
	}
}

def parse(String description) {
	def name = parseName(description)
	def value = parseValue(description)
	def linkText = getLinkText(device)
	def descriptionText = parseDescriptionText(linkText, value, description)
	def handlerName = getState(value)
	def isStateChange = isStateChange(device, name, value)

	def results = [
    	translatable: true,
		name: name,
		value: value,
		unit: null,
		linkText: linkText,
		descriptionText: descriptionText,
		handlerName: handlerName,
		isStateChange: isStateChange,
		displayed: displayed(description, isStateChange)
	]
	log.debug "Parse returned $results.descriptionText"
	return results
}

private String parseName(String description) {
	if (description?.startsWith("presence: ")) {
		return "presence"
	} else if (description?.startsWith("occupancy: ")) {
		return "occupancy"
	}
	null
}

private String parseValue(String description) {
	switch(description) {
		case "presence: 1": return "present"
		case "presence: 0": return "not present"
		case "occupancy: 1": return "occupied"
		case "occupancy: 0": return "unoccupied"
		default: return description
	}
}

private parseDescriptionText(String linkText, String value, String description) {
	switch(value) {
		case "present": return "{{ linkText }} has arrived"
		case "not present": return "{{ linkText }} has left"
		case "occupied": return "{{ linkText }} is inside"
		case "unoccupied": return "{{ linkText }} is away"
		default: return value
	}
}

private getState(String value) {
	switch(value) {
		case "present": return "arrived"
		case "not present": return "left"
		case "occupied": return "inside"
		case "unoccupied": return "away"
		default: return value
	}
}

def refresh() { 
	 log.info("Whistle Presence Refresh Requested")
    callAPI()
}
def poll() { 
	 log.info("Whistle Presence Poll Requested")
    callAPI()
}

def scheduledPoll() { 
	 log.info("Whistle Presence starting Scheduled Refresh")
    callAPI()
}

def getAPIkey() {
	return "Bearer ${token}"
}


def setAPIkey() {
	
    def params = [
    uri: "https://app.whistle.com/api/login",
    body: [email: "${email}",password: "${password}"]
]
   def auth_token = getAPIkey() 
                        
 try {
      	log.debug "Starting HTTP POST request to Whistle API"
        log.debug " POST params: ${params}"
    	httpPost(params) { resp ->
   		if (resp.data) {
      		log.debug "SetAPI Response Data = ${resp.data}"
        		log.debug "Response Status = ${resp.status}"
  
//           resp.headers.each {
//			log.debug "header: ${it.name}: ${it.value}"
//  				}
       	}
        	if(resp.status == 200 | resp.status == 201) {
	        	log.debug "Request to Whistle API was OK, parsing data"
  			    auth_token = resp.data.auth_token         
          		}
            
        	else {
        		log.error "Request got HTTP status ${resp.status}"
        	}
        }
    } catch(e)
    {
    	log.debug e
    }
       
       return "Bearer ${auth_token}"
}




private def callAPI() {

    if (petID){
        def refreshTime =  refreshRate ? (refreshRate as int) * 60 : 0
        if (refreshTime > 0) {
            runIn (refreshTime, scheduledPoll)
            log.debug "Data will repoll every ${refreshRate} minutes"   
        }
        else log.debug "Data will never automatically repoll"   
    
    	
        def accessToken = setAPIkey()
        
        def params = [
            uri: "https://app.whistle.com",
            path: "/api/pets/${petID}",
            contentType: "application/json",
            headers: [
            	"Authorization": "${accessToken}",
                "Accept": "application/vnd.whistle.com.v4+json",
                "Content-Type": "application/json",
                "Connection": "keep-alive",
                "Accept-Language": "en-us",
                "Accept-Encoding": "br, gzip, deflate",
                "User-Agent": "Winston/2.5.3 (iPhone; iOS 12.0.1; Build:1276; Scale/2.0)" ],
                      ]
      try {
      	log.debug "Starting HTTP GET request to Whistle API"
    	httpGet(params) { resp ->
    		if (resp.data) {
        		log.debug "Response Data = ${resp.data}"
        		log.debug "Response Status = ${resp.status}"
  
//              resp.headers.each {
//  			log.debug "header: ${it.name}: ${it.value}"
//  				}
       	}
        	if(resp.status == 200 | resp.status == 201) {
	        	log.debug "Request to Whistle API was OK, parsing data"
  
                def batt = resp.data.pet.device.battery_level
                log.info "Whistle battery status is ${batt}%"
                sendEvent(name:"battery", value: batt, unit: "%")
                
                def locationIDnum = resp.data.pet.last_location.place.id.toInteger()
                def locationStatus = resp.data.pet.last_location.place.status.toString()
                def homeIDnum = "${homeID}".toInteger()
                
                log.debug "Current Home ID is ${homeIDnum}"
                log.debug "Current Pet Location ID is ${locationIDnum}"
                log.debug "Current Pet Location Status is ${locationStatus}"
               
                
                if (locationIDnum.equals(homeIDnum) && locationStatus.equals("in_beacon_range")) {
                                sendEvent(name: "presence", value: "present")
                                sendEvent(name: "motion", value: "inactive")
                                log.info "Pet is on Home WiFi Beacon, Updating Presence to Present"
                            } 
                else if (locationIDnum.equals(homeIDnum) && locationStatus.equals("in_geofence_range")) {
                                sendEvent(name: "presence", value: "present")
                                sendEvent(name: "motion", value: "inactive")
                                log.info "Pet inside Home Geofence, Updating Presence to Present"
                            } 
                            else {
                                sendEvent(name: "presence", value: "not present")
                                sendEvent(name: "motion", value: "active")
                                log.info "Pet is NOT Home, Updating Presence to Not Present"
                            }
            
    		}
            
        	else {
        		log.error "Request got HTTP status ${resp.status}"
        		setAPIkey()
        	}
        }
    } catch(e)
    {
    	log.debug e
       setAPIkey()
    }
}
       else log.debug "The Pet ID missing from the device settings"
   }