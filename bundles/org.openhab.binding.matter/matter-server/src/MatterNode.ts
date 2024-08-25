// Include this first to auto-register Crypto, Network and Time Node.js implementations
import { CommissioningController, MatterServer } from "@project-chip/matter-node.js";

import { NodeId } from "@project-chip/matter-node.js/datatype";
import { PairedNode, CommissioningControllerNodeOptions } from "@project-chip/matter-node.js/device";
import { EndpointInterface } from "@project-chip/matter.js/endpoint";
import { Logger } from "@project-chip/matter-node.js/log";
import { StorageContext, StorageManager } from "@project-chip/matter-node.js/storage";
import { StorageBackendJsonFile } from "@project-chip/matter-node.js/storage";

const logger = Logger.get("Node");

/**
 * This class contains all the core Matter functionality uses by "Cluster", "Nodes", etc... 
 */
export class MatterNode {
    private storage?: StorageBackendJsonFile;
    private storageManager?: StorageManager;
    private storageContext?: StorageContext;

    commissioningController?: CommissioningController;
    private matterController?: MatterServer;

    constructor(
        private storageLocation: string,
        private readonly nodeNum: number,
        private readonly netInterface?: string
    ) { }

    get Store() {
        if (!this.storageContext) {
            throw new Error("Storage uninitialized");
        }
        return this.storageContext;
    }

    async close() {
        await this.matterController?.close();
    }

    async initialize() {
        this.storage = new StorageBackendJsonFile(this.storageLocation);
        logger.info(`Storage location: ${this.storageLocation} (Directory)`);
        this.storageManager = new StorageManager(this.storage);
        await this.storageManager.initialize();
        this.storageContext = this.storageManager.createContext("Node");


        if (this.storageManager === undefined) {
            throw new Error("StorageManager not initialized"); // Should never happen
        }
        if (this.matterController !== undefined) {
            return;
        }
        logger.info(`matter.js shell controller started for node ${this.nodeNum}`);

        /**
         * Create Matter Server and Controller Node
         *
         * To allow the device to be announced, found, paired and operated we need a MatterServer instance and add a
         * CommissioningController to it and add the just created device instance to it.
         * The Controller node defines the port where the server listens for the UDP packages of the Matter protocol
         * and initializes deice specific certificates and such.
         *
         * The below logic also adds command handlers for commands of clusters that normally are handled internally
         * like testEventTrigger (General Diagnostic Cluster) that can be implemented with the logic when these commands
         * are called.
         */

        this.matterController = new MatterServer(this.storageManager, { mdnsInterface: this.netInterface });
        this.commissioningController = new CommissioningController({
            autoConnect: false,
        });
        await this.matterController.addCommissioningController(this.commissioningController);

        /**
         * Start the Matter Server
         *
         * After everything was plugged together we can start the server. When not delayed announcement is set for the
         * CommissioningServer node then this command also starts the announcement of the device into the network.
         */

        await this.matterController.start();
    }

    async getNode(nodeId: number | string, connectOptions?: CommissioningControllerNodeOptions) {
        if (this.commissioningController === undefined) {
            throw new Error("CommissioningController not initialized");
        }
        logger.debug(`converting ${nodeId} to ${BigInt(nodeId)}`)
        const node = await this.commissioningController.connectNode(NodeId(BigInt(nodeId)), connectOptions)
        if (node === undefined) {
            throw new Error(`Node ${nodeId} not connected`);
        }
        return node;
    }

    /**
     * Finds the given endpoint, included nested endpoints
     * @param node 
     * @param endpointId 
     * @returns 
     */
    getEndpoint(node: PairedNode, endpointId: number) {
        const endpoints = node.getDevices();
        for (const e of endpoints) {
            const endpoint = this.findEndpoint(e, endpointId);
            if (e != undefined) {
                return e;
            }
        }
        return undefined;
    }

    /**
     * 
     * @param root Endpoints can have child endpoints. This function recursively searches for the endpoint with the given id.
     * @param endpointId 
     * @returns 
     */
    private findEndpoint(root: EndpointInterface, endpointId: number): EndpointInterface | undefined {
        if (root.number === endpointId) {
            return root;
        }
        for (const endpoint of root.getChildEndpoints()) {
            const found = this.findEndpoint(endpoint, endpointId);
            if (found !== undefined) {
                return found;
            }
        }
        return undefined;
    }
}
