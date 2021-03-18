/*
 * ----------------------------------------------------------------------------------
 *
 *                             `_`     `_,_`  _'                                  `,`  
 *                            -#@@- >O#@@@@u B@@>                                 8@E  
 *    :)ilc}` `=|}uccccVu}r"   VQz `@@#Mhzk= |8M   `=v}ucccccuY),    `~v}uVVcccccV#@$  
 *  ^Q@#EMqK.I#@QRdMqqMdRQ@@Q, Q@B `@@BqqqW^ W@@` e@@QRdMMMMbEQ@@8: i#@BOMqqqqqqqM#@$  
 *  D@@`    )@@x          <@@T Q@B `@@q      W@@`>@@l          :@@z`#@d           Q@$  
 *  D@#     ?@@@##########@@@} Q@B `@@q      W@@`^@@@##########@@@y`#@W           Q@$  
 *  0@#     )@@d!::::::::::::` Q@B `@@M      W@@`<@@E!::::::::::::``#@b          `B@$  
 *  D@#     `m@@#bGPP}         Q@B `@@q      W@@` 3@@BbPPPV         y@@QZPPPPPGME#@8=  
 *  *yx       .*icywwv         )yv  }y>      ~yT   .^icywyL          .*]uywwwwycL^-   
 *                                                                                    
 *      (c) 2021 Reified Ltd.   W: www.reified.co.uk    E: sales@reified.co.uk
 *
 * ----------------------------------------------------------------------------------
 *
 * Z-way Dimmer Driver for Hubitat C-7
 *
 * Calls Z-way REST API to perform switch on/off and dimming on a class 38 device.
 *
 * For technical details of Z-Way REST API, see https://zwave.me/z-way
 *
 * Notes: (1) Z-Way seems to require admin privileges (Z-Way 2.2.5) for control.
 *
 *        (2) The API URIs can be found using by clicking the device's cog in Zway,
 *            then scrolling down to "API commands for developers"
 *
 *        (3) Logging is enabled via UI, but auto-disables after a while (to avoid
 *            the logs getting accidentally overwhelmed).
 *
 *        (4) Polling is made more complicated in that it *seems* that z-way may
 *            itself also be polling internally, which can create weird twitchy
 *            effects if trying to update the Hubitat slider with the latest
 *            "actual" value too frequently. Also, hubitat slider tracks your
 *            finger (of course) so reflects thet *requested* value, so if you
 *            update its state to the "actual" value, it may be a stale "actual"
 *            value for a short while. 
 *
 * Author: S.J.Morley  Date: 2021-02-12  E: steve@reified.co.uk  W: www.reified.co.uk
 *
 * ----------------------------------------------------------------------------------
 */

import groovy.json.JsonSlurper

/*
 * Define the device driver capabilities. 
 *
 */

metadata
{
    definition(name: "Z-way Dimmer", namespace: "Reified", author: "Steve Morley") {
        capability "Initialize"
        capability "Light"
        capability "Switch"
        capability "Switch Level"
    }
}

/*
 * Define the user-specifiable device configuration parameters. 
 *
 */

preferences
{
    section("Device Identity") {
        input name: "ipAddr",     type: "text",     title: "Z-Way IP Address", required: true
        input name: "ipPort",     type: "number",   title: "Z-Way Port", defaultValue: 8083, required: true
        input name: "username",   type: "text",     title: "Z-Way Username", required: false
        input name: "password",   type: "password", title: "Z-Way Password", required: false
        input name: "deviceID",   type: "text",     title: "Z-Way Switch Device ID", required: true
        input name: "instanceID", type: "text",     title: "Z-Way Switch Instance ID", defaultValue: 0, required: true
    }

    section("Device Behaviour") {
        input name: "immediateFeedback", type: "bool", title: "Immediate feedback", defaultValue: true
        input name: "refreshInterval", type: "number", title: "Actual state polling (seconds, 0=none)", defaultValue: 5, required: true
    }
    
    section("Diagnostics") {
        input name: "logEnable",  type: "bool",   title: "Enable debug logging", defaultValue: true
    }
}

/*
 * ----------------------------------------------------------------------------------
 *
 * zWaySendCommand(username, password, ipAddr, ipPort, commandPath)
 *
 * Send a Z-Way specific HTTP GET to a Z-Way device.
 *      username -  username for BasicAuth login (optional)
 *      password -  password for basicAuth login (optional)
 *        ipAddr -  IP address of the Z-Way device
 *        ipPort -  Port no. for the Z-way device
 *   commandPath -  Device-specific path-part representing the command.
 *
 * ----------------------------------------------------------------------------------
 */

Object zwaySendCommand(username, password, ipAddr, ipPort, commandPath)
{ 
    def response = null;
    
    String host = "${ipAddr}:${ipPort}";
    password = !password ? "" : password; // render null as ""
    
    headerItems = [:];
    
    if (username) { // neither null nor empty
        String credentials = "${username}:${password}"
        headerItems.put("Authorization", "Basic " + credentials.bytes.encodeBase64().toString());
    }
    
    if (logEnable) {
        log.debug "${device.label?device.label:device.name}: Zway - " + host + " GET path " + commandPath;
    }
    
    URI fullUri = new URI("http://" + host + commandPath);
    
    httpGet(  
        uri: fullUri,
        headers: headerItems
    ) { resp -> response = resp; }       
   
    
    if (!response.success) {
        throw new Exception("Unsuccessful response to HTTP GET of ${commandPath}");
    }
    
    if (logEnable) {
        log.debug "${device.label?device.label:device.name}: response data: ${response.data}";
    }
    
    return response;
}

/*
 * ----------------------------------------------------------------------------------
 *
 * zWaySendCommand(username, password, ipAddr, ipPort, commandPath)
 *
 * Send a Z-Way specific HTTP GET to the current Z-Way device.
 *   commandPath -  Device-specific path-part representing the command.
 *
 * ----------------------------------------------------------------------------------
 */

Object zwaySendCommand(commandPath) {
    return zwaySendCommand(
        settings.username, settings.password,
        settings.ipAddr, settings.ipPort,
        commandPath
    );
}

/*
 * ----------------------------------------------------------------------------------
 *
 * zWayRead(commandPath)
 *
 * Send a Z-Way specific command to return some data.
 *   commandPath -  Device-specific path-part representing the item to return.
 * Returns: Json object for the requested data.
 *
 * ----------------------------------------------------------------------------------
 */

Object zwayRead(commandPath) { 
    def response = zwaySendCommand(commandPath);
    return response.data;
}

/*
 * ----------------------------------------------------------------------------------
 *
 * zWayWrite(commandPath)
 *
 * Send a Z-Way specific command to write some data.
 *   commandPath -  Device-specific path-part representing the item write command.
 * Returns: Json object for the requested data.
 *
 * ----------------------------------------------------------------------------------
 */

def void zwayWrite(commandPath) {     
    zwaySendCommand(commandPath);
}

/*
 * ----------------------------------------------------------------------------------
 *
 * formDevicePathBase(deviceID, instanceID, classID)
 *
 * Form the path base for the given deviceID/instanceID/classID.
 *   deviceID - the deviceID as shown in z-way
 *   instanceID - the device instance ID (usually zero) as shown in z-way.
 *   classID - the zwave class of device.
 * Returns: The path to the device.
 *
 * ----------------------------------------------------------------------------------
 */

def String zwayFormDevicePathBase(deviceID, instanceID, classID) {
    return "/ZAutomation/api/v1/devices/ZWayVDev_zway_${deviceID}-${instanceID}-${classID}";
}

/*
 * ----------------------------------------------------------------------------------
 *
 * zwayReadDeviceState()
 *
 * Send a Z-Way specific command to read an attribute of the device.
 * Returns: The JSON string representing the device's state.
 *
 * ----------------------------------------------------------------------------------
 */

def Object zwayReadDeviceState()
{
    def zwayCommandClass = 38; // zway class MultilevelSwitch
    String commandPath = zwayFormDevicePathBase(settings.deviceID, settings.instanceID, zwayCommandClass);
    
    log.debug "${device.label?device.label:device.name}: reading state for device ${settings.deviceID} instance ${settings.instanceID}.";

    Object responseDataJson = zwayRead(commandPath);
    
    return responseDataJson;
}

/*
 * ----------------------------------------------------------------------------------
 *
 * int zwayGetLevel()
 *
 * Reads the device's dimming level. 
 * For a dimmer this will be 1..100 if on, or 0 for off.
 * For a switch this will 255 if on, or 0 for off.
 *
 * ----------------------------------------------------------------------------------
 */

def Integer zwayGetLevel() {    
    Object json = zwayReadDeviceState();
    return json.data.metrics.level;
}

/*
 * ----------------------------------------------------------------------------------
 *
 * zwaySetLevel(level)
 * Writes the devices dimming level.
 * (0 means off, 255 means on, 1..100 mean specific dimming level (if a dimmer)
 *
 * ----------------------------------------------------------------------------------
 */

def zwaySetLevel(level) {
    def zwayCommandClass = 38; // ...means a dimmer (MultilevelSwitch)
    String commandPath = zwayFormDevicePathBase(settings.deviceID, settings.instanceID, zwayCommandClass);
    commandPath += "/command/exact?level=${level}";
    zwayWrite(commandPath);
}

/*
 * ----------------------------------------------------------------------------------
 *
 * zwaySetOnOff(on)
 * Sets the device to either on or off.
 *
 * ----------------------------------------------------------------------------------
 */

def zwaySetOnOff(on) {
    String command = on ? "on" : "off";
    def zwayCommandClass = 38; // ...means a dimmer (MultilevelSwitch)
    String commandPath = zwayFormDevicePathBase(settings.deviceID, settings.instanceID, zwayCommandClass);
    commandPath += "/command/${command}";
    zwayWrite(commandPath);        
}

/*
 * ----------------------------------------------------------------------------------
 *
 * zwayGetOnOff(on)
 * Gets the device on/off state.
 * Returns true if on, otherwise false.
 *
 * ----------------------------------------------------------------------------------
 */

def zwayGetOnOff() {
    Object json = zwayReadDeviceState();
    return json.data.metrics.level > 0;
}

/*
 * ----------------------------------------------------------------------------------
 *
 * on()
 *
 * Framework event: respond to an ON request.
 *
 * ----------------------------------------------------------------------------------
 */

def on() { 
    log.debug "### ${device.label?device.label:device.name}: REQUEST FOR ON.";
    doOn();
}

/*
 * ----------------------------------------------------------------------------------
 *
 * doOn()
 *
 * Tell zwave about the off request.
 *
 * ----------------------------------------------------------------------------------
 */

def doOn() { 
    try {
        zwaySetOnOff(true);
        if (settings.immediateFeedback) {
            sendEvent(name: "switch", value: "on", isStateChange: true);
        }
    } catch (Exception e) {
        log.warn "${device.label?device.label:device.name}: failed to switch on: ${e.message})";
    }
}

/*
 * ----------------------------------------------------------------------------------
 *
 * off()
 *
 * Framework event: respond to an OFF request.
 *
 * ----------------------------------------------------------------------------------
 */

def off() {
    log.debug "### ${device.label?device.label:device.name}: REQUEST FOR OFF.";
    doOff();
}

/*
 * ----------------------------------------------------------------------------------
 *
 * doOff()
 *
 * Tell zwave about the off request.
 *
 * ----------------------------------------------------------------------------------
 */

def doOff() {
    try {
        zwaySetOnOff(false);
        if (settings.immediateFeedback) {
            sendEvent(name: "switch", value: "off", isStateChange: true);
        }
    } catch (Exception e) {
        log.warn "${device.label?device.label:device.name}: failed to switch off: ${e.message})";
    }
}

/*
 * ----------------------------------------------------------------------------------
 *
 * setLevel(selectedLevel)
 *
 * Tell zwave about the newly requested level.
 *   selectedLevel -  percentage dimmed (0-100).
 *
 * ----------------------------------------------------------------------------------
 */

def setLevel(level) {
    log.debug "### ${device.label?device.label:device.name}: REQUEST TO SET LEVEL ${level}."
    doSetLevel(level);
}

/*
 * ----------------------------------------------------------------------------------
 *
 * setLevel(level)
 *
 * Framework event: request to set a specific dimming level.
 *   level -  percentage dimmed (0-100).
 *
 * ----------------------------------------------------------------------------------
 */

def doSetLevel(level) {
    try {
        zwaySetLevel(level);
        if (settings.immediateFeedback) {
            sendEvent(name: "switch", value: isOn(level) ? "on" : "off", isStateChange: true);
            rescheduleStateRefresh(); // to avoid any imminent (stale) update to the UI.
        }
    } catch (Exception e) {
        log.warn "${device.label?device.label:device.name}: failed to set dimming level to ${level}: ${e.message})";
    }
}

/*
 * ----------------------------------------------------------------------------------
 *
 * isOn(level)
 *
 * Determine if the given level means on.
 * Returns: true if on, false if off.
 *
 * ----------------------------------------------------------------------------------
 */

def isOn(level) {
    return level > 0;   
}

/*
 * ----------------------------------------------------------------------------------
 *
 * writeState(attributeName value, hasChange)
 *
 * ----------------------------------------------------------------------------------
 */

def writeState(attributeName, value, hasChanged) {
    sendEvent(name: attributeName, value: value.toString(), isStateChange: hasChanged);
}

/*
 * ----------------------------------------------------------------------------------
 *
 * showStaticAttributes()
 *
 * Retrieve some unchanging attributes from the device and show them. 
 *
 * ----------------------------------------------------------------------------------
 */

def showStaticAttributes()
{
    // Retrieve non-changing attributes here.
    // These are potentially useful, showing aspects of the dimmer,
    // so let's retrieve them and show them on the device config page.

    // Object json = zwaySendCommand("/ZWaveAPI/Data/*");
                                  
    // log.debug "Initialisation: retrieved attributes ${json}";
    
    // writeState("zwayDeviceType", json.data.deviceType, true);
    
    // writeState("zwaveVersion",  state.data., true);
    // writeState("zwaveSecurity", zwayReadAttribute("security"), true);
}

/*
 * ----------------------------------------------------------------------------------
 *
 * initialize()
 *
 * Framework event: request to initialize the dimmer. 
 *
 * ----------------------------------------------------------------------------------
 */

def initialize() {
    scheduleStateRefresh();
    showStaticAttributes();
}

/* 
 *
 */

def refreshStateLevel(level) {
    log.debug "Refreshing state level as ${level}"
    sendEvent(name: "level", value: level, unit:"%", isStateChange: true);
    sendEvent(name: "switch", value: isOn(level) ? "on" : "off", isStateChange: true);
}

/* 
 *
 */

def scheduleStateRefresh() {
    if (settings.refreshInterval > 0) {
        runIn(settings.refreshInterval, doStateRefresh);
    }
}

/*
 *
 */

def unscheduleStateRefresh() {
    try {
        unschedule(doStateRefresh);
    } catch (Exception ex) {
        log.warn "${device.label?device.label:device.name}: on trying to unschedule state refresh: ${ex.message}"    
    }
}

/* 
 *
 */

def rescheduleStateRefresh() {
    unscheduleStateRefresh();
    scheduleStateRefresh();    
}

/* 
 *
 */

def stateRefreshLevel()
{
    // We only update our own state from what zway tell us. We never do so 
    // at the point of user interaction as that request might fail. We 
    // of course want our own state (and UI) to reflect the reality.

    synchronized(this)
    {
        try {
            level = zwayGetLevel();
            log.debug "State refresh: read level of ${level}";
            refreshStateLevel(level);
        } catch (Exception e) {
            log.warn "${device.label?device.label:device.name}: failed to read level/state: ${e.message})";
        }
    }
}

/* 
 *
 */

def doStateRefresh()
{
    unscheduleStateRefresh(); // Avoid overlaps.

    stateRefreshLevel();
    
    if (settings.refreshInterval > 0) {
        scheduleStateRefresh(); // the next one.
    }
    
    // TEMPORARY
    /*
    try {
        showStaticAttributes();
    } catch (Exception e2) {
        log.warn "${device.label?device.label:device.name}: failed to read state attribute(s): ${e2.message})";
    }
    */
}

/*
 * scheduleAutoLogOff()
 *
 * Schedule auto off of logging. 
 *
 */

def scheduleAutoLogOff() {
    runIn(1800, doAutoLogOff);
}

/*
 * scheduleAutoLogOff()
 *
 * Schedule auto off of logging. 
 *
 */

def unscheduleAutoLogOff() {
    try {
        unschedule(doAutoLogOff);
    } catch (Exception ex) {
        log.warn "${device.label?device.label:device.name}: on trying to unschedule auto logging off: ${ex.message}"    
    }
}

/*
 * doAutoLogOff()
 *
 * Schedule auto off of logging. 
 *
 */

def doAutoLogOff()
{
    unscheduleAutoLogOff();
    
    // This is called when the config preferences are updated by the user.

    log.info "Configuration read."
    log.warn "${device.label?device.label:device.name}: debug logging is: ${settings.logEnable == true}";
    
    // If logging has been enabled, schedule to turn it off in 30 mins. 
    // Stops logs getting full.
    
    if (settings.logEnable) scheduleAutoLogOff();
}

/*
 * updated()
 *
 * Framework event: called when any device parameters have been updated. 
 *
 */

def updated() {
    log.debug "${device.label?device.label:device.name}: update...";
    unschedule();
    scheduleStateRefresh();
    scheduleAutoLogOff();
}

/*
 * logsOff()
 *
 * Turns off logging when not needed (so that the logs are not flooded). 
 *
 */

def logsOff() {
    log.warn "${device.label?device.label:device.name}: debug logging disabled.";
    device.updateSetting("logEnable", [value: "false", type: "bool"]);
}


