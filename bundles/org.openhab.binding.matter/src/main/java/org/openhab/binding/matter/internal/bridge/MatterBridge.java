/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.matter.internal.bridge;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.matter.internal.bridge.devices.DeviceRegistry;
import org.openhab.binding.matter.internal.bridge.devices.GenericDevice;
import org.openhab.binding.matter.internal.client.MatterClientListener;
import org.openhab.binding.matter.internal.client.MatterWebsocketService;
import org.openhab.binding.matter.internal.client.dto.ws.AttributeChangedMessage;
import org.openhab.binding.matter.internal.client.dto.ws.BridgeCommissionState;
import org.openhab.binding.matter.internal.client.dto.ws.BridgeEventAttributeChanged;
import org.openhab.binding.matter.internal.client.dto.ws.BridgeEventMessage;
import org.openhab.binding.matter.internal.client.dto.ws.BridgeEventTriggered;
import org.openhab.binding.matter.internal.client.dto.ws.EventTriggeredMessage;
import org.openhab.binding.matter.internal.client.dto.ws.NodeDataMessage;
import org.openhab.binding.matter.internal.client.dto.ws.NodeStateMessage;
import org.openhab.core.OpenHAB;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.common.registry.RegistryChangeListener;
import org.openhab.core.config.core.ConfigurableService;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.items.GenericItem;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.ItemRegistryChangeListener;
import org.openhab.core.items.Metadata;
import org.openhab.core.items.MetadataKey;
import org.openhab.core.items.MetadataRegistry;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MatterBridge}
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
@Component(immediate = true, service = MatterBridge.class, configurationPid = MatterBridge.CONFIG_PID, property = Constants.SERVICE_PID
        + "=" + MatterBridge.CONFIG_PID)
@ConfigurableService(category = "io", label = "Matter Bridge", description_uri = MatterBridge.CONFIG_URI)
public class MatterBridge implements MatterClientListener {
    private final Logger logger = LoggerFactory.getLogger(MatterBridge.class);
    private static final String CONFIG_PID = "org.openhab.matter";
    private static final String CONFIG_URI = "io:matter";

    private static final String VENDOR_NAME = "openHAB";
    private static final String DEVICE_NAME = "Bridge Device";
    private static final String PRODUCT_ID = "0001";
    private static final String VENDOR_ID = "65521";
    // this will be used in the name of the storage directory
    private static final String UNIQUE_ID = "bridge-0";

    private final Map<String, GenericDevice> devices = new HashMap<>();

    private MatterBridgeClient client;
    private ItemRegistry itemRegistry;
    private MetadataRegistry metadataRegistry;
    private MatterWebsocketService websocketService;
    private ConfigurationAdmin configAdmin;
    private MatterBridgeSettings settings;

    private final ItemRegistryChangeListener itemRegistryChangeListener;
    private final RegistryChangeListener<Metadata> metadataRegistryChangeListener;
    private final ScheduledExecutorService scheduler = ThreadPoolManager
            .getScheduledPool(ThreadPoolManager.THREAD_POOL_NAME_COMMON);
    private boolean resetBridge = false;
    private boolean bridgeInitialized = false;
    private boolean commissioningWindowOpen = false;
    private @Nullable ScheduledFuture<?> modifyFuture;
    private @Nullable ScheduledFuture<?> reconnectFuture;

    @Activate
    public MatterBridge(final @Reference ItemRegistry itemRegistry, final @Reference MetadataRegistry metadataRegistry,
            final @Reference MatterWebsocketService websocketService, final @Reference ConfigurationAdmin configAdmin) {
        this.itemRegistry = itemRegistry;
        this.metadataRegistry = metadataRegistry;
        this.websocketService = websocketService;
        this.configAdmin = configAdmin;
        this.client = new MatterBridgeClient();
        this.settings = new MatterBridgeSettings();

        itemRegistryChangeListener = new ItemRegistryChangeListener() {
            private boolean handleMetadataChange(Item item) {
                if (metadataRegistry.get(new MetadataKey("matter", item.getUID())) != null) {
                    updateModifyFuture();
                    return true;
                }
                return false;
            }

            @Override
            public void added(Item element) {
                handleMetadataChange(element);
            }

            @Override
            public void updated(Item oldElement, Item element) {
                if (!handleMetadataChange(oldElement)) {
                    handleMetadataChange(element);
                }
            }

            @Override
            public void allItemsChanged(Collection<String> oldItemNames) {
                updateModifyFuture();
            }

            @Override
            public void removed(Item element) {
                handleMetadataChange(element);
            }
        };
        this.itemRegistry.addRegistryChangeListener(itemRegistryChangeListener);

        metadataRegistryChangeListener = new RegistryChangeListener<>() {
            private void handleMetadataChange(Metadata element) {
                if ("matter".equals(element.getUID().getNamespace())) {
                    updateModifyFuture();
                }
            }

            public void added(Metadata element) {
                handleMetadataChange(element);
            }

            public void removed(Metadata element) {
                handleMetadataChange(element);
            }

            public void updated(Metadata oldElement, Metadata element) {
                handleMetadataChange(element);
            }
        };
        this.metadataRegistry.addRegistryChangeListener(metadataRegistryChangeListener);
    }

    @Activate
    public synchronized void activate(Map<String, Object> properties) {
        logger.debug("Activating Matter Bridge {}", properties);
        // if this returns true, we will wait for @Modified to be called after the config is persisted
        if (!parseInitialConfig(properties)) {
            this.settings = (new Configuration(properties)).as(MatterBridgeSettings.class);
            if (this.settings.enableBridge) {
                connectClient();
            }
        }
    }

    @Deactivate
    public synchronized void deactivate() {
        logger.debug("Deactivating Matter Bridge");
        itemRegistry.removeRegistryChangeListener(itemRegistryChangeListener);
        metadataRegistry.removeRegistryChangeListener(metadataRegistryChangeListener);
        ScheduledFuture<?> reconnectFuture = this.reconnectFuture;
        if (reconnectFuture != null) {
            reconnectFuture.cancel(true);
        }
        stopClient();
    }

    @Modified
    protected synchronized void modified(Map<String, Object> properties) {
        logger.debug("Modified Matter Bridge {}", properties);
        MatterBridgeSettings settings = (new Configuration(properties)).as(MatterBridgeSettings.class);
        boolean restart = false;
        if (this.settings.enableBridge != settings.enableBridge) {
            restart = true;
        }
        if (!this.settings.bridgeName.equals(settings.bridgeName)) {
            restart = true;
        }
        if (this.settings.discriminator != settings.discriminator) {
            restart = true;
        }
        if (this.settings.passcode != settings.passcode) {
            restart = true;
        }
        if (this.settings.port != settings.port) {
            restart = true;
        }
        if (settings.resetBridge) {
            this.resetBridge = true;
            restart = true;

        }
        settings.resetBridge = false;

        this.settings = settings;

        if (!settings.enableBridge) {
            stopClient();
        } else if (!client.isConnected() || restart) {
            stopClient();
            scheduler.schedule(this::connectClient, 5, TimeUnit.SECONDS);
        } else {
            manageCommissioningWindow();
        }
    }

    @Override
    public void onDisconnect(String reason) {
        stopClient();
        ScheduledFuture<?> reconnectFuture = this.reconnectFuture;
        if (reconnectFuture == null || reconnectFuture.isDone() && this.settings.enableBridge) {
            this.reconnectFuture = scheduler.schedule(this::connectClient, 5, TimeUnit.SECONDS);
        }
    }

    @Override
    public void onConnect() {
    }

    @Override
    public void onReady() {
        registerItems();
    }

    @Override
    public void onEvent(NodeStateMessage message) {
    }

    @Override
    public void onEvent(AttributeChangedMessage message) {
    }

    @Override
    public void onEvent(EventTriggeredMessage message) {
    }

    @Override
    public void onEvent(NodeDataMessage message) {
    }

    @Override
    public void onEvent(BridgeEventMessage message) {
        if (message instanceof BridgeEventAttributeChanged attributeChanged) {
            GenericDevice d = devices.get(attributeChanged.data.endpointId);
            if (d != null) {
                d.handleMatterEvent(attributeChanged.data.clusterName, attributeChanged.data.attributeName,
                        attributeChanged.data.data);
            }
        } else if (message instanceof BridgeEventTriggered bridgeEventTriggered) {
            switch (bridgeEventTriggered.data.eventName) {
                case "commissioningWindowOpen":
                    // commissioningWindowOpen = true;
                    // Object qrPairingCode = bridgeEventTriggered.data.data.get("qrPairingCode");
                    // Object manualPairingCode = bridgeEventTriggered.data.data.get("manualPairingCode");
                    // if (qrPairingCode != null && manualPairingCode != null) {
                    // updatePairingCodes(qrPairingCode.toString(), manualPairingCode.toString());
                    // }
                    break;
                case "commissioningWindowClosed":
                    commissioningWindowOpen = false;
                    updateConfig(Map.of("openCommissioningWindow", false));
                    break;
                default:
            }
        }
    }

    private synchronized void connectClient() {
        if (client.isConnected()) {
            logger.debug("Already Connected, returning");
            return;
        }

        try {
            commissioningWindowOpen = false;
            String folderName = OpenHAB.getUserDataFolder() + File.separator + "matter";
            File folder = new File(folderName);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            Map<String, String> paramsMap = new HashMap<>();

            paramsMap.put("service", "bridge");
            paramsMap.put("storagePath", folder.getAbsolutePath());

            // default values the bridge exposes to clients
            paramsMap.put("deviceName", DEVICE_NAME);
            paramsMap.put("vendorName", VENDOR_NAME);
            paramsMap.put("vendorId", VENDOR_ID);
            paramsMap.put("productId", PRODUCT_ID);
            paramsMap.put("uniqueId", UNIQUE_ID);

            paramsMap.put("productName", settings.bridgeName);
            paramsMap.put("passcode", String.valueOf(settings.passcode));
            paramsMap.put("discriminator", String.valueOf(settings.discriminator));
            paramsMap.put("port", String.valueOf(settings.port));

            if (resetBridge) {
                resetBridge = false;
                paramsMap.put("resetBridge", "true");
                updateConfig(Map.of("resetBridge", false));
            }

            client.addListener(this);
            client.connectWhenReady(this.websocketService, paramsMap);
        } catch (Exception e) {
            logger.error("Error connecting to websocket", e);
        }
    }

    private void stopClient() {
        logger.debug("Stopping Matter Bridge Client");
        ScheduledFuture<?> modifyFuture = this.modifyFuture;
        if (modifyFuture != null) {
            modifyFuture.cancel(true);
        }
        MatterBridgeClient client = this.client;
        if (client != null) {
            client.removeListener(this);
            client.disconnect();
        }
        devices.values().forEach(GenericDevice::dispose);
        devices.clear();
        bridgeInitialized = false;
    }

    private boolean parseInitialConfig(Map<String, Object> properties) {
        logger.debug("Parse Config Matter Bridge");

        Dictionary<String, Object> props = null;
        org.osgi.service.cm.Configuration config = null;

        try {
            config = configAdmin.getConfiguration(MatterBridge.CONFIG_PID);
            props = config.getProperties();
        } catch (IOException e) {
            logger.warn("cannot retrieve config admin {}", e.getMessage());
        }

        if (props == null) { // if null, the configuration is new
            props = new Hashtable<>();
        }

        // A discriminator uniquely identifies a Matter device on the IPV6 network, 12-bit integer (0-4095)
        int discriminator = -1;
        @Nullable
        Object discriminatorProp = props.get("discriminator");
        if (discriminatorProp instanceof String discriminatorString) {
            try {
                discriminator = Integer.parseInt(discriminatorString);
            } catch (NumberFormatException e) {
                logger.debug("Could not parse discriminator {}", discriminatorString);
            }
        } else if (discriminatorProp instanceof Integer discriminatorInteger) {
            discriminator = discriminatorInteger;
        }

        // randomly create one if not set
        if (discriminator < 0) {
            Random random = new Random();
            discriminator = random.nextInt(4096);
        }

        props.put("discriminator", discriminator);

        // this should never be persisted true, temporary settings
        props.put("resetBridge", false);

        boolean changed = false;
        if (config != null) {
            try {
                changed = config.updateIfDifferent(props);
            } catch (IOException e) {
                logger.warn("cannot update configuration {}", e.getMessage());
            }
        }
        return changed;
    }

    private synchronized void registerItems() {
        if (bridgeInitialized) {
            try {
                logger.debug("Resetting Endpoints");
                bridgeInitialized = false;
                client.resetBridge().get();
            } catch (InterruptedException | ExecutionException e) {
                logger.debug("Could not reset endpoints", e);
                return;
            }
        }

        // clear out any existing devices
        devices.values().forEach(GenericDevice::dispose);
        devices.clear();

        for (Metadata metadata : metadataRegistry.getAll()) {
            final MetadataKey uid = metadata.getUID();
            if ("matter".equals(uid.getNamespace())) {
                try {
                    logger.debug("Metadata {}", metadata);
                    if (devices.containsKey(uid.getItemName())) {
                        logger.debug("Updating item {}", uid.getItemName());
                    }
                    final GenericItem item = (GenericItem) itemRegistry.getItem(uid.getItemName());
                    String deviceType = metadata.getValue();
                    GenericDevice device = DeviceRegistry.createDevice(deviceType, metadataRegistry, client, item);
                    if (device != null) {
                        try {
                            device.registerDevice().get();
                            logger.debug("Registered item {} with device type {}", item.getName(), device.deviceType());
                            devices.put(item.getName(), device);
                        } catch (InterruptedException | ExecutionException e) {
                            logger.debug("Could not register device with bridge", e);
                            device.dispose();
                        }
                    }
                } catch (ItemNotFoundException e) {
                    logger.debug("Could not find item {}", uid.getItemName(), e);
                }
            }
        }
        if (devices.isEmpty()) {
            logger.info("No devices found to register with bridge, not starting bridge");
            return;
        }
        try {
            client.startBridge().get();
            bridgeInitialized = true;
            updatePairingCodes();
        } catch (InterruptedException | ExecutionException e) {
            logger.debug("Could not start bridge", e);
        }
    }

    private void manageCommissioningWindow() {
        if (settings.openCommissioningWindow && !commissioningWindowOpen) {
            try {
                client.openCommissioningWindow().get();
                commissioningWindowOpen = true;
            } catch (CancellationException | InterruptedException | ExecutionException e) {
                logger.debug("Could not open commissioning window", e);
            }
        } else if (!settings.openCommissioningWindow && commissioningWindowOpen) {
            try {
                client.closeCommissioningWindow().get();
                commissioningWindowOpen = false;
            } catch (CancellationException | InterruptedException | ExecutionException e) {
                logger.debug("Could not close commissioning window", e);
            }
        }
    }

    private void updatePairingCodes() {
        try {
            BridgeCommissionState state = client.getCommissioningState().get();
            updateConfig(Map.of("manualPairingCode", state.pairingCodes.manualPairingCode, "qrCode",
                    state.pairingCodes.qrPairingCode, "openCommissioningWindow", state.commissioningWindowOpen));
            commissioningWindowOpen = state.commissioningWindowOpen;
        } catch (CancellationException | InterruptedException | ExecutionException e) {
            logger.debug("Could not query codes", e);
        }
    }

    private void updateConfig(Map<String, Object> entires) {
        try {
            org.osgi.service.cm.Configuration config = configAdmin.getConfiguration(MatterBridge.CONFIG_PID);
            Dictionary<String, Object> props = config.getProperties();
            if (props == null) {
                return;
            }
            entires.forEach((k, v) -> props.put(k, v));
            // if this updates, it will trigger a @Modified call
            config.updateIfDifferent(props);
        } catch (IOException e) {
            logger.debug("Could not get pairing codes", e);
        }
    }

    /**
     * This should be called by changes to items or metadata
     */
    private synchronized void updateModifyFuture() {
        // if the bridge is not initialized, we don't need to update the future
        if (!bridgeInitialized) {
            return;
        }
        ScheduledFuture<?> modifyFuture = this.modifyFuture;
        if (modifyFuture != null) {
            modifyFuture.cancel(false);
        }
        this.modifyFuture = scheduler.schedule(this::registerItems, 5, TimeUnit.SECONDS);
    }
}
