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
        capability "Light"               // on() off()    // DO we need this as well as Swithc?
		capability "Switch"              // on() off() 
        capability "SwitchLevel"         // setLevel(level, duration)
        capability "ColorControl"        // setColor(???)  setHue(hue)   setSaturationsat)
		capability "LightEffects"        // setEffect(preset#), setNextEffect(), setPreviousEffect()
		capability "ColorTemperature"    // setColorTemperature(kelvin, level, transition-time)
		// capability "Alarm"               // strobe(), off()     /* Oops! off() is for "Light" and "Alarm" - how to distinquish? Custom attributes?
    }
}

preferences
{
    section()
	{
		input name: "brokerURL", type: "text", 
            title: "MQTT Broker URL", defaultValue: "mqtt.local", required: true
        
		input name: "brokerPort", type: "number",
            title: "MQTT Broker Port", defaultValue: 1883, required: true

		input name: "brokerProtocol", type: "text",
            title: "Protocol", defaultValue: "tcp", required: true

		input name: "brokerTopicPrefix", type: "text",
			title: "Topic Prefix", defaultValue: "", required: true
			
        // ---
        
        input name: "requiresCredentials", type: "bool",
            title: "Requires credentials", defaultValue: false, required: false
        
        input name: "brokerUserName", type: "text",
            title: "User Name (optional)", defaultValue: null, required: false

        input name: "brokerPassword", type: "text",
            title: "Password (optional)", defaultValue: null, required: false

        // ---

        input name: "clientCert", type: "text",
            title: "Client Certificate (optional)", required: false
        
        input name: "caCert", type: "text",
            title: "CA Certificate (optional)", required: false
        
        input name: "privateKey", type: "text",
            title: "Private Key (optional)", required: false
        
        // ---

        input name: "logEnable", type: "bool",
            title: "Enable debug logging", defaultValue: true
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

    state.targetPosition = 0
    state.actualPosition = 0

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
    hostStr = settings.brokerURL + ":" + brokerPort.toString()
    urlStr = settings.brokerProtocol + "://" + hostStr

    // If the previous connection has not yet been fully teared down at the broker, 
    // then trying to reconnect with the same client ID as previously can result in
    // the connection being refused. One way to avoid this is to generate a client
    // ID which is partially random each time.
    
    clientID = "hubitat-shade-" + (Math.abs(new Random().nextInt() % 1000000)).toString()

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
    
    if (isTopicProvided(settings.brokerTopicPrefix))
	{
		topic = constructTopic(settings.brokerTopicPrefix, "#");
		
        if (settings.logEnable) {
            logDebug("Subscribing to '${topic}'")
        }
		
        interfaces.mqtt.subscribe(topic)
    }    
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
        logWarn("Attempt to parse MQTT message (topic ${topic}, payload: ${payload}): ${ex.message} (${ex})")
    }
}

/***********************************************************************************
 *
 * processIncoming(topic, payload)
 *
 ***********************************************************************************/

def processIncoming(topic, payload)
{
	if (topic.endsWith("power")) {
		isOn = parseBoolPayload(payload);
        sendEvent(name: "switch", value: isOn, isStateChange: true)
        this.power = isOn
	}
	
	else if (topic.endsWith("brightness")) {
		brightness = Integer.parseInt(payload);
        sendEvent(name: "level", value: brightness, isStateChange: true) // The dimmer perspective.
		this.brightness = brightness 
	}
	
	else if (topic.endsWith("white")) {
		kelvin = Integer.parseInt(payload);
        sendEvent(name: "colortemperature", value: kelvin, isStateChange: true)
		this.kelvin = kelvin
	}

	else if (topic.endsWith("rgb")) {
		rgb = Integer.parseInt(payload, 16);
        sendEvent(name: "RGB", value: rgb, isStateChange: true)
		this.rgb = rgb
	}
	
	else if (topic.endsWith("preset")) {
		presetNo = Integer.parseInt(payload);
        sendEvent(name: "effectName", value: payload, isStateChange: true)	
		this.preset = preset
	}

	/***
	else if (topic.endsWith("alert")) {
		isOn = parseBoolPayload(payload);
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
	publishSetOnOff(true)
}

/***********************************************************************************
 *
 * off()
 *
 ***********************************************************************************/

def off() {
    logDebug("Performing off().")
	sendEvent(name: "switch", value: "off", descriptionText: "${device.displayName} is off")
	publishSetOnOff(false)
}

/***********************************************************************************
 *
 * setLevel(level)
 *
 ***********************************************************************************/

def setLevel(level) {
    logDebug("Performing setLevel(${level}) - passing to publishSetBrightness(${level})).")
    publishSetBrightness(level)    
}

/***********************************************************************************
 *
 * setColor(colorMap)
 *
 ***********************************************************************************/

def setColor(colorMap) {
    logDebug("Performing setColor(${colorMap})   - NOTE: NOT HANDLED YET!.")
}

/***********************************************************************************
 *
 * publishSetOnOff(on)
 *
 ***********************************************************************************/

def publishSetOnOff(on)
{
    // Only perform the publishing if the user has provided us with an
    // MQTT topic as a destination to which we can publish.
    
    publishRequired = isTopicProvided(settings.brokerTopicPrefix)

    if (publishRequired)
    {
		topic = constructTopic(settings.brokerTopicPrefix, "power/set")
		payload = renderPowerPayload(on)
		
        // Note: we do *not* want the message to be retained by the broker. This is
        // a command and has to be enacted when requested - we don't want it to be
        // unexpectedly honoured hours later if the blind has been turned off.
        
        // We don't mind duplicate messages. Sending a set position message twice is 
        // fine as any subsequent to the first are merely redundant and without
        // harmful consequence. So let's not demand more of MQTT than we need and
        // keep the handshaking/traffic to a minimum. Hence a QoS of 1.

		tryPublish(topic, payload, qos=1, retain = false)
	}
}

/***********************************************************************************
 *
 * publishSetBrightness(position)
 *
 ***********************************************************************************/

def publishSetBrightness(brightness)
{
    // Only perform the publishing if the user has provided us with an
    // MQTT topic as a destination to which we can publish.
    
    publishRequired = isTopicProvided(settings.brokerTopicPrefix)

    if (publishRequired)
    {
		topic = constructTopic(settings.brokerTopicPrefix, "brightness/set")
        payload = renderBrightnessPayload(brightness)
        
        // Note: we do *not* want the message to be retained by the broker. This is
        // a command and has to be enacted when requested - we don't want it to be
        // unexpectedly honoured hours later if the blind has been turned off.
        
        // We don't mind duplicate messages. Sending a set position message twice is 
        // fine as any subsequent to the first are merely redundant and without
        // harmful consequence. So let's not demand more of MQTT than we need and
        // keep the handshaking/traffic to a minimum. Hence a QoS of 1.
        
        tryPublish(topic, payload, qos=1, retain=false)
    }
    
    return publishRequired
}

/***********************************************************************************
 *
 * publishSetBrightness(position)
 *
 ***********************************************************************************/

def publishSetColour(colour)
{
    // Only perform the publishing if the user has provided us with an
    // MQTT topic as a destination to which we can publish.
    
    publishRequired = isTopicProvided(settings.setPositionPubTopic)

    if (publishRequired)
    {
		topic = constructTopic(settings.brokerTopicPrefix, "rgb/set")   /* does this need to be hsv? I think so! */
        payload = renderColourPayload(brightness)
        
        // Note: we do *not* want the message to be retained by the broker. This is
        // a command and has to be enacted when requested - we don't want it to be
        // unexpectedly honoured hours later if the blind has been turned off.
        
        // We don't mind duplicate messages. Sending a set position message twice is 
        // fine as any subsequent to the first are merely redundant and without
        // harmful consequence. So let's not demand more of MQTT than we need and
        // keep the handshaking/traffic to a minimum. Hence a QoS of 1.
        
        tryPublish(settings.setPositionPubTopic, payload, qos=1, retain=false)
    }
    
    return publishRequired
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
 * renderRGBPayload(rgb)
 *
 ***********************************************************************************/

def renderRGBPayload(rgb) {
	return Integer.toHexString(rgb);
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
        
        logDebug("Publish to '${topic}' message '${message}'")
        
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

