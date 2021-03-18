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
 *      (c) 2021 Reified Ltd.    W: www.reified.co.uk     E: info@reified.co.uk
 *
 * ----------------------------------------------------------------------------------
 *
 * MQTT Muktilevel Switch (Dimmer) Driver for Hubitat C-7
 *
 * Hubitat MQTT API details at https://docs.hubitat.com/index.php?title=MQTT_Interface
 *
 * ----------------------------------------------------------------------------------
 */
 
metadata
{
    definition(
        name: "MQTT Window Shade", 
        namespace: "community", 
        author: "Community"       // , 
        // importUrl: "https://raw.githubusercontent.com/reified/..../mqttMultiLevelSwitch.groovy"
    ) {
        capability "Initialize"
        capability "Actuator"
        capability "Window Shade"
        capability "Switch Level"
        capability "Switch"
    }
}

preferences
{
    section()
	{
		input name: "brokerURL", type: "text", 
            title: "MQTT Broker URL", required: true
        
		input name: "brokerPort", type: "number",
            title: "MQTT Broker Port", defaultValue: 1883, required: true

		input name: "brokerProtocol", type: "text",
            title: "Protocol", defaultValue: "tcp", required: true

        // ---
        
        input name: "requiresCredentials", type: "bool",
            title: "Requires credentials", defaultValue: false, required: false
        
        input name: "brokerUserName", type: "text",
            title: "User Name (optional)", defaultValue: null, required: false

        input name: "brokerPassword", type: "text",
            title: "Password (optional)", defaultValue: null, required: false

        // ---

        input name: "setPositionPubTopic", type: "text",
            title: "Control position topic (publish)", defaultValue: "", required: false
        
        input name: "setPositionPayloadTemplate", type: "text",
            title: "Control position payload\n(use \$pos for position)", defaultValue: "$pos", required: false
        
        // ---

        input name: "targetPositionSubTopic", type: "text",
            title: "Target position topic (subscribe)", defaultValue: "", required: false
        
        input name: "targetPositionPayloadIsJSON", type: "bool",
            title: "Is target position payload JSON?", defaultValue: false, required: false
        
        input name: "targetPositionPayloadJSONPath", type: "text",
            title: "Target position JSON Path", defaultValue: "", required: false
        
        // ---

        input name: "actualPositionSubTopic", type: "text",
            title: "Actual position topic (subscribe)", defaultValue: "", required: false
        
        input name: "actualPositionPayloadIsJSON", type: "bool",
            title: "Is actual position payload JSON?", defaultValue: false, required: false
        
        input name: "actualPositionPayloadJSONPath", type: "text",
            title: "Actual position JSON Path (if JSON)", defaultValue: "", required: false
        
        // ---

        /*
        input name: "lastWillTopic", type: "text",
            title: "Last Will Topic", required: false
        
		input name: "lastWillMessage", type: "text",
            title: "Last Will Message", required: false
        
		input name: "lastWillQoS", type: "number",
            title: "Last Will QoS", defaultValue: 0, required: false
		*/
        
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

    logDebug("mqttClientStatus received  ${message}")
    
    // device.updateDataValue("lastMQTTStatus", "${message}")

    if (message.startsWith('Status')) {
        onStatus(message)
    }
    
    else if (message.startsWth('Error')) {
        onError(message)
    }

}

/***********************************************************************************
 *
 * onStatus(message)
 *
 ***********************************************************************************/

def onStatus(message) {
    logDebug("MQTT Status: '{message}'")
}

/***********************************************************************************
 *
 * onError(message)
 *
 ***********************************************************************************/

def onError(message) {
    logError("MQTT Status: '{message}'")
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

    clientID = "client-" + (Math.abs(new Random().nextInt() % 1000) + 1).toString()

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
    
    if (isTopicProvided(settings.targetPositionSubTopic)) {
        topic = settings.targetPositionSubTopic.trim()
        if (settings.logEnable) {
            logDebug("Subscribing to '${topic}'")
        }
        interfaces.mqtt.subscribe(topic)
    }    

    // Here we optionally subscribe to incoming messages that provide
    // us with updates on the blinds actual physical position. This
    // can mean tracking the blind in real-time as it scrolls up/down,
    // perhaps to drive an animation on a UI of the blind as it moves,
    // or (more mundanely) to simply show on the device configuration
    // page.
    
    if (isTopicProvided(settings.actualPositionSubTopic)) {
        topic = settings.actualPositionSubTopic.trim()
        if (settings.logEnable) {
            logDebug("Subscribing to '${topic}'")
        }
        interfaces.mqtt.subscribe(topic)
    }
    
    // Note: both of the above mightbe defined by the user as the 
    // same topic, which is fine. They both in any case flow to the
    // same calback, so regardless of whether or not MQTT overrides
    // th previous subscribe or compliments it, all is ok.
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
    
        logDebug("Topic: ${topic}, payload: ${payload}")

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
    prevTargetPosition = state.targetPosition
    prevActualPosition = state.actualPosition

    positional = parsePayload(topic, payload)
    
    newTargetPosition = positional['target']
    newActualPosition = positional['actual']
    
    logDebug("New positional: target ${newTargetPosition}, actual ${newActualPosition}")

    targetPosition = (newTargetPosition != null) ? newTargetPosition : prevTargetPosition
    actualPosition = (newActualPosition != null) ? newActualPosition : prevActualPosition
    
    logDebug("Update positional: target ${targetPosition}, actual ${actualPosition}")

    updateLocalState(targetPosition, prevActualPosition, actualPosition)
    
    state.targetPosition = targetPosition
    state.actualPosition = actualPosition
}

/***********************************************************************************
 *
 * parsePayload(topic, payload)
 *
 ***********************************************************************************/

def parsePayload(topic, payload)
{
    targetPosition = null
    actualPosition = null

    logDebug("Parsing topic ${topic} with payload ${payload}")

    // Note that we consider that the same topic may contain both the
    // target and actual blind position, hence we must test for both.
    
    if (topic == settings.targetPositionSubTopic) {
        logDebug("Payload indicates a new target position.")
        targetPosition = parseTargetPositionPayload(payload)
    }
    
    if (topic == settings.actualPositionSubTopic) {
        logDebug("Payload indicates an update in actual position.")
        actualPosition = parseActualPositionPayload(payload)
    }
    
    if ((topic != settings.targetPositionSubTopic) && (topic != settings.actualPositionSubTopic)) {
        logError("Unexpected incoming topic: ${topic}")
    }

    positional = makePositional(targetPosition, actualPosition)
    
    logDebug("Returning parsed payload: ${positional}")
    
    return positional
}

/***********************************************************************************
 *
 * makePositional(targetPosition, actualPosition)
 *
 ***********************************************************************************/

def makePositional(targetPosition, actualPosition)
{
    positional = [:]
    
    positional['target'] = targetPosition
    positional['actual'] = actualPosition
    
    return positional
}

/***********************************************************************************
 *
 * updateLocalState(targetPosition, oldActualPosition, newActualPosition)
 *
 ***********************************************************************************/

def updateLocalState(targetPosition, oldActualPosition, newActualPosition)
{
    logDebug("Updating local state with target of ${newTargetPosition} and actual of ${newActualPosition}.")
    logDebug("...from old target of ${oldTargetPosition} and old actual of ${oldActualPosition}.")
    
    //if (oldTargetPosition != newTargetPosition)
    //{
        sendEvent(name: "position", value: targetPosition, isStateChange: true) // The shade perspective.
        sendEvent(name: "level", value: targetPosition, isStateChange: true)    // The dimmer perspective.
        
        // The dimmer perspective is simply an on/off thing.
        sendEvent(name: "switch", value: ((targetPosition > 0) ? "on" : "off"), isStateChange: true)
        
        // The shade perspective has multiple states that are represented.
        // Opened, Opening, Partially-opened, Closing, Closed. But we'll
        // deal with that once we have handled the actual position as the
        // blind state is derived from both target and actual positions.
        
        state.targetPosition = targetPosition   // TODO: reduncdant
    //}

    //if (oldActualPosition != newActualPosition)
    //{
        // The *actual* position per-se is not directly supported by the widget,
        // but we as least make sure we have mantained & exposed state that
        // reflects it (perhaps for any future shade widget that also shows
        // motion progress).
        
        sendEvent(name: "actualPosition", value: newActualPosition, isStateChange: true) 

        state.actualPosition = newActualPosition      // TODO: reduncdant
    //}

    // Now we deal with determining the currect transiton state.
    
    oldBlindState = device.currentValue("windowShade")
    newBlindState = determineTransitionState(targetPosition, newActualPosition)
    
    if (newBlindState != oldBlindState) {
        sendEvent(name: "windowShade", value: newBlindState, isStateChange: true)
        logDebug("Blind state changed fron ${oldBlindState} to ${newBlindState}")    
    } else {
        logDebug("Blind state remains as ${oldBlindState}")    
    }
}

/***********************************************************************************
 *
 * determineTransitionState(targetPosition, actualPosition)
 *
 ***********************************************************************************/

def determineTransitionState(targetPosition, actualPosition)
{
    logDebug("Determining transition for ${actualPosition} to ${targetPosition}")

    transitionState = "unknown"
    
    if (targetPosition > actualPosition) {
        transitionState = "opening"
        logDebug("#####Opening: target is ${targetPosition}, actual is ${actualPosition}")
    }
    
    else if (targetPosition < actualPosition) {
        transitionState = "closing"
        logDebug("#####Closing: target is ${targetPosition}, actual is ${actualPosition}")
        
    }
    
    else if (actualPosition == 0) 
        transitionState = "closed"
        
    else if (actualPosition == 100)
        transitionState = "open"
        
    else
        transitionState = "partially open"
    
    logDebug("Returning transition type of: ${transitionState}")
    
    return transitionState
}

/***********************************************************************************
 *
 * parseTargetPositionPayload(payload)
 *
 ***********************************************************************************/

def parseTargetPositionPayload(payload)
{
    logDebug("Parsing target position payload of '${payload}'")

    position = settings.targetPositionPayloadIsJSON ? 
        parsePositionAsJSON(payload, settings.targetPositionPayloadJSONPath) : 
        parsePositionAsText(payload)
    
    return position
}

/***********************************************************************************
 *
 * parseActualPositionPayload(payload)
 *
 ***********************************************************************************/

def parseActualPositionPayload(payload)
{
    logDebug("Parsing actual position payload of '${payload}'")
    
    position = settings.actualPositionPayloadIsJSON ? 
        parsePositionAsJSON(payload, settings.actualPositionPayloadJSONPath) : 
        parsePositionAsText(payload)

    return position
}

/***********************************************************************************
 *
 * parsePositionAsJSON(payload, jsonPath)
 *
 ***********************************************************************************/

def parsePositionAsJSON(payload, jsonPath)
{
    slurper = new groovy.json.JsonSlurper()
    json = slurper.parseText(payload)
    
    String[] pathElements = jsonPath.split('/')
    n = pathElements.size()
    
    if (n==0) {
        throw new Exception("Missing JSON path element")   
    }
        
    i = 0
    
    element = json.get(pathElements[i])
    
    while (++i < n) {
        element = element.get(pathElements[i])
    }
    
    value = element;
    
    return value
}

/***********************************************************************************
 *
 * parsePositionAsText(payload)
 *
 ***********************************************************************************/

def parsePositionAsText(payload) {
    return payload
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
 * on()
 *
 ***********************************************************************************/

def on() {
    logDebug("Performing on().")
    setPosition(100)
}

/***********************************************************************************
 *
 * off()
 *
 ***********************************************************************************/

def off() {
    logDebug("Performing off().")
    setPosition(0)
}

/***********************************************************************************
 *
 * setLevel(level)
 *
 ***********************************************************************************/

def setLevel(level) {
    logDebug("Performing setLevel(${level}) - passing to setPosition(${level})).")
    setPosition(level)    
}

/***********************************************************************************
 *
 * open()
 *
 ***********************************************************************************/

def open() {
    logDebug("Performing open().")
    setPosition(100)
}

/***********************************************************************************
 *
 * close()
 *
 ***********************************************************************************/

def close() {
    logDebug("Performing close().")
    setPosition(0)
}

/***********************************************************************************
 *
 * setPosition(targetPosition)
 *
 ***********************************************************************************/

def setPosition(targetPosition)
{ 
    logDebug("Performing setPosition(${position}).")

    // First we tell our local state of the change in target 
    // position...then we tell the rest of the world about it,
    // which will include the blind itself, whereever it
    // lives, which should then respond appropritely.
    
    updateLocalState(targetPosition, state.actualPosition, state.actualPosition)   
    publishSetPosition(targetPosition)
}

/***********************************************************************************
 *
 * publishSetPosition(position)
 *
 ***********************************************************************************/

def publishSetPosition(position)
{
    // Only perform the publishing if the user has provided us with an
    // MQTT topic as a destination to which we can publish.
    
    publishRequired = isTopicProvided(settings.setPositionPubTopic)

    if (publishRequired)
    {
        payload = renderPositionPayload(settings.setPositionPayloadTemplate, position)
        
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
 * renderPositionPayload(template, position)
 *
 ***********************************************************************************/

def renderPositionPayload(template, position) {
    return template.replace("\$pos", position.toString())    
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
 * tryPublish(targetTopic, payload, QoS, retain)
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

