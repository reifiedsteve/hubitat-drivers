/*
 * ----------------------------------------------------------------------------------
 *
 *                             `_`     `_,_`  _'                                  `,`  
 *                            -#@@- >O#@@@@u B@@>                                 8@E  
 *    :)ilc}` `=|}uccccVu}r"   VQz `@@#Mhzk= |8M   `=v}ucccccuY),    `~v}uVVcccccV#@$  
 *  ^Q@#EMqK.I#@QRdMqqMdRQ@@Q, Q@B `@@BqqqW^ W@@` e@@QRdMMMMbEQ@@8: i#@BOMqqqqqqqM#@$  a
 *  D@@`    )@@x          <@@T Q@B `@@q      W@@`>@@l          :@@z`#@d           Q@$  
 *  D@#     ?@@@##########@@@} Q@B `@@q      W@@`^@@@##########@@@y`#@W           Q@$  
 *  0@#     )@@d!::::::::::::` Q@B `@@M      W@@`<@@E!::::::::::::``#@b          `B@$  
 *  D@#     `m@@#bGPP}         Q@B `@@q      W@@` 3@@BbPPPV         y@@QZPPPPPGME#@8=  
 *  *yx       .*icywwv         )yv  }y>      ~yT   .^icywyL          .*]uywwwwycL^-   
 *                                                                                    
 *      (c) 2021 Reified Ltd.    W: www.reified.co.uk     E: info@reified.co.uk
 *
 * ----------------------------------------------------------------------------------
 *
 * MQTT eRora Lightstrip Driver for Hubitat C-7
 *
 * Hubitat MQTT API details at https://docs.hubitat.com/index.php?title=MQTT_Interface
 *
 * ----------------------------------------------------------------------------------
 */
 
metadata
{
    definition(
        name: "eRora Lightstrip (MQTT)", 
        namespace: "Reified", 
        author: "Steve Morley"       // , 
        // importUrl: "https://raw.githubusercontent.com/reified/..../mqttMultiLevelSwitch.groovy"
    ) {
        capability "Initialize"          // initialize()
        capability "Actuator"            // -
        capability "Light"               // on() off()
        capability "Switch"              // on() off() 
        capability "SwitchLevel"         // setLevel(level, duration)
        // capability "ChangeLevel"      // startLevelChange(dir), stopLevelChange()
        capability "ColorControl"        // setColor(???)  setHue(hue)   setSaturation(sat)
        capability "LightEffects"        // setEffect(preset#), setNextEffect(), setPreviousEffect()
        capability "ColorTemperature"    // setColorTemperature(kelvin, level, transition-time)
        // capability "Alarm"               // strobe(), off()     /* Oops! off() is for "Light" and "Alarm" - how to distinquish? Custom attributes?
    }
}

preferences
{
    section()
    {
        input (
            name: "brokerAddress", 
            type: "text", 
            title: "MQTT Broker Domain/IP", 
            defaultValue: "mqtt.local", 
            required: true
        )
        
        input (
            name: "brokerPort",
            type: "number",
            title: "MQTT Broker Port",
            defaultValue: 1883,
            required: true
        )
        
        input (
            name: "brokerProtocol",
            type: "text",
            title: "Protocol",
            defaultValue: "tcp",
            required: true
        )
        
        input (
            name: "brokerTopicPrefix",
            type: "text",
            title: "Topic Prefix",
            defaultValue: null,
            required: true
        )
        
        // ---
        
        input (
            name: "requiresCredentials",
            type: "bool",
            title: "Requires credentials",
            defaultValue: false,
            required: false
        )
        
        input (
            name: "brokerUserName",
            type: "text",
            title: "User Name (optional)",
            defaultValue: null,
            required: false
        )
        
        input (
            name: "brokerPassword",
            type: "text",
            title: "Password (optional)",
            defaultValue: null,
            required: false
        )
        
        // ---

        input (
            name: "clientCert",
            type: "text",
            title: "Client Certificate (optional)",
            required: false
        )
        
        input (
            name: "caCert",
            type: "text",
            title: "CA Certificate (optional)",
            required: false
        )
        
        input (
            name: "privateKey",
            type: "text",
            title: "Private Key (optional)",
            required: false
        )
    }
    
    section()
    {
        input (
            name: "logEnable",
            type: "bool",
            title: "Enable debug logging",
            defaultValue: true
        )
    }
}

/***********************************************************************************
 *
 * initialize()
 *
 ***********************************************************************************/

def initialize()
{
    logDebug("initializing.")

    connect()
    subscribeToTopics()

    logDebug("initialized.")
}

/***********************************************************************************
 *
 * configure()
 *
 ***********************************************************************************/

def configure()
{
    logDebug("Configuring.")

    disconnect()
    
    logDebug("configure: state is ${state}")
    
    connect()
    subscribeToTopics()
    
    logDebug("Finished configuring.")
}

/***********************************************************************************
 *
 * updated()
 *
 ***********************************************************************************/

def updated()
{
    logDebug("Updated...")
    logWarn("Debug logging is: ${settings.logEnable == true}")
    
    if (settings.logEnable) runIn(1800, logsOff)
}

/***********************************************************************************
 *
 * connect()
 *
 ***********************************************************************************/

def connect()
{
    logDebug("Connecting...")
    
    try {
        connectToBroker()
        boolean connected = interfaces.mqtt.isConnected() 
        logDebug("MQTT broker is ${connected ? 'connected' : 'disconnected'}")
    }
    
    catch (Exception ex) {
        logError("Cannot connect: ${ex}")
    }

    if (settings.logEnable) 
        logDebug("Finished connecting.")
}

/***********************************************************************************
 *
 * disconnect()
 *
 ***********************************************************************************/

def disconnect()
{
    try {
        if (interfaces.mqtt.isConnected()) {
            interfaces.mqtt.disconnect()
        }
        state.connected = false
    }
    
    catch (Exception ex) {
        logError("On disconnect: ${ex}")
    }
}

/***********************************************************************************
 *
 * mqttClientStatus(String message)
 *
 ***********************************************************************************/

def mqttClientStatus(String message)
{
    // This method is called with any status messages from the MQTT client connection
    // (disconnections, errors during connect, etc) The string that is passed to this
    // method with start with "Error" if an error occurred or "Status" if this is
    // just a status message.

    logDebug("mqttClientStatus received ${message}")
    
    // device.updateDataValue("lastMQTTStatus", "${message}")

    if (message.startsWith('Status')) {
        onStatus(message)
    }
    
    else if (message.startsWith('Error')) {
        onError(message)
    }
}

/***********************************************************************************
 *
 * onStatus(message)
 *
 ***********************************************************************************/

def onStatus(message) {
    logDebug("MQTT Status: '${message}'")
}

/***********************************************************************************
 *
 * onError(message)
 *
 ***********************************************************************************/

def onError(message) {
    logError("MQTT Error: '${message}'")
}

/***********************************************************************************
 *
 * connectToBroker()
 *
 ***********************************************************************************/

def connectToBroker()
{   
    hostStr = settings.brokerAddress + ":" + brokerPort.toString()
    urlStr = settings.brokerProtocol + "://" + hostStr

    // If the previous connection has not yet been fully teared down at the broker, 
    // then trying to reconnect with the same client ID as previously can result in
    // the connection being refused. One way to avoid this is to generate a client
    // ID which is partially random each time.
    
    clientID = "hubitat-eRora-" + (Math.abs(new Random().nextInt() % 1000000)).toString()

    userName = ""
    password = ""

    if (settings.requiresCredentials) {
        userName = settings.brokerUserName
        password = settings.brokerPassword
    }

    options = [:]

    /*
    if (settings.lastWillTopic)
        options["lastWillTopic"] = settings.lastWillTopic
    
    if (settings.lastWillQoS)
        options["lastWillQoS"] = settings.lastWillQos
    
    if (settings.lastWillMessage)
        options["lastWillMessage"] = settings.lastWillMessage
    */
    
    if (settings.clientCert) {
        String clientCert = wrapIfMissing(
            settings.clientCert,
            "-----BEGIN CERTIFICATE-----\n",
            "\n-----END CERTIFICATE-----"
        )
        options['clientCertificate'] = clientCert
    }

    if (settings.caCert) {
        String caCert = wrapIfMissing(
            settings.caCert,
            "\n-----END CERTIFICATE-----",
            "-----BEGIN CERTIFICATE-----\n",
        )        
        options['caCertificate'] = caCert
    }

    if (settings.privateKey) {
        String privateKey = wrapIfMissing(
            settings.privateKey,
            "-----BEGIN RSA PRIVATE KEY-----\n",
            "\n-----END RSA PRIVATE KEY-----"
        )
        options['privateKey'] = privateKey    
    }

    // Connect options, see https://docs.hubitat.com/index.php?title=MQTT_Interface#Overview
    
    try {  
        interfaces.mqtt.connect(options, urlStr, clientID, userName, password)
    }
    
    catch (Exception ex) {
        logWarn("Attempt to connect failed: ${ex.message} (${ex})")
        logInfo("Re-attempting to connect...")
        runIn(5, connectToBroker)
    }
}

/***********************************************************************************
 *
 * subscribeToTopics()
 *
 ***********************************************************************************/

def subscribeToTopics()
{
    logDebug("Subscribing to topics...")
    
    // We subscribe to a topic that provides the target position
    // Because there may be more than one user/client controlling
    // the blind and if it is us that has not set the current
    // target, we still want to know what it is.
    
    if (isTopicProvided(settings.brokerTopicPrefix)) {
        subscribeToTopic(constructTopic(settings.brokerTopicPrefix, "power"));
        subscribeToTopic(constructTopic(settings.brokerTopicPrefix, "brightness"));
        subscribeToTopic(constructTopic(settings.brokerTopicPrefix, "white"));
        subscribeToTopic(constructTopic(settings.brokerTopicPrefix, "rgb"));
        subscribeToTopic(constructTopic(settings.brokerTopicPrefix, "preset"));        
    }    
}

/***********************************************************************************
 *
 * subscribeToTopics()
 *
 ***********************************************************************************/

def subscribeToTopic(topic)
{
    if (settings.logEnable) {
        logDebug("Subscribing to '${topic}'")
    }

    interfaces.mqtt.subscribe(topic)
}

/***********************************************************************************
 *
 * refresh()
 *
 ***********************************************************************************/

def refresh() {
    logDebug("refresh: state is ${state}")
}

/***********************************************************************************
 *
 * parse(String message)
 *
 ***********************************************************************************/

def parse(String message)
{
    // It's not our thread, so protect it.

    logDebug("MQTT incoming: '${message}'")

    try 
    {
        def map = interfaces.mqtt.parseMessage(message)
    
        logDebug("message parsed is ${map}")
    
        topic = map['topic']
        payload = map['payload']
    
        logDebug("Topic: ${topic}, payload: '${payload}'")

        processIncoming(topic, payload)
    }
    
    catch (Exception ex) {
        logWarn("Attempt to parse MQTT message (topic '${topic}', payload: '${payload}'): ${ex.message} (${ex})")
    }
}

/***********************************************************************************
 *
 * processIncoming(topic, payload)
 *
 ***********************************************************************************/

def processIncoming(topic, payload)
{
    logDebug("Topic and payload incoming: '${topic}', '${payload}'")
    
    if (topic.endsWith("/power")) {
        isOn = parseBool(payload);
        sendEvent(name: "switch", value: isOn ? "on" : "off", isStateChange: true)
        this.power = isOn
    }
    
    else if (topic.endsWith("/brightness")) {
        brightness = Integer.parseInt(payload);
        sendEvent(name: "level", value: brightness, isStateChange: true) // The dimmer perspective.
        this.brightness = brightness 
    }
    
    else if (topic.endsWith("/white")) {
        kelvin = Integer.parseInt(payload);
        sendEvent(name: "colorMode", value: "W", displayed:false)
        sendEvent(name: "colorTemperature", value: kelvin, isStateChange: true)
        this.kelvin = kelvin
    }

    else if (topic.endsWith("/rgb")) {
        if (payload.startsWith('#')) {
            payload = payload.substring(1);   
        }
        payload = "#" + payload.toUpperCase();
        rgbTuple = hubitat.helper.ColorUtils.hexToRGB(payload)
        logDebug("rgb tuple is ${rgbTuple}");        
        hsvTuple = hubitat.helper.ColorUtils.rgbToHSV(rgbTuple)
        logDebug("hsv value is ${hsvTuple}");        
        // sendEvent(name: "RGB", value: rgb, isStateChange: true) 
        sendEvent(name: "colorMode", value: "RGB", displayed:false)
        sendEvent(name: "hue", value: hsvTuple[0], isStateChange: true) 
        sendEvent(name: "saturation", value: hsvTuple[1], isStateChange: true)
        // sendEvent(name: "level", value: hsvTuple[2], isStateChange: true) // The dimmer perspective.
        this.rgb = rgb
    }
    
    else if (topic.endsWith("/preset")) {
        // Could be two parts.
        // First part is the preset number.
        // Second (optional) part is the preset name.
        parts = payload.split(" ");
        if (parts.length > 0) {
            presetNo = Integer.parseInt(parts[0];
            sendEvent(name: "effectName", value: payload, isStateChange: true);
            this.preset = preset;
        }
    }

    /***
    else if (topic.endsWith("alert")) {
        isOn = parseBool(payload);
        state = isOn ? "strobe" : "off"
        sendEvent(name: "alarm", value: state, isStateChange: true) 
    }
    ***/
    
    else {
        logWarn("Ignored MQTT topic (topic ${topic}), payload '${payload}'");
    }
}

/***********************************************************************************
 *
 * isTopicProvided(requestedTopic)
 *
 ***********************************************************************************/

def isTopicProvided(requestedTopic)
{
    boolean provided = false
    
    if (requestedTopic) {
        topic = requestedTopic.trim()
        provided = (topic.length() > 0)
    }
    
    return provided
}

/***********************************************************************************
 *
 * constructTopic(prefix, subTopic)
 *
 ***********************************************************************************/

def constructTopic(prefix, subTopic)
{
    topic = prefix
    
    if (!prefix.endsWith("/")) {
        topic = topic + "/"
    }
    
    topic = topic + subTopic
}

/***********************************************************************************
 *
 * on()
 *
 ***********************************************************************************/

def on() {
    logDebug("Performing on().")
    sendEvent(name: "switch", value: "on", descriptionText: "${device.displayName} is on")
    deviceSetOnOff(true)
}

/***********************************************************************************
 *
 * off()
 *
 ***********************************************************************************/

def off() {
    logDebug("Performing off().")
    sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} is off")
    deviceSetOnOff(false)
}

/***********************************************************************************
 *
 * setLevel(level)
 *
 ***********************************************************************************/

def setLevel(level) {
    logDebug("Performing setLevel(${level}) - passing to publishSetBrightness(${level})).")
    deviceSetBrightness(level)    
}

/***********************************************************************************
 *
 * setColorTemperture(colorMap)
 *
 ***********************************************************************************/

def setColorTemperature(kelvin)
{
    logDebug("Performing setColorTemperature(${kelvin}).")
    deviceSetWhite(kelvin);    
}

/***********************************************************************************
 *
 * setColor(colorMap)
 *
 ***********************************************************************************/

def setColor(hslTuple) {
    logDebug("Performing setColor(${hslTuple}).")
    rgbTuple = hubitatHsvToRGB(hslTuple)
    deviceSetRGBColor(rgbTuple)
}

/***********************************************************************************
 *
 * setHue(hue)
 *
 ***********************************************************************************/

def setHue(hue) {
    logDebug("Performing setHue(${hue})   - NOTE: NOT HANDLED YET!.")
}

/***********************************************************************************
 *
 * setSaturation(sat)
 *
 ***********************************************************************************/

def setSaturation(sat) {
    logDebug("Performing setSaturation(${sat})   - NOTE: NOT HANDLED YET!.")
}

/***********************************************************************************
 *
 * setEffect(presetNo)
 *
 ***********************************************************************************/

def setEffect(presetNo) {
    logDebug("Performing setEffect(${presetNo})   - NOTE: NOT HANDLED YET!.")
     publishSetPreset(presetNo); 
}

/***********************************************************************************
 *
 * setPreviousEffect()
 *
 ***********************************************************************************/

def setPreviousEffect() {
    logDebug("Performing setPreviousEffect()   - NOTE: NOT HANDLED YET!.")
}

/***********************************************************************************
 *
 * setNextEffect()
 *
 ***********************************************************************************/

def setNextEffect() {
    logDebug("Performing setNextEffect()   - NOTE: NOT HANDLED YET!.")
}

/***********************************************************************************
 *
 * deviceSetOnOff(on)
 *
 ***********************************************************************************/

def deviceSetOnOff(on) {
    payload = renderPowerPayload(on)
    sendCommandToDevice("power", payload)
}

/***********************************************************************************
 *
 * deviceSetBrightness(position)
 *
 ***********************************************************************************/

def deviceSetBrightness(brightness) {
    payload = renderBrightnessPayload(brightness)
    sendCommandToDevice("brightness", payload)
}

/***********************************************************************************
 *
 * deviceetWhite(kelvin)
 *
 ***********************************************************************************/

def deviceSetWhite(kelvin) {
    payload = renderWhitePayload(kelvin)
    sendCommandToDevice("white", payload)
}

/***********************************************************************************
 *
 * deviceSetRGBColor(position)
 *
 ***********************************************************************************/

def deviceSetRGBColor(rgbTuple) {
    payload = renderRGBPayload(rgbTuple)
    sendCommandToDevice("rgb", payload)
}

/***********************************************************************************
 *
 * deviceSetHSLColor(position)
 *
 ***********************************************************************************/

def deviceSetHSLColor(hslTuple) {
    payload = renderHSLPayload(hslTuple)
    sendCommandToDevice("hsl", payload)
}

/***********************************************************************************
 *
 * deviceSetPreset(presetNo)
 *
 ***********************************************************************************/

def deviceSetPreset(presetNo) {
    payload = renderPresetPayload(presetNo)
    sendCommandToDevice("preset", payload)
}   

/***********************************************************************************
 *
 * renderPowerPayload(template, position)
 *
 ***********************************************************************************/

def renderPowerPayload(on) {
    return on ? "on" : "off";
}

/***********************************************************************************
 *
 * renderBrightnessPayload(brightness)
 *
 ***********************************************************************************/

def renderBrightnessPayload(brightness) {
    return brightness.toString();
}

/***********************************************************************************
 *
 * renderWhitePayload(rgb)
 *
 ***********************************************************************************/

def renderWhitePayload(kelvin) {
    return kelvin.toString();    
}

/***********************************************************************************
 *
 * renderRGBPayload(rgb)
 *
 ***********************************************************************************/

def renderRGBPayload(rgbTuple) {
    logDebug("Rendering rgb ${rgbTuple}...")
    // hexStr = hubitat.helper.ColorUtils.rgbToHEX(rgbTuple)
    hexStr = rgbToHEX(rgbTuple)
    logDebug("RGB as hex is ${hexStr}.")
    return hexStr;
}

/***********************************************************************************
 *
 * renderRGBPayload(rgb)
 *
 ***********************************************************************************/

def renderHSLPayload(hsvTuple) {
    logDebug("Rendering hsl ${hsvTuple}...")
    // hexStr = hubitat.helper.ColorUtils.hsvToHEX(hsvTuple)
    hexStr = hsvToHEX(hsvTuple)
    logDebug("HSL as hex is ${hexStr}.")
    return hexStr;
}

/***********************************************************************************
 *
 * renderPresetPayload(presetNo)
 *
 ***********************************************************************************/

def renderPresetPayload(preset) {
    return preset.toString()    
}

/***********************************************************************************
 *
 * sendToDevice(subTopic, payload)
 *
 ***********************************************************************************/

def sendCommandToDevice(attributeName, payload)
{
    // Only perform the publishing if the user has provided us with an
    // MQTT topic as a destination to which we can publish.
    
    publishRequired = isTopicProvided(settings.brokerTopicPrefix)

    if (publishRequired)
    {
        subTopic = attributeName + "/set"
        topic = constructTopic(settings.brokerTopicPrefix, subTopic)
        
        // Note: we do *not* want the message to be retained by the broker. This is
        // a command and has to be enacted when requested - we don't want it to be
        // unexpectedly honoured hours later if the blind has been turned off.
        
        // We don't mind duplicate messages. Sending a set position message twice is 
        // fine as any subsequent to the first are merely redundant and without
        // harmful consequence. So let's not demand more of MQTT than we need and
        // keep the handshaking/traffic to a minimum. Hence a QoS of 1.

        serviceLevel = 1
        retainMode = false

        tryPublish(topic, payload, qos = serviceLevel, retain = retainMode)
    }
    
    return publishRequired
}

/***********************************************************************************
 *
 * tryPublish(targetTopic, payload, QoS, retain)
 *
 ***********************************************************************************/

def tryPublish(targetTopic, payload, QoS, retain)
{
    success = false
    
    try
    {
        // Checking if we're connected would just be a race.
        // boolean connected = interfaces.mqtt.isConnected()
        
        topic = targetTopic.trim()
        message = (payload ? payload : "")
        
        logDebug("MQTT publish to '${topic}' message '${message}'")
        
        interfaces.mqtt.publish(topic, message, (int)QoS, retain)
        success = true
    } 
    
    catch (Exception ex) {
        logWarn("Publish to ${targetTopic} failed: ${ex.message} (${ex})")
    }
    
    return success
}

/***********************************************************************************
 *
 * rgbToHEX(hsvTuple)
 *
 ***********************************************************************************/

// In lieu of hubitat..helper.ColorUtils.rgbToHEX which seems absent in the version
// of Hubitat I'm using.

def rgbToHEX(rgbTuple)
{
    // logDebug("rgbToHEX(${rgbTuple})")
    r = rgbTuple["red"]
    g = rgbTuple["green"]
    b = rgbTuple["blue"]
    return tupleToHex(r, g, b)
}

/***********************************************************************************
 *
 * hsvToHEX(hsvTuple)
 *
 ***********************************************************************************/

// In lieu of hubitat..helper.ColorUtils.hsvToHEX which seems absent in the version
// of Hubitat I'm using.

def hsvToHEX(hsvTuple)
{
    h = hsvTuple["hue"]
    s = hsvTuple["saturation"]
    v = hsvTuple["level"]
    return tupleToHex(h, s, v)
}

/***********************************************************************************
 *
 * tupleToHex(a, b, c)
 *
 ***********************************************************************************/

def tupleToHex(a, b, c) {
    String aHex = String.format("%02X", a)    
    String bHex = String.format("%02X", b)    
    String cHex = String.format("%02X", c)
    return aHex + bHex + cHex;
}

/***********************************************************************************
 *
 * hubitatHsvToRGB(h, s, v)
 *
 ***********************************************************************************/

def hubitatHsvToRGB(hsvTuple) {
    // logDebug("converting Hsv ${hsvTuple} to RGB...")
    h = hsvTuple["hue"]
    s = hsvTuple["saturation"]
    v = hsvTuple["level"]
    rgbTuple = hsvToRGB((int)(3.6 * h), (int)s, (int)v)    
    // logDebug("hsv ${hsvTuple} converted to rgb ${rgbTuple}")
    return rgbTuple
}

/***********************************************************************************
 *
 * hsvToRGB(h, s, v)
 *
 ***********************************************************************************/

def hsvToRGB(hue, sat, vol)
{
    H = 1.0 * hue
    S = 1.0 * sat
    V = 1.0 * vol
    
    if (H < 0) H = 0
    if (H > 360) H = 360
    
    if (S < 0) S = 0
    if (S > 100) S = 100
    
    if (V < 0) V = 0
    if (V > 100) V = 100
    
    s = 1.0 * S / 100
    v = 1.0 * V / 100
    C = 1.0 * s * v
    // X = C * (1 - abs(fmod(H/60.0, 2) - 1))
    X = C * (1 - Math.abs( (((float)H/60.0) % 2) - 1 ))
    m = v-C

    r = 0.0
    g = 0.0
    b = 0.0
    
    if (H >= 0 && H < 60){
        r = C
        g = X
        b = 0
    }
    
    else if (H >= 60 && H < 120){
        r = X
        g = C
        b = 0
    }
    
    else if (H >= 120 && H < 180){
        r = 0
        g = C
        b = X;
    }
    
    else if (H >= 180 && H < 240){
        r = 0
        g = X
        b = C
    }
    
    else if (H >= 240 && H < 300){
        r = X
        g = 0
        b = C
    }
    
    else{
        r = C
        g = 0
        b = X
    }
    
    R = (int)((r+m)*255)
    G = (int)((g+m)*255)
    B = (int)((b+m)*255)
    
    return [red: R, green: G, blue: B];
}

/***********************************************************************************
 *
 * parseBool(state)
 *
 ***********************************************************************************/

def boolean parseBool(str) {
    boolean state = false;
    if ((str.equalsIgnoreCase("on")) || (str.equalsIgnoreCase("true")) || (str.equalsIgnoreCase("yes")) || (str.equalsIgnoreCase("enable")) || (str.equalsIgnoreCase("enabled")) || (str.equals("1"))) {
        state = true
    } 
    return state;   
}

/***********************************************************************************
 *
 * logDebug(str)
 *
 ***********************************************************************************/

void logDebug(str) {
   if (settings.logEnable) log.debug(str)
}

/***********************************************************************************
 *
 * logWarn(str)
 *
 ***********************************************************************************/

def logWarn(str) {
   log.warn(str)
}
    
/***********************************************************************************
 *
 * logError(str)
 *
 ***********************************************************************************/

def logError(str) {
   log.warn(str)
}
    
/***********************************************************************************
 *
 * logInfo(str)
 *
 ***********************************************************************************/

def logInfo(str) {
   if (settings.logEnable) log.info(str)
}
    
/***********************************************************************************
 *
 * logsOff()
 *
 ***********************************************************************************/

def logsOff() {
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

/***********************************************************************************
 *
 * wrapIfMissing(str, header, footer)
 *
 ***********************************************************************************/

def wrapIfMissing(str, header, footer) {
    return appendIfMissing(prependIfMissing(str, header), footer)    
}

/***********************************************************************************
 *
 * prependIfMissing(str, prefix)
 *
 ***********************************************************************************/

def prependIfMissing(str, prefix) {
    return str.startsWith(prefix) ? str : (prefix + str)
}

/***********************************************************************************
 *
 * appendIfMissing(str, postfix)
 *
 ***********************************************************************************/

def appendIfMissing(str, postfix) {
    return str.endsWith(postfix) ? str : (str + postfix)
}

