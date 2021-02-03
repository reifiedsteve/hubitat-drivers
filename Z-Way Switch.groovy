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
 * Z-way Switch Driver for Hubitat C-7
 *
 * Calls Z-way REST API to perform switch on/off and dimming.
 * 
 * For technical details of Z-Way REST API, see https://zwave.me/z-way
 *
 * Notes: (1) Z-Way seems to require admin privileges (Z-Way 2.2.5) for control.
 *        (2) The device ID can be found via Z-Way Expert UI -> Devices.
 *        (3) Not sure of the purpose of the instance ID (default value of 0 works fine).
 *        (4) Logging is enabled via UI, but auto-disables after a while (to avoid
 *            the logs getting accidentally overwhelmed).
 *
 *  Author: S.J.Morley  Date: 2021-01-27  E: steve@reified.co.uk  
 *
 * ----------------------------------------------------------------------------------
 */

/*
 * Define the device driver capabilities. 
 *
 */

metadata
{
    definition(name: "Z-way Switch", namespace: "Reified", author: "Steve Morley") {
        capability "Initialize"
        capability "Light"
        capability "Switch"
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
        input name: "refreshInterval", type: "number", title: "Refresh interval (seconds)", defaultValue: 10, required: true
    }
    
    section("Diagnostics") {
        input name: "logEnable",  type: "bool",   title: "Enable debug logging", defaultValue: true
    }
}

/*
 * zWaySendCommand(username, password, ipAddr, ipPort, commandPath)
 *
 * Send a Z-Way specific HHTP GET to a Z-Way device.
 *      username -  username for BasicAuth login (optional)
 *      password -  password for basicAuth login (optional)
 *        ipAddr -  IP address of the Z-Way device
 *        ipPort -  Port no. for the Z-way device
 *   commandPath -  Device-specific path-part representing the command.
 *
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
    
    synchronized(this) { // In case we say request attributes on a schedule.
        httpGet(  
            uri: "http://" + host,
            path: commandPath,
            headers: headerItems
        ) { resp -> response = resp; }       
    }
    
    return response;
}

/*
 * zWayRead(commandPath)
 *
 * Send a Z-Way specific command to return some data.
 *   commandPath -  Device-specific path-part representing the item to return.
 * Returns: Json object for the requested data.
 *
 */

Object zwayRead(commandPath)
{ 
    def response = zwaySendCommand(
        settings.username, settings.password, 
        settings.ipAddr, settings.ipPort, 
        commandPath
    );
    
    if (!response.success) {
        throw new Exception("${device.label?device.label:device.name}: unsuccessful response to zwayRead from ${commandPath}");
    }
    
    if (logEnable) {
        log.debug "${device.label?device.label:device.name}: response data: ${response.data}";
    }
    
    return response.data;
}

/*
 * zWayWrite(commandPath)
 *
 * Send a Z-Way specific command to write some data.
 *   commandPath -  Device-specific path-part representing the item write command.
 * Returns: Json object for the requested data.
 *
 */

def void zwayWrite(commandPath)
{     
    def response = zwaySendCommand(
        settings.username, settings.password, 
        settings.ipAddr, settings.ipPort, 
        commandPath
    );
    
    if (!response.success) {
        throw new Exception("${device.label?device.label:device.name}: unsuccessful response to zwayWrite from ${commandPath}");
    }
}

/*
 * zwayReadAttribute(attributeName)
 *
 * Send a Z-Way specific command to read an attribute of the device.
 *   attributeName -  The name of the attribute.
 * Returns: The value of the requested attribute.
 *
 */

def Object zwayReadAttribute(attributeName)
{
    String commandPath = "/ZWaveAPI/Run/devices[${settings.deviceID}].instances[${settings.instanceID}].Basic.data.${attributeName}.value";
    // Synonymous to: "/ZWaveAPI/Run/devices[${settings.deviceID}].instances[${settings.instanceID}].commandClasses[38].data.${attributeName}.value"

    log.debug "${device.label?device.label:device.name}: reading attribute '${attributeName}' from device ${settings.deviceID} instance ${settings.instanceID}.";
    Object obj = zwayRead(commandPath);

    return obj;
}

/*
 * on()
 *
 * Framework event: respond to an ON request.
 */

def on()
{ 
    String commandPath = "/ZWaveAPI/Run/devices[${settings.deviceID}].instances[${settings.instanceID}].Basic.Set(${255})";
    
    try {
        zwayWrite(commandPath);
        // Do an immediate notification: refresh will overwrite if it didn't take.
        // (Speed of response versus (unlikely) failure to write trade-off). 
        // (Actually, not yet figured out how to read actual on/off state from device,
        // so this is endEvent is essential.)
        sendEvent(name: "switch", value: "on", isStateChange: true);
    }
    
    catch (Exception e) {
        log.warn "${device.label?device.label:device.name}: failed to switch on: ${e.message})";
    }   
}

/*
 * off()
 *
 * Framework event: respond to an OFF request.
 */

def off()
{
    String commandPath = "/ZWaveAPI/Run/devices[${deviceID}].instances[${instanceID}].Basic.Set(${0})";

    try {
        zwayWrite(commandPath);
        // Do an immediate notification: refresh will overwrite if it didn't take.
        // (Speed of response versus (unlikely) failure to write trade-off). 
        // (Actually, not yet figured out how to read actual on/off state from device,
        // so this is endEvent is essential.)
        sendEvent(name: "switch", value: "off", isStateChange: true);
    }
    
    catch (Exception e) {
        log.warn "${device.label?device.label:device.name}: failed to switch off: ${e.message})";
    }   
}

/*
 * isOn()
 *
 * Determine if the device is on.
 * Returns: true if on, flse if off.
 *
 */

def boolean isOn()
{
    // Not sure how to do this. The level value seems to be the 
    // only exposed device variable, but that is independent
    // from on/off (as level is preserved on the device 
    // between on-off-on cycles). 
    // (Q. Do all MultiLevelSwitches act this way? Or at
    // least are expressed that way via zway?).
    
    throw new Exception("${device.label?device.label:device.name}: not implemented: isOn()");
}

/*
 * refreshAttribute(attributeName)
 *
 * Retrieves the latest value of an attribute and send it as an event. The result is
 * that it is shown on the device's configuration page.
 *   attributeName -  The name of the attribute.
 *
 */

def refreshAttribute(attributeName) {
    sendEvent(name: attributeName, value: zwayReadAttribute(attributeName).toString(), isStateChange: true);
}

/*
 * initialize()
 *
 * Framework event: request to initialize the dimmer. 
 *
 */

def initialize() {
    // Retrieve non-changing attributes here.
    // These are potentially useful, showing aspects of the dimmer.
    refreshAttribute("version");
    refreshAttribute("security");
}

/*
 * scheduleAutoLogOff()
 *
 * Schedule auto off of logging. 
 *
 */

def scheduleAutoLogOff()
{
    try {
        unschedule(logsOff);
    } catch (Exception ex) {
        log.warn "${device.label?device.label:device.name}: on trying to unschedule auto logging off: ${ex.message}"    
    }
    
    // This is called when the config preferences are updated by the user.

    log.info "Configuration read."
    log.warn "${device.label?device.label:device.name}: debug logging is: ${logEnable == true}";
    
    // If logging has been enabled, schedule to turn it off in 30 mins. 
    // Stops logs getting full.
    
    if (logEnable) runIn(1800, logsOff);
}

/*
 * updated()
 *
 * Framework event: called when any device parameters have been updated. 
 *
 */

def updated() {
    log.debug "${device.label?device.label:device.name}: update...";
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


