import { Endpoint } from "@matter/node";
import { OccupancySensorDevice } from "@matter/node/devices/occupancy-sensor";
import { BridgedDeviceBasicInformationServer } from "@matter/node/behaviors/bridged-device-basic-information";
import { GenericDeviceType } from './GenericDeviceType';

export class OccupancySensorDeviceType extends GenericDeviceType {
    
    override createEndpoint(clusterValues: Record<string, any>) {
        const endpoint = new Endpoint(OccupancySensorDevice.with(BridgedDeviceBasicInformationServer), {
            ...this.endPointDefaults(),
            ...clusterValues
        });
        return endpoint
    }

    override defaultClusterValues() {
        return {
            occupancySensing: {
                occupancy: {
                    occupied: false
                },
                occupancySensorType: 3
            }
        }
    }
}