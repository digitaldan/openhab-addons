# GrandstreamGDS Binding

_Give some details about what this binding is meant for - a protocol, system, specific device._

_If possible, provide some resources like pictures, a YouTube video, etc. to give an impression of what can be done with this binding. You can place such resources into a `doc` folder next to this README.md._

## Supported Things

_Please describe the different supported things / devices within this section._
_Which different types are supported, which models were tested etc.?_
_Note that it is planned to generate some part of this based on the XML files within ```src/main/resources/OH-INF/thing``` of your binding._

## Discovery

_Describe the available auto-discovery features here. Mention for what it works and what needs to be kept in mind when using it._

## Binding Configuration

_If your binding requires or supports general configuration settings, please create a folder ```cfg``` and place the configuration file ```<bindingId>.cfg``` inside it. In this section, you should link to this file and provide some information about the options. The file could e.g. look like:_

```
# Configuration for the Philips Hue Binding
#
# Default secret key for the pairing of the Philips Hue Bridge.
# It has to be between 10-40 (alphanumeric) characters
# This may be changed by the user for security reasons.
secret=openHABSecret
```

_Note that it is planned to generate some part of this based on the information that is available within ```src/main/resources/OH-INF/binding``` of your binding._

_If your binding does not offer any generic configurations, you can remove this section completely._

## Thing Configuration

_Describe what is needed to manually configure a thing, either through the (Paper) UI or via a thing-file. This should be mainly about its mandatory and optional configuration parameters. A short example entry for a thing file can help!_

_Note that it is planned to generate some part of this based on the XML files within ```src/main/resources/OH-INF/thing``` of your binding._

## Channels

_Here you should provide information about available channel types, what their meaning is and how they can be used._

_Note that it is planned to generate some part of this based on the XML files within ```src/main/resources/OH-INF/thing``` of your binding._

| Trigger Event                                        | Name                                             | description                                                                                                                                                              |
|------------------------------------------------------|--------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| event_open_door_via_card                             | Open Door via Card                               | Indicates that someone opens the door via card or key fob.                                                                                                               |
| event_open_door_via_card_over_wiegand                | Open Door via Card (over Wiegand)                | Indicates that someone opens the door via card or key fob using Wiegand interface connected to GDS.                                                                      |
| event_visiting_log                                   | Visiting Log                                     | Indicates that door has been opened for visitor which pressed door bell button.                                                                                          |
| event_open_door_via_universal_pin                    | Open Door via Universal PIN                      | Indicates that door has been opened successfully using local PIN code via GDS keypad.                                                                                    |
| event_open_door_via_private_pin                      | Open Door via Private PIN                        | Indicates that someone opened the door successfully using their private PIN code via GDS keypad.                                                                         |
| event_open_door_via_guest_pin                        | Open Door via Guest PIN                          | Indicates that a guest used “Guest PIN” code to open the door using GDS keypad.                                                                                          |
| event_open_door_via_di                               | Open Door via DI                                 | Indicates that door has been opened using DI (Digital Input) Signal, such as using a push button.                                                                        |
| event_call_out_log                                   | Call Out Log                                     | Indicates the GDS unit initiated a call out, for example when someone uses the keypad to dial a number or press door bell button which preconfigured destination number. |
| event_call_in_log                                    | Call In Log                                      | Indicates that call has been received by the GDS unit.                                                                                                                   |
| event_call_log_door_bell_call                        | Call Log (Door Bell Call)                        | Indicates that someone has initiated a call using door bell button.                                                                                                      |
| event_open_door_via_card_and_pin                     | Open Door via Card and PIN                       | Indicates that someone used his RFID card or key fob, plus his own private password to authenticate and open the door.                                                   |
| event_keep_door_open_immediately                     | Keep Door Open (Immediately)                     | Key door Open (immediately) action has been performed from the web Interface.                                                                                            |
| event_keep_door_open_scheduled                       | Keep Door Open (Scheduled)                       | Key door Open (immediately) action has been set from the web Interface and the event is triggered.                                                                       |
| event_open_door_via_remote_pin                       | Open Door via Remote PIN                         | Indicates that someone did send remote PIN code to open the door using GDS manager tool for example.                                                                     |
| event_http_api_open_door                             | HTTP API Open Door                               | Indicates that someone did send remote PIN code to open the door HTTP API command.                                                                                       |
| event_motion_detection                               | Motion Detection                                 | Indicates that motion detection is triggered.                                                                                                                            |
| event_di_alarm                                       | DI Alarm                                         | Indicates that alarm IN is triggered.                                                                                                                                    |
| event_dismantle_by_force                             | Dismantle by Force                               | Indicates that the unit has been dismantled by force.                                                                                                                    |
| event_system_up                                      | System up                                        | Indicates that the system is UP                                                                                                                                          |
| event_reboot                                         | Reboot                                           | Indicates that the GDS unit has been rebooted.                                                                                                                           |
| event_reset_clear_all_data                           | Reset (Clear All Data)                           | Factory reset (clear all data) has been performed.                                                                                                                       |
| event_reset_retain_network_data_only                 | Reset (Retain Network Data Only)                 | Factory reset (Retain Network Data Only) has been performed.                                                                                                             |
| event_reset_retain_only_card_information             | Reset (Retain Only Card Information)             | Factory reset (Retain Only Card Information) has been performed.                                                                                                         |
| event_reset_retain_network_data_and_card_information | Reset (Retain Network Data and Card Information) | Factory reset (Retain Network Data and Card Information) has been performed.                                                                                             |
| event_reset_wiegand                                  | Reset (Wiegand)                                  | Factory reset using Wiegand module has been performed on the unit.                                                                                                       |
| event_config_update                                  | Config Update                                    | Indicates that the system’s configuration has been updated.                                                                                                              |
| event_firmware_update                                | Firmware Update (1.0.0.0)                        | Indicates that the system’s firmware has been upgraded.                                                                                                                  |
| event_hostage_alarm                                  | Hostage Alarm                                    | Indicates that someone has entered the hostage alarm PIN code to open the door.                                                                                          |
| event_invalid_password                               | Invalid Password                                 | Indicates that someone has entered wrong password PIN code to open the door for 5 attempts and corresponding alarm action has been triggered.                            |
| event_mainboard_temperature_normal                   | Mainboard Temperature(32°C) Normal               | Indicates that device’s mainboard temperature is normal, (around 32°C).                                                                                                  |
| event_mainboard_temperature_too_low                  | Mainboard Temperature(32°C) Too Low              | Indicates that device’s mainboard temperature is to low.                                                                                                                 |
| event_mainboard_temperature_too_high                 | Mainboard Temperature(32°C) Too High             | Indicates that device’s mainboard temperature is to high.                                                                                                                |
| event_sensor_temperature_normal                      | Sensor Temperature(32°C) Normal                  | Indicates that device’s sensor temperature is normal, (around 32°C).                                                                                                     |
| event_sensor_temperature_too_low                     | Sensor Temperature(32°C) Too Low                 | Indicates that device’s sensor temperature is to low.                                                                                                                    |
| event_sensor_temperature_too_high                    | Sensor Temperature(32°C) Too High                | Indicates that device’s sensor temperature is to high.                                                                                                                   |


## Full Example

_Provide a full usage example based on textual configuration files (*.things, *.items, *.sitemap)._

## Any custom content here!

_Feel free to add additional sections for whatever you think should also be mentioned about your binding!_
