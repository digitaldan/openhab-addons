# UniFi Protect Binding

![logo](doc/logo.png)

This binding integrates Ubiquiti UniFi Protect into openHAB.
It connects to your Protect NVR/CloudKey/UNVR and provides live events and configurable settings for Cameras, Floodlights, and Sensors.

It uses the official Protect Integration API over HTTPS and WebSocket with a Bearer Token.

## Supported Things

- `unifiprotect:nvr` (Bridge): The Protect NVR/CloudKey/UNVR.
  Required to discover and manage child devices.
- `unifiprotect:camera`: A Protect camera.
  Channels are added dynamically based on device capabilities (mic, HDR, smart detection, PTZ, etc.).
- `unifiprotect:light`: A Protect Floodlight.
- `unifiprotect:sensor`: A Protect environmental/contact sensor.

## Discovery

- Add the `NVR` bridge by entering its Hostname/IP and an Integration API Token.
- Once the NVR is ONLINE, Cameras, Floodlights, and Sensors are discovered automatically and appear in the Inbox.
- Approve discovered things to add them to your system.
  Manual creation is also possible using `deviceId`.

## Binding Configuration

There are no global binding settings.
All configuration is on the NVR bridge and on individual things.

## Thing Configuration

### NVR (Bridge) `unifiprotect:nvr`

| Name | Type | Description | Default | Required | Advanced |
|------|------|-------------|---------|----------|----------|
| hostname | text | Hostname or IP address of the NVR | N/A | yes | no |
| token | text | Bearer token used for API/WebSocket authentication | N/A | yes | no |

How to get the Token:

- In the UniFi Protect UI, go to Settings → Control Plane → Integrations and create an API token.
- Copy the token and paste it into the NVR bridge configuration in openHAB.

![Protect API key creation](doc/keys.png)



### Camera `unifiprotect:camera`

| Name | Type | Description | Required |
|------|------|-------------|----------|
| deviceId | text | Unique device identifier of the camera | yes |

Cameras are best added via discovery.
For manual setup, the `deviceId` can be taken from the discovery inbox, logs, or Protect API.

### Floodlight `unifiprotect:light`

| Name | Type | Description | Required |
|------|------|-------------|----------|
| deviceId | text | Unique device identifier of the floodlight | yes |

### Sensor `unifiprotect:sensor`

| Name | Type | Description | Required |
|------|------|-------------|----------|
| deviceId | text | Unique device identifier of the sensor | yes |

## Channels

Below are the channels exposed by each thing type.
Some camera channels are created dynamically depending on device capabilities.

### NVR (Bridge)

No channels.

### Camera

- The following are dynamically created depending on features.

| Channel ID | Item Type | RW | Description |
|------------|-----------|----|-------------|
| micvolume | Number | RW | Microphone volume (0-100) |
| videomode | String | RW | Camera video mode (e.g., `default`, `highFps`, `sport`, `slowShutter`, `lprReflex`, `lprNoneReflex`) |
| hdrtype | String | RW | HDR mode (`auto`, `on`, `off`) |
| osdname | Switch | RW | Show name on OSD |
| osddate | Switch | RW | Show date on OSD |
| osdlogo | Switch | RW | Show logo on OSD |
| ledenabled | Switch | RW | Enable/disable camera status LED |
| activepatrolslot | Number | RW | Active PTZ patrol slot (set 0 to stop) |
| motioncontact | Contact | R | Motion state (OPEN = motion detected) |
| motionsnapshot | Image | R | Snapshot captured around motion event |
| smartaudiodetectcontact | Contact | R | Smart audio detection active state |
| smartaudiodetectsnapshot | Image | R | Snapshot captured around smart audio detection |
| smartdetectzonecontact | Contact | R | Smart zone detection active state |
| smartdetectzonesnapshot | Image | R | Snapshot captured around smart zone detection |
| smartdetectlinecontact | Contact | R | Smart line detection active state |
| smartdetectlinesnapshot | Image | R | Snapshot captured around smart line detection |
| smartdetectloitercontact | Contact | R | Smart loiter detection active state |
| smartdetectloitersnapshot | Image | R | Snapshot captured around smart loiter detection |

Trigger channels (for rules):

| Trigger Channel ID | Payload (if any) | Description |
|--------------------|------------------|-------------|
| motionstart | none | Motion started |
| motionend | none | Motion ended |
| smartaudiodetectstart | `alrmSmoke`, `alrmCmonx`, `alrmSiren`, `alrmBabyCry`, `alrmSpeak`, `alrmBark`, `alrmBurglar`, `alrmCarHorn`, `alrmGlassBreak`, `none` | Smart audio detection started |
| smartaudiodetectend | `alrmSmoke`, `alrmCmonx`, `alrmSiren`, `alrmBabyCry`, `alrmSpeak`, `alrmBark`, `alrmBurglar`, `alrmCarHorn`, `alrmGlassBreak`, `none` | Smart audio detection ended |
| smartdetectzonestart | `person`, `vehicle`, `package`, `licensePlate`, `face`, `animal`, `none` | Zone smart detection started |
| smartdetectzoneend | `person`, `vehicle`, `package`, `licensePlate`, `face`, `animal`, `none` | Zone smart detection ended |
| smartdetectlinestart | `person`, `vehicle`, `package`, `licensePlate`, `face`, `animal`, `none` | Line smart detection started |
| smartdetectlineend | `person`, `vehicle`, `package`, `licensePlate`, `face`, `animal`, `none` | Line smart detection ended |
| smartdetectloiterstart | `person`, `vehicle`, `package`, `licensePlate`, `face`, `animal`, `none` | Loiter smart detection started |
| smartdetectloiterend | `person`, `vehicle`, `package`, `licensePlate`, `face`, `animal`, `none` | Loiter smart detection ended |

### Floodlight

| Channel ID | Item Type | RW | Description |
|------------|-----------|----|-------------|
| light | Switch | RW | Main floodlight on/off (forces light) |
| isdark | Switch | R | Scene is currently dark |
| pirmotion | Trigger | - | PIR motion event |
| lastmotion | DateTime | R | Timestamp of last motion |
| lightmode | String | RW | Light mode (`always`, `motion`, `off`) |
| enableat | String | RW | When mode is relevant (`fulltime`, `dark`) |
| indicatorenabled | Switch | RW | Status LED indicator on floodlight |
| pirduration | Number | RW | How long the light stays on after motion (milliseconds) |
| pirsensitivity | Number | RW | PIR motion sensitivity (0-100) |
| ledlevel | Number | RW | LED brightness level (1-6) |

### Sensor

| Channel ID | Item Type | RW | Description |
|------------|-----------|----|-------------|
| battery | Number | R | Battery charge level (%) |
| contact | Contact | R | Contact state (OPEN/CLOSED) |
| temperature | Number:Temperature | R | Ambient temperature |
| humidity | Number | R | Ambient humidity |
| illuminance | Number:Illuminance | R | Ambient light (Lux) |
| alarmcontact | Contact | R | Smoke/CO alarm contact (OPEN = alarming) |
| waterleakcontact | Contact | R | Water leak contact (OPEN = leak) |
| tampercontact | Contact | R | Tamper contact (OPEN = tampering) |

Trigger channels (for rules):

| Trigger Channel ID | Payload (if any) | Description |
|--------------------|------------------|-------------|
| opened | `door`, `window`, `garage`, `leak`, `none` | Sensor opened |
| closed | `door`, `window`, `garage`, `leak`, `none` | Sensor closed |
| alarm | `smoke`, `CO` (optional) | Smoke/CO alarm event |
| waterleak | `door`, `window`, `garage`, `leak`, `none` | Water leak detected |
| tamper | none | Tampering detected |

## Full Examples (Textual Configuration)

Replace the IDs with your own thing and item names.

### Things (`.things`)

```
Bridge unifiprotect:nvr:myNvr "UniFi Protect NVR" [ hostname="192.168.1.10", token="YOUR_LONG_TOKEN" ] {
	Thing unifiprotect:camera:frontdoor [ deviceId="60546f80e4b0abcd12345678" ]
	Thing unifiprotect:light:driveway [ deviceId="60a1b2c3d4e5f67890123456" ]
	Thing unifiprotect:sensor:garagedoor [ deviceId="60112233445566778899aabb" ]
}
```

### Items (`.items`)

```
// Camera
Number  Cam_Front_MicVolume        "Mic Volume [%d]"                   { channel="unifiprotect:camera:myNvr:frontdoor:micvolume" }
String  Cam_Front_VideoMode        "Video Mode [%s]"                    { channel="unifiprotect:camera:myNvr:frontdoor:videomode" }
String  Cam_Front_HDR              "HDR [%s]"                           { channel="unifiprotect:camera:myNvr:frontdoor:hdrtype" }
Switch  Cam_Front_OSD_Name         "OSD Name"                           { channel="unifiprotect:camera:myNvr:frontdoor:osdname" }
Switch  Cam_Front_OSD_Date         "OSD Date"                           { channel="unifiprotect:camera:myNvr:frontdoor:osddate" }
Switch  Cam_Front_OSD_Logo         "OSD Logo"                           { channel="unifiprotect:camera:myNvr:frontdoor:osdlogo" }
Switch  Cam_Front_LED              "Status LED"                         { channel="unifiprotect:camera:myNvr:frontdoor:ledenabled" }
Number  Cam_Front_PatrolSlot       "PTZ Patrol Slot [%d]"               { channel="unifiprotect:camera:myNvr:frontdoor:activepatrolslot" }
Contact Cam_Front_Motion           "Motion [%s]"                        { channel="unifiprotect:camera:myNvr:frontdoor:motioncontact" }
Image   Cam_Front_MotionSnapshot   "Motion Snapshot"                    { channel="unifiprotect:camera:myNvr:frontdoor:motionsnapshot" }

// Floodlight
Switch  Light_Driveway_OnOff       "Driveway Light"                     { channel="unifiprotect:light:myNvr:driveway:light" }
Switch  Light_Driveway_IsDark      "Is Dark"                            { channel="unifiprotect:light:myNvr:driveway:isdark" }
DateTime Light_Driveway_LastMotion "Last Motion [%1$ta %1$tR]"          { channel="unifiprotect:light:myNvr:driveway:lastmotion" }
String  Light_Driveway_Mode        "Mode [%s]"                          { channel="unifiprotect:light:myNvr:driveway:lightmode" }
String  Light_Driveway_EnableAt    "Enable At [%s]"                     { channel="unifiprotect:light:myNvr:driveway:enableat" }
Switch  Light_Driveway_Indicator   "Indicator LED"                      { channel="unifiprotect:light:myNvr:driveway:indicatorenabled" }
Number  Light_Driveway_PIR_Dur     "PIR Duration [%.0f ms]"             { channel="unifiprotect:light:myNvr:driveway:pirduration" }
Number  Light_Driveway_PIR_Sens    "PIR Sensitivity [%.0f]"             { channel="unifiprotect:light:myNvr:driveway:pirsensitivity" }
Number  Light_Driveway_LED_Level   "LED Level [%.0f]"                   { channel="unifiprotect:light:myNvr:driveway:ledlevel" }

// Sensor
Number  Sensor_Garage_Battery      "Battery [%.0f %%]"                  { channel="unifiprotect:sensor:myNvr:garagedoor:battery" }
Contact Sensor_Garage_Contact      "Contact [%s]"                       { channel="unifiprotect:sensor:myNvr:garagedoor:contact" }
Number:Temperature Sensor_Garage_T "Temperature [%.1f %unit%]"         { channel="unifiprotect:sensor:myNvr:garagedoor:temperature" }
Number  Sensor_Garage_Humidity     "Humidity [%.0f %%]"                 { channel="unifiprotect:sensor:myNvr:garagedoor:humidity" }
Number:Illuminance Sensor_Garage_L "Illuminance [%.0f lx]"              { channel="unifiprotect:sensor:myNvr:garagedoor:illuminance" }
Contact Sensor_Garage_Alarm        "Alarm [%s]"                         { channel="unifiprotect:sensor:myNvr:garagedoor:alarmcontact" }
Contact Sensor_Garage_Leak         "Leak [%s]"                          { channel="unifiprotect:sensor:myNvr:garagedoor:waterleakcontact" }
Contact Sensor_Garage_Tamper       "Tamper [%s]"                        { channel="unifiprotect:sensor:myNvr:garagedoor:tampercontact" }
```

### Sitemap (`.sitemap`)

``` 
sitemap home label="Home" {
	Frame label="Front Door Camera" {
		Text item=Cam_Front_Motion
		Image item=Cam_Front_MotionSnapshot
	}
	Frame label="Driveway Floodlight" {
		Switch item=Light_Driveway_OnOff
		Text item=Light_Driveway_IsDark
		Text item=Light_Driveway_LastMotion
		Selection item=Light_Driveway_Mode mappings=[always="Always", motion="Motion", off="Off"]
		Selection item=Light_Driveway_EnableAt mappings=[fulltime="Full time", dark="Dark"]
		Setpoint item=Light_Driveway_PIR_Sens minValue=0 maxValue=100 step=1
		Setpoint item=Light_Driveway_LED_Level minValue=1 maxValue=6 step=1
	}
	Frame label="Garage Sensor" {
		Text item=Sensor_Garage_Contact
		Text item=Sensor_Garage_T
		Text item=Sensor_Garage_Humidity
		Text item=Sensor_Garage_L
		Text item=Sensor_Garage_Battery
	}
}
```

### Rules (`.rules`)

Examples showing trigger channels.

``` 
// Camera motion start/end
rule "Front door motion alert"
when
	Channel "unifiprotect:camera:myNvr:frontdoor:motionstart" triggered
then
	logInfo("protect", "Front door motion started")
end

rule "Front door motion ended"
when
	Channel "unifiprotect:camera:myNvr:frontdoor:motionend" triggered
then
	logInfo("protect", "Front door motion ended")
end

// Camera smart detection with payload
rule "Front door smart zone detect"
when
	Channel "unifiprotect:camera:myNvr:frontdoor:smartdetectzonestart" triggered
then
	// Access payload from the trigger channel event (person, vehicle, package, licensePlate, face, animal, none)
	val String payload = receivedEvent.getEvent()
	logInfo("protect", "Smart zone detection started: {}", payload)
end

// Camera smart audio detect with payload
rule "Front door smart audio detect"
when
	Channel "unifiprotect:camera:myNvr:frontdoor:smartaudiodetectstart" triggered
then
	val String payload = receivedEvent.getEvent() // alrmSmoke, alrmCmonx, alrmSiren, alrmBabyCry, alrmSpeak, alrmBark, alrmBurglar, alrmCarHorn, alrmGlassBreak, none
	logInfo("protect", "Smart audio detected: {}", payload)
end

// Camera doorbell ring with payload filtering
rule "Front doorbell pressed"
when
	Channel "unifiprotect:camera:myNvr:frontdoor:ring" triggered PRESSED
then
	logInfo("protect", "Doorbell pressed")
end

// Or handle any ring payload generically
rule "Front doorbell ring generic"
when
	Channel "unifiprotect:camera:myNvr:frontdoor:ring" triggered
then
	val String payload = receivedEvent.getEvent() // PRESSED, RELEASED
	logInfo("protect", "Doorbell ring event: {}", payload)
end

// Floodlight PIR motion trigger
rule "Driveway PIR motion"
when
	Channel "unifiprotect:light:myNvr:driveway:pirmotion" triggered
then
	logInfo("protect", "Driveway PIR motion")
	// Optionally turn on the light for a bit
	sendCommand(Light_Driveway_OnOff, ON)
	createTimer(now.plusSeconds(30), [ | sendCommand(Light_Driveway_OnOff, OFF) ])
end

// Sensor opened/closed with payload
rule "Garage sensor opened"
when
	Channel "unifiprotect:sensor:myNvr:garagedoor:opened" triggered
then
	val String payload = receivedEvent.getEvent() // door, window, garage, leak, none
	logInfo("protect", "Garage sensor opened: {}", payload)
end

rule "Garage sensor closed"
when
	Channel "unifiprotect:sensor:myNvr:garagedoor:closed" triggered
then
	val String payload = receivedEvent.getEvent() // door, window, garage, leak, none
	logInfo("protect", "Garage sensor closed: {}", payload)
end

// Sensor water leak
rule "Garage water leak"
when
	Channel "unifiprotect:sensor:myNvr:garagedoor:waterleak" triggered
then
	val String payload = receivedEvent.getEvent() // door, window, garage, leak, none
	logWarn("protect", "Water leak detected by garage sensor: {}", payload)
end
```
