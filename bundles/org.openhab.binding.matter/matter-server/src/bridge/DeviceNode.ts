// Include this first to auto-register Crypto, Network and Time Node.js implementations
import "@matter/node";

import { FabricIndex, VendorId } from "@matter/types";
import { logEndpoint, DeviceCommissioner, FabricManager, SessionManager } from "@matter/protocol";
import { Endpoint, EndpointServer, ServerNode } from "@matter/node";
import { AggregatorEndpoint } from "@matter/node/endpoints";
import { Environment, Logger, StorageService } from "@matter/general";
import { GenericDeviceType } from "./devices/GenericDeviceType";
import { OnOffLightDeviceType } from "./devices/OnOffLightDeviceType";
import { OnOffPlugInDeviceType } from "./devices/OnOffPlugInDeviceType";
import { DimmableDeviceType } from "./devices/DimmableDeviceType";
import { ThermostatDeviceType } from "./devices/ThermostatDeviceType";
import { WindowCoveringDeviceType } from "./devices/WindowCoveringDeviceType";
import { BridgeController } from "./BridgeController";
import { DoorLockDeviceType } from "./devices/DoorLockDeviceType";
import { TemperatureSensorType } from "./devices/TemperatureSensorType";
import { HumiditySensorType } from "./devices/HumiditySensorType";
import { OccupancySensorDeviceType } from "./devices/OccupancySensorDeviceType";
import { ContactSensorDeviceType } from "./devices/ContactSensorDeviceType";
import { FanDeviceType } from "./devices/FanDeviceType";
import { ColorDeviceType } from "./devices/ColorDeviceType";
import { BridgeEvent, BridgeEventType, EventType } from "../MessageTypes";


const logger = Logger.get("DeviceNode");
const DEFAULT_NODE_ID = "bridge-0"

export class DeviceNode {
    private server!: ServerNode;
    #environment: Environment = Environment.default;

    private aggregator!: Endpoint<AggregatorEndpoint>;
    private devices: Map<string, GenericDeviceType> = new Map();
    private inCommission = false;
    private storageService: StorageService;
    
    constructor(private bridgeController: BridgeController, private storagePath: string, private deviceName: string, private vendorName: string, private passcode: number, private discriminator: number, private vendorId: number, private productName: string, private productId: number, private port: number) {
        logger.info(`Device Node Storage location: ${this.storagePath} (Directory)`);
        this.#environment.vars.set('storage.path', this.storagePath)
        this.storageService = this.#environment.get(StorageService);
    }

    async ohBridgeStorage() {
        return (await this.storageService.open(DEFAULT_NODE_ID)).createContext("openhab");
    }

    //remove this after some amount of time that users have upgraded.
    async isLegacyBridge() {
        const rootContext = (await this.storageService.open(DEFAULT_NODE_ID)).createContext("root");
        const ohStorage = await this.ohBridgeStorage();
        //is there an existing common matter.js root element but not our openhab storage? 
        return (await rootContext.has("__number__")) && !(await ohStorage.has("lastStart"));
    }

    async init() {
        const ohStorage = await this.ohBridgeStorage();
        //use the default node id as unique id unless one has been reset by the user. Used the basicCluster of the root endpoint to uniquely identify the bridge
        const uniqueId = await ohStorage.get("basicInformation.uniqueId", (await this.isLegacyBridge()) ? DEFAULT_NODE_ID : this.#randomUUID());

        logger.info(`Unique ID: ${uniqueId}`);
        /**
         * Create a Matter ServerNode, which contains the Root Endpoint and all relevant data and configuration
         */
        try {
            this.server = await ServerNode.create({
                // Required: Give the Node a unique ID which is used to store the state of this node
                id: DEFAULT_NODE_ID,

                // Provide Network relevant configuration like the port
                // Optional when operating only one device on a host, Default port is 5540
                network: {
                    port: this.port,
                },

                // Provide Commissioning relevant settings
                // Optional for development/testing purposes
                commissioning: {
                    passcode: this.passcode,
                    discriminator: this.discriminator,

                },

                // Provide Node announcement settings
                // Optional: If Ommitted some development defaults are used
                productDescription: {
                    name: this.deviceName,
                    deviceType: AggregatorEndpoint.deviceType,
                },

                // Provide defaults for the BasicInformation cluster on the Root endpoint
                // Optional: If Omitted some development defaults are used
                basicInformation: {
                    vendorName: this.vendorName,
                    vendorId: VendorId(this.vendorId),
                    nodeLabel: this.productName,
                    productName: this.productName,
                    productLabel: this.productName,
                    productId: this.productId,
                    serialNumber:`${this.productName}-${this.productId}`,
                    uniqueId: uniqueId,
                },
            });

            logger.info(`ServerNode created with ID: ${this.server.id}`);
            this.aggregator = new Endpoint(AggregatorEndpoint, { id: "aggregator" });
            await this.server.add(this.aggregator);
            await ohStorage.set("basicInformation.uniqueId", uniqueId);
        } catch (e) {
            logger.error(`Error starting server: ${e}`);
            throw e;
        }
    }

    async close() {
        await this.server?.close();
        this.devices.clear();

        // await Promise.race([
        //     this.server?.close(),
        //     new Promise((_, reject) => 
        //         setTimeout(() => reject(new Error('Server close operation timed out after 10 seconds')), 10000)
        //     )
        // ]);
        this.devices.clear();
    }

    getCommissioningState() {
        return {
            pairingCodes: {
                manualPairingCode: this.server.state.commissioning.pairingCodes.manualPairingCode,
                qrPairingCode: this.server.state.commissioning.pairingCodes.qrPairingCode
            },
            commissioningWindowOpen : !this.server.state.commissioning.commissioned || this.inCommission
        }
    }

    getFabrics() {
        const fabricManager = this.server.env.get(FabricManager);
        return fabricManager.fabrics;
    }

    async removeFabric(fabricIndex: number) {
        const fabricManager = this.server.env.get(FabricManager);
        await fabricManager.removeFabric(FabricIndex(fabricIndex));
    }

    async addEndpoint(deviceType: string, id: string, nodeLabel: string, productName: string, productLabel: string, serialNumber: string, attributeMap: { [key: string]: any }) {
        //const deviceType = this.deviceTypes[endpointType];
        let device: GenericDeviceType | null = null;

        if (this.devices.has(id)) {
            throw new Error(`Device ${id} already exists! Call 'resetEndpoints' first and try again.`);
        }

        if (!this.aggregator) {
            throw new Error(`Aggregator not initialized, aborting.`);
        }

        switch (deviceType) {
            case "OnOffLight":
                device = new OnOffLightDeviceType(this.bridgeController, attributeMap, id, nodeLabel, productName, productLabel, serialNumber);
                break;
            case "OnOffPlugInUnit":
                device = new OnOffPlugInDeviceType(this.bridgeController, attributeMap, id, nodeLabel, productName, productLabel, serialNumber);
                break;
            case "DimmableLight":
                device = new DimmableDeviceType(this.bridgeController, attributeMap, id, nodeLabel, productName, productLabel, serialNumber);
                break;
            case "Thermostat":
                device = new ThermostatDeviceType(this.bridgeController, attributeMap, id, nodeLabel, productName, productLabel, serialNumber);
                break;
            case "WindowCovering":
                device = new WindowCoveringDeviceType(this.bridgeController, attributeMap, id, nodeLabel, productName, productLabel, serialNumber);
                break;
            case "DoorLock":
                device = new DoorLockDeviceType(this.bridgeController, attributeMap, id, nodeLabel, productName, productLabel, serialNumber);
                break;
            case "TemperatureSensor":
                device = new TemperatureSensorType(this.bridgeController, attributeMap, id, nodeLabel, productName, productLabel, serialNumber);
                break;
            case "HumiditySensor":
                device = new HumiditySensorType(this.bridgeController, attributeMap, id, nodeLabel, productName, productLabel, serialNumber);
                break;
            case "OccupancySensor":
                device = new OccupancySensorDeviceType(this.bridgeController, attributeMap, id, nodeLabel, productName, productLabel, serialNumber);
                break;
            case "ContactSensor":
                device = new ContactSensorDeviceType(this.bridgeController, attributeMap, id, nodeLabel, productName, productLabel, serialNumber);
                break;
            case "Fan":
                device = new FanDeviceType(this.bridgeController, attributeMap, id, nodeLabel, productName, productLabel, serialNumber);
                break;
            case "ColorLight":
                device = new ColorDeviceType(this.bridgeController, attributeMap, id, nodeLabel, productName, productLabel, serialNumber);
                break;
            default:
                throw new Error(`Unsupported device type ${deviceType}`);
        }
        if (device != null) {
            this.devices.set(id, device);
            await this.aggregator.add(device.endpoint);
        }

    }

    async initializeBridge(resetStorage: boolean = false) {
        //remove these hacks once we have a proper way to close the server
        logger.info(`Closing bridge`);
        await this.close();
        logger.info(`Initializing bridge`);
        await this.init();
        if (resetStorage) {
            logger.info(`!!! Erasing ServerNode Storage !!!`);
            await this.server.erase();
            //generate a new uniqueId for the bridge (bridgeBasicInformation.uniqueId)
            const ohStorage = await this.ohBridgeStorage();
            await ohStorage.set("basicInformation.uniqueId", this.#randomUUID());
            await this.init();
        }
        logger.info(`Bridge initialized`);
    }

    async startBridge() {
        if (this.devices.size == 0) {
            throw new Error("No devices added, not starting");
        }
        logEndpoint(EndpointServer.forEndpoint(this.server));
        logger.info(`Starting bridge`);
        await this.server.start();
        logger.info(`Bridge started`);
        const ohStorage = await this.ohBridgeStorage();
        await ohStorage.set("lastStart", Date.now());
    }

    async setEndpointState(endpointId: string, clusterName: string, stateName: string, stateValue: any) {
        const device = this.devices.get(endpointId);
        if (device) {
            device.updateState(clusterName, stateName, stateValue);
        }
    }

    async openCommissioningWindow() {
        const dc = this.server.env.get(DeviceCommissioner);
        logger.debug('opening basic commissioning window')
        await dc.allowBasicCommissioning(() => {
            this.inCommission = false;
            logger.debug('commissioning window closed')
            const be: BridgeEvent = {
                type: BridgeEventType.EventTriggered,
                data: {
                    eventName: "commissioningWindowClosed",
                    data: {}
                }
            }
            this.bridgeController.ws.sendEvent(EventType.BridgeEvent, be);
        });
        this.inCommission = true;
        logger.debug('basic commissioning window open')
    }

    async closeCommissioningWindow() {
        const dc = this.server.env.get(DeviceCommissioner);
        logger.debug('closing basic commissioning window')
        await dc.endCommissioning();
    }

    #randomUUID() {
        return crypto.randomUUID().replace(/-/g, '');
    }

    // #sendCommissioningCodes() {
    //     if (!this.server.state.commissioning.commissioned || this.inCommission) {
    //         const be: BridgeEvent = {
    //             type: BridgeEventType.EventTriggered,
    //             data: {
    //                 eventName: "commissioningWindowOpen",
    //                 data: {
    //                     manualPairingCode: this.server.state.commissioning.pairingCodes.manualPairingCode,
    //                     qrPairingCode: this.server.state.commissioning.pairingCodes.qrPairingCode
    //                 }
    //             }
    //         }
    //         this.bridgeController.ws.sendEvent(EventType.BridgeEvent, be);
    //     } else {
    //         const be: BridgeEvent = {
    //             type: BridgeEventType.EventTriggered,
    //             data: {
    //                 eventName: "commissioningWindowClosed",
    //                 data: {}
    //             }
    //         }
    //         this.bridgeController.ws.sendEvent(EventType.BridgeEvent, be);
    //     }
    //}
}
