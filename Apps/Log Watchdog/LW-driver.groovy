/**
 *  ****************  Log Watchdog Driver  ****************
 *
 *  Design Usage:
 *  This driver opens a webSocket to capture Log info.
 *
 *  Copyright 2019-2020 Bryan Turcotte (@bptworld)
 *  
 *  This App is free.  If you like and use this app, please be sure to mention it on the Hubitat forums!  Thanks.
 *
 *  Remember...I am not a programmer, everything I do takes a lot of time and research (then MORE research)!
 *  Donations are never necessary but always appreciated.  Donations to support development efforts are accepted via: 
 *
 *  Paypal at: https://paypal.me/bptworld
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  If modifying this project, please keep the above header intact and add your comments/credits below - Thank you! -  @BPTWorld
 *
 *  App and Driver updates can be found at https://github.com/bptworld/Hubitat
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  Special thanks to @dan.t for his sample code for making the websocket connection.
 *
 *  Changes:
 *
 *  1.1.3 - 06/27/20 - Massive overhaul of the logic
 *  1.1.2 - 06/27/20 - Improvements to connection and to the dashboard list
 *  1.1.1 - 06/26/20 - Added code to reconnect the websocket if something goes wrong
 *  1.1.0 - 06/26/20 - Tighting up the code
 *  1.0.9 - 06/24/20 - More changes
 *  1.0.8 - 06/24/20 - Tons of little changes
 *  1.0.7 - 10/08/19 - Now handles only 1 keyset per child app
 *  1.0.6 - 09/07/19 - Added some error catching
 *  1.0.5 - 09/05/19 - Getter better!
 *  1.0.4 - 09/05/19 - Trying some new things
 *  1.0.3 - 09/03/19 - Added 'does not contain' keywords
 *  1.0.2 - 09/02/19 - Evolving fast!
 *  1.0.1 - 09/01/19 - Major changes to the driver
 *  1.0.0 - 08/31/19 - Initial release
 *
 */

metadata {
	definition (name: "Log Watchdog Driver", namespace: "BPTWorld", author: "Bryan Turcotte", importUrl: "https://raw.githubusercontent.com/bptworld/Hubitat/master/Apps/Log%20Watchdog/LW-driver.groovy") {
   		capability "Actuator"
       
        attribute "status", "string"
        attribute "bpt-lastLogMessage", "string"       
        attribute "bpt-logData", "string"        
        attribute "numOfCharacters", "number"
        attribute "keywordInfo", "string"
        attribute "appStatus", "string"
        
        command "connect"
        command "close"
        command "clearData"
        command "keywordInfo"
        command "appStatus"
    }
    preferences() {    	
        section(){
            input name: "about", type: "paragraph", element: "paragraph", title: "<b>Log Watchdog Driver</b>", description: "ONLY click 'Clear Data' to clear the message data."
            input("fontSize", "text", title: "Font Size", required: true, defaultValue: "15")
			input("hourType", "bool", title: "Time Selection (Off for 24h, On for 12h)", required: false, defaultValue: false)
            input("logEnable", "bool", title: "Enable logging", required: true, defaultValue: false)
            input("traceEnable", "bool", title: "Enable Trace", required: true, defaultValue: false)
        }
    }
}

def installed(){
    log.info "Log Watchdog Driver has been Installed"
    clearData()
    initialize()
}

def updated() {
    log.info "Log Watchdog Driver has been Updated"
    initialize()
}

def initialize() {
    log.info "In initialize"
}

def connect() {
	interfaces.webSocket.connect("ws://localhost:8080/logsocket")
}

def close() {
    interfaces.webSocket.close()
    log.warn "Log Watchdog Driver - Closing webSocket"
}

def webSocketStatus(String socketStatus) {
    if(logEnabled) log.debug "In webSocketStatus - socketStatus: ${socketStatus}"
	if(socketStatus.startsWith("status: open")) {
		log.warn "Log Watchdog Driver - Connected"
        sendEvent(name: "status", value: "connected", displayed: true)
        pauseExecution(500)
        state.delay = null
	} else if(socketStatus.startsWith("status: closing")) {
		log.warn "Log Watchdog Driver - Closing connection"
        sendEvent(name: "status", value: "disconnected")
	} else if(socketStatus.startsWith("failure:")) {
		log.warn "Log Watchdog Driver - Connection has failed with error [${socketStatus}]."
        sendEvent(name: "status", value: "disconnected", displayed: true)
        autoReconnectWebSocket()
	} else {
        log.warn "WebSocket error, reconnecting."
        autoReconnectWebSocket()
	}
}

def autoReconnectWebSocket() {
    state.delay = (state.delay ?: 0) + 30    
    if(state.delay > 600) state.delay = 600

    log.warn "Log Watchdog Driver - Connection lost, will try to reconnect in ${state.delay} seconds"
    runIn(state.delay, connect)
}

def keywordInfo(keys) {
    if(traceEnable) log.trace "In keywordInfo"
    
    def (keySet,keySetType,keyword1,sKeyword1,sKeyword2,sKeyword3,sKeyword4,nKeyword1,nKeyword2) = keys.split(";")
    
    state.keyValue = "${keySetType};${keyword1};${sKeyword1};${sKeyword2};${sKeyword3};${sKeyword4};${nKeyword1};${nKeyword2}"

    if(traceEnable) log.trace "In keywordInfo - Recieved ${keySet}"
    if(traceEnable) log.trace "In keywordInfo - keyValue: ${state.keyValue}"
}

def parse(String description) {
    def aStatus = device.currentValue('appStatus')
    if(aStatus == "active") {
        theData = "${description}"
        // This is what the incoming data looks like
        //{"name":"Log Watchdog","msg":"Log Watchdog Driver - Connected","id":365,"time":"2019-11-24 10:05:07.518","type":"dev","level":"warn"}

        def (name, msg, id, time, type, level) = theData.split(",")
        def (msgKey, msgValue) = msg.split(":",2)

        def (nameKey, nameValue) = name.split(":",2)

        msgValue = msgValue.replace("\"","")
        nameValue = nameValue.replace("\"","")
        msgCheck = msgValue.toLowerCase()
        state.msgV = msgValue

        try {
            def (lvlKey, lvlValue) = level.split(":",2)
            lvlValue = lvlValue.replace("\"","").replace("}","")
            lvlCheck = lvlValue.toLowerCase()
        } catch (e) {
            lvlCheck = "-"
        }

// *****************************

        def keyValue = state.keyValue.toLowerCase()
        def (keySetType,keyword1,sKeyword1,sKeyword2,sKeyword3,sKeyword4,nKeyword1,nKeyword2) = keyValue.split(";")
        if(keyword1 == "-") keyword1 = ""
        if(sKeyword1 == "-") sKeyword1 = ""
        if(sKeyword2 == "-") sKeyword2 = ""
        if(sKeyword3 == "-") sKeyword3 = ""
        if(sKeyword4 == "-") sKeyword4 = ""
        if(nKeyword1 == "-") nKeyword1 = ""
        if(nKeyword2 == "-") nKeyword2 = ""
        
        String keyword = keyword1.replace("[","").replace("]","")
        if(logEnable) log.debug "msgCheck: ${msgCheck} - keyword: ${keyword}"
        
        state.kCheck1 = false
        state.kCheck2 = true
        state.match = false

        if(keySetType == "l") {
            try {
                if(lvlCheck.contains("${keyword}")) {
                    if(traceEnable) {
                        keyword1a = keyword.replace("a","@").replace("e","3").replace("i","1").replace("o","0",).replace("u","^")
                        log.trace "In keyword - Found msgCheck: ${keyword1a}"
                    }

                    if(sKeyword1 || sKeyword2 || sKeyword3 || sKeyword4) {
                        if(msgCheck.contains("${sKeyword1}")) {
                            if(traceEnable) log.trace "In Secondary Keyword1: ${sKeyword1} Found! That's GOOD!"
                            state.kCheck1 = true
                        } else if(msgCheck.contains("${sKeyword2}")) {
                            if(traceEnable) log.trace "In Secondary Keyword2: ${sKeyword2} Found! That's GOOD!"
                            state.kCheck1 = true
                        } else if(msgCheck.contains("${sKeyword3}")) {
                            if(traceEnable) log.trace "In Secondary Keyword1: ${sKeyword3} Found! That's GOOD!"
                            state.kCheck1 = true
                        } else if(msgCheck.contains("${sKeyword4}")) {
                            if(traceEnable) log.trace "In Secondary Keyword4: ${sKeyword4} Found! That's GOOD!"
                            state.kCheck1 = true
                        }       
                    } else {
                        state.kCheck1 = true
                    }

                    if(nKeyword1 || nKeyword2) {  
                        if(msgCheck.contains("${nKeyword1}")) {
                            if(traceEnable) log.trace "In Not Keyword1: ${nKeyword1} found! That's BAD!"
                            state.kCheck2 = false
                        } else if(msgCheck.contains("${nKeyword2}")) {
                            if(traceEnable) log.trace "In Not Keyword2: ${nKeyword2} found! That's BAD!"
                            state.kCheck2 = false
                        }
                    }

                    if(traceEnable) log.trace "In keyword: ${keyword1a} - kCheck1: ${state.kCheck1} - kCheck2: ${state.kCheck2}"
                    if(state.kCheck1 && state.kCheck2) {
                        state.match = true
                    } else {
                        state.match = false
                    }
                }

                if(state.match) {
                    if(traceEnable) log.warn "In keyword: ${keyword1a} - Everything is GOOD!"
                    if(traceEnable) log.warn "In keyword: Sending: ${state.msgV}"
                    makeList(nameValue, state.msgV)
                    state.msgV = null
                }
            } catch (e) {
                if(traceEnable) log.trace "In parse - Error to follow!"
                log.error e
            }
        }
        
// **********
            
        if(keySetType == "k") {
            try {
                if(msgCheck.contains("${keyword}")) {
                    if(traceEnable) {
                        keyword1a = keyword.replace("a","@").replace("e","3").replace("i","1").replace("o","0",).replace("u","^")
                        log.trace "In keyword - Found msgCheck: ${keyword1a}"
                    }

                    if(sKeyword1 || sKeyword2 || sKeyword3 || sKeyword4) {
                        if(msgCheck.contains("${sKeyword1}")) {
                            if(traceEnable) log.trace "In Secondary Keyword1: ${sKeyword1} Found! That's GOOD!"
                            state.kCheck1 = true
                        } else if(msgCheck.contains("${sKeyword2}")) {
                            if(traceEnable) log.trace "In Secondary Keyword2: ${sKeyword2} Found! That's GOOD!"
                            state.kCheck1 = true
                        } else if(msgCheck.contains("${sKeyword3}")) {
                            if(traceEnable) log.trace "In Secondary Keyword1: ${sKeyword3} Found! That's GOOD!"
                            state.kCheck1 = true
                        } else if(msgCheck.contains("${sKeyword4}")) {
                            if(traceEnable) log.trace "In Secondary Keyword4: ${sKeyword4} Found! That's GOOD!"
                            state.kCheck1 = true
                        }       
                    } else {
                        state.kCheck1 = true
                    }

                    if(nKeyword1 || nKeyword2) {  
                        if(msgCheck.contains("${nKeyword1}")) {
                            if(traceEnable) log.trace "In Not Keyword1: ${nKeyword1} found! That's BAD!"
                            state.kCheck2 = false
                        } else if(msgCheck.contains("${nKeyword2}")) {
                            if(traceEnable) log.trace "In Not Keyword2: ${nKeyword2} found! That's BAD!"
                            state.kCheck2 = false
                        }
                    }

                    if(traceEnable) log.trace "In keyword: ${keyword1a} - kCheck1: ${state.kCheck1} - kCheck2: ${state.kCheck2}"
                    if(state.kCheck1 && state.kCheck2) {
                        state.match = true
                    } else {
                        state.match = false
                    }
                }

                if(state.match) {
                    if(traceEnable) log.warn "In keyword: ${keyword1a} - Everything is GOOD!"
                    if(traceEnable) log.warn "In keyword: Sending: ${state.msgV}"
                    makeList(nameValue, state.msgV)
                    state.msgV = null
                }
            } catch (e) {
                if(traceEnable) log.trace "In parse - Error to follow!"
                log.error e
            }
        }
    }
}
// *****************************
 
def makeList(nameValue,msgValue) {
    if(traceEnable) log.trace "In makeList - working on - nameValue: ${nameValue} - ${msgValue}"

    try {
        if(state.list == null) state.list = []

        getDateTime()
        last = "${nameValue}::${newDate}::${msgValue}"
        state.list.add(0,last)  

        if(state.list) {
            listSize1 = state.list.size()
        } else {
            listSize1 = 0
        }

        int intNumOfLines = 10
        if (listSize1 > intNumOfLines) state.list.removeAt(intNumOfLines)
        String result1 = state.list.join(",")
        def lines = result1.split(",")

        theData = "<div style='overflow:auto;height:90%'><table style='text-align:left;font-size:${fontSize}px'><tr><td width=20%><td width=1%><td width=10%><td width=1%><td width=68%>"

        for (i=0;i<intNumOfLines && i<listSize1;i++) {
            combined = theData.length() + lines[i].length() + 16
            if(combined < 1000) {
                def (theApp, theTime, theMsg) = lines[i].split("::") 
                theData += "<tr><td>${theApp} <td> - <td>${theTime}<td> - <td>${theMsg}"
            }
        }

        theData += "</table></div>"
        if(logEnable) log.debug "theData - ${theData.replace("<","!")}"       

        dataCharCount1 = theData.length()
        if(dataCharCount1 <= 1024) {
            if(logEnable) log.debug "Log Watchdog Attribute - theData - ${dataCharCount1} Characters"
        } else {
            theData = "Log Watchdog - Too many characters to display on Dashboard (${dataCharCount1})"
        }

        sendEvent(name: "bpt-logData", value: theData, displayed: true)
        sendEvent(name: "numOfCharacters", value: dataCharCount1, displayed: true)
        sendEvent(name: "bpt-lastLogMessage", value: msgValue, displayed: true)
    }
    catch(e1) {
        log.error "Log Watchdog Driver - In makeList - Error to follow!"
        log.error e1    
    }
}

def appStatus(data){
	if(logEnable) log.debug "Log Watchdog Driver - In appStatus"
    sendEvent(name: "appStatus", value: data, displayed: true)
}

def clearData(){
	if(logEnable) log.debug "Log Watchdog Driver - Clearing the data"
    msgValue = "-"
    logCharCount = "0"
    
    state.list = []
    sendEvent(name: "bpt-logData", value: state.list, displayed: true)
	
    sendEvent(name: "bpt-lastLogMessage", value: msgValue, displayed: true)
    sendEvent(name: "numOfCharacters", value: logCharCount, displayed: true)
}

def getDateTime() {
	def date = new Date()
	if(hourType == false) newDate=date.format("MM-d HH:mm")
	if(hourType == true) newDate=date.format("MM-d hh:mm")
    return newDate
}
