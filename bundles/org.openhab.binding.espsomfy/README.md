# ESPSomfy Binding

This binding integrates [ESPSomfy-RTS](https://github.com/rstrouse/ESPSomfy-RTS) firmware devices with openHAB.
ESPSomfy-RTS runs on ESP32 hardware and controls Somfy RTS motorized shades, blinds, awnings, and shutters via radio.

The binding communicates with the device over HTTP for commands and WebSocket for real-time state updates.

## Supported Things

| Thing Type   | ThingTypeUID | Description                                                    |
|--------------|--------------|----------------------------------------------------------------|
| Bridge       | `controller` | An ESPSomfy-RTS controller device (the ESP32 hardware)         |
| Shade        | `shade`      | A single Somfy RTS motorized shade, blind, awning, or shutter  |
| Group        | `group`      | A group of shades that can be controlled together               |

## Discovery

The binding supports automatic discovery of ESPSomfy-RTS controllers via mDNS.
Controllers register themselves with the service type `_espsomfy_rts._tcp`.

Once a controller bridge is added and online, its shades and groups are automatically discovered via the device's HTTP API.

## Thing Configuration

### Controller (Bridge)

| Name            | Type    | Description                                          | Default | Required | Advanced |
|-----------------|---------|------------------------------------------------------|---------|----------|----------|
| hostname        | text    | Hostname or IP address of the ESPSomfy-RTS device    | N/A     | yes      | no       |
| port            | integer | HTTP port of the device                              | 80      | no       | yes      |
| password        | text    | PIN or password if device security is enabled        | N/A     | no       | no       |
| refreshInterval | integer | Interval in seconds to poll the device for state     | 30      | no       | yes      |

### Shade

| Name    | Type    | Description                                              | Default | Required |
|---------|---------|----------------------------------------------------------|---------|----------|
| shadeId | integer | The shade ID as configured on the ESPSomfy-RTS device    | N/A     | yes      |

### Group

| Name    | Type    | Description                                              | Default | Required |
|---------|---------|----------------------------------------------------------|---------|----------|
| groupId | integer | The group ID as configured on the ESPSomfy-RTS device    | N/A     | yes      |

## Channels

### Controller Channels

| Channel   | Type   | Read/Write | Description                                     |
|-----------|--------|------------|-------------------------------------------------|
| version   | String | R          | Firmware version of the ESPSomfy-RTS controller |

### Shade Channels

| Channel    | Type           | Read/Write | Description                                          |
|------------|----------------|------------|------------------------------------------------------|
| position   | Rollershutter  | RW         | Shade position (0% = open, 100% = closed)            |
| tilt       | Rollershutter  | RW         | Tilt angle for venetian blinds                       |
| direction  | Number         | R          | Movement direction (-1 = opening, 0 = stopped, 1 = closing) |
| myPosition | Number         | R          | Configured favorite (My) position (-1 if not set)    |
| command    | String         | W          | Virtual remote command (My, Up, Down, Prog, etc.)    |
| sunny      | Switch         | R          | Sun sensor state (for shades with sun sensor)        |
| windy      | Switch         | R          | Wind sensor state (for shades with sun sensor)       |

### Group Channels

| Channel    | Type           | Read/Write | Description                                          |
|------------|----------------|------------|------------------------------------------------------|
| position   | Rollershutter  | RW         | Group position (0% = open, 100% = closed)            |
| direction  | Number         | R          | Movement direction (-1 = opening, 0 = stopped, 1 = closing) |
| myPosition | Number         | R          | Configured favorite (My) position (-1 if not set)    |
| command    | String         | W          | Virtual remote command (My, Up, Down, Prog, etc.)    |

### Virtual Remote Commands

The `command` channel accepts the following values:

| Command  | Description                                  |
|----------|----------------------------------------------|
| My       | Move to favorite position or stop movement   |
| Up       | Open the shade                               |
| Down     | Close the shade                              |
| Prog     | Enter programming mode                       |
| SunFlag  | Set sun flag for sensor behavior             |
| Flag     | Set flag                                     |
| StepUp   | Open one step                                |
| StepDown | Close one step                               |
| Toggle   | Toggle last direction                        |

## Full Example

### Thing Configuration

```java
Bridge espsomfy:controller:mycontroller "ESPSomfy Controller" [ hostname="192.168.1.100" ] {
    Thing shade  0 "Living Room Shade"  [ shadeId=0 ]
    Thing shade  1 "Bedroom Blind"      [ shadeId=1 ]
    Thing group  0 "All Shades"         [ groupId=0 ]
}
```

### Item Configuration

```java
Rollershutter LivingRoom_Shade_Position  "Living Room Shade"     { channel="espsomfy:shade:mycontroller:0:position" }
Rollershutter LivingRoom_Shade_Tilt      "Living Room Tilt"      { channel="espsomfy:shade:mycontroller:0:tilt" }
Number        LivingRoom_Shade_Direction "Direction [%d]"        { channel="espsomfy:shade:mycontroller:0:direction" }
Number        LivingRoom_Shade_MyPos     "My Position [%.0f]"    { channel="espsomfy:shade:mycontroller:0:myPosition" }
String        LivingRoom_Shade_Command   "Command"               { channel="espsomfy:shade:mycontroller:0:command" }
Switch        LivingRoom_Shade_Sunny     "Sun Sensor"            { channel="espsomfy:shade:mycontroller:0:sunny" }

Rollershutter AllShades_Position         "All Shades"            { channel="espsomfy:group:mycontroller:0:position" }
String        AllShades_Command          "Group Command"         { channel="espsomfy:group:mycontroller:0:command" }
```

### Sitemap Configuration

```perl
sitemap espsomfy label="ESPSomfy" {
    Frame label="Living Room" {
        Slider    item=LivingRoom_Shade_Position
        Slider    item=LivingRoom_Shade_Tilt
        Text      item=LivingRoom_Shade_Direction
        Selection item=LivingRoom_Shade_Command mappings=[My="My", Prog="Prog", StepUp="Step Up", StepDown="Step Down"]
    }
    Frame label="Groups" {
        Slider item=AllShades_Position
    }
}
```
