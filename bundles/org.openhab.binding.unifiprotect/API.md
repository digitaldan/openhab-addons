## UniFi Protect Integration API - REST & WebSocket Guide

This document summarizes the UniFi Protect Integration API exposed under the `openapi.json` schema and how it is consumed by this binding. It covers REST endpoints, request/response DTOs, WebSocket subscriptions, authentication, and client usage.

## Base URL and Authentication

- **Base URL**: `<scheme>://<host>:<port>/integration`
- **Auth**: The client sends an `Authorization: Bearer <token>` header (binding config: `token`). If your deployment uses cookie/session auth, adapt headers accordingly.

## Conventions

- JSON fields map to DTOs under `org.openhab.binding.unifiprotect.internal.dto` with public fields and enums for fixed sets.
- Polymorphic payloads use discriminators and Gson factories:
  - Devices (`modelKey`) via `DeviceTypeAdapterFactory`
  - Events (`type`) via `EventTypeAdapterFactory`

---

## REST API

Status 200 returns JSON unless noted; non-2xx returns `genericError`.

### Meta

- GET `/v1/meta/info` → `ProtectVersionInfo`

### Viewers

- GET `/v1/viewers/{id}` → `Viewer`
- PATCH `/v1/viewers/{id}` (partial JSON) → `Viewer`
- GET `/v1/viewers` → `List<Viewer>`

### Liveviews

- GET `/v1/liveviews/{id}` → `Liveview`
- PATCH `/v1/liveviews/{id}` (partial `Liveview`) → `Liveview`
- GET `/v1/liveviews` → `List<Liveview>`
- POST `/v1/liveviews` (`Liveview`) → `Liveview`

### Cameras

- GET `/v1/cameras` → `List<Camera>`
- GET `/v1/cameras/{id}` → `Camera`
- PATCH `/v1/cameras/{id}` (partial JSON) → `Camera`
- POST `/v1/cameras/{id}/rtsps-stream` (`qualities`: `List<ChannelQuality>`) → `CreatedRtspsStreams`
- DELETE `/v1/cameras/{id}/rtsps-stream?qualities=high,medium` → 204
- GET `/v1/cameras/{id}/rtsps-stream` → `ExistingRtspsStreams`
- GET `/v1/cameras/{id}/snapshot[?highQuality=true|false]` → `image/jpeg` bytes
- POST `/v1/cameras/{id}/disable-mic-permanently` → `Camera`
- POST `/v1/cameras/{id}/talkback-session` → `TalkbackSession`

#### Camera PTZ

- POST `/v1/cameras/{id}/ptz/patrol/start/{slot}` → 204
- POST `/v1/cameras/{id}/ptz/patrol/stop` → 204
- POST `/v1/cameras/{id}/ptz/goto/{slot}` → 204

### Lights

- GET `/v1/lights/{id}` → `Light`
- PATCH `/v1/lights/{id}` (partial JSON) → `Light`
- GET `/v1/lights` → `List<Light>`

### Sensors

- GET `/v1/sensors/{id}` → `Sensor`
- PATCH `/v1/sensors/{id}` (partial JSON) → `Sensor`
- GET `/v1/sensors` → `List<Sensor>`

### Chimes

- GET `/v1/chimes/{id}` → `Chime`
- PATCH `/v1/chimes/{id}` (partial JSON) → `Chime`
- GET `/v1/chimes` → `List<Chime>`

### NVR

- GET `/v1/nvrs` → `Nvr`

### Files (multipart)

- POST `/v1/files/{fileType}` (multipart/form-data, part name: `file`) → `FileSchema`
- GET `/v1/files/{fileType}` → `List<FileSchema>`

`fileType`: enum `AssetFileType` (e.g., `animations`).

### Alarm Manager

- POST `/v1/alarm-manager/webhook/{id}` → 204 (triggers configured alarms for `id`)

### Errors

- On non-2xx, server returns `genericError` or `idRequiredError` for missing IDs. The client throws `IOException` with HTTP status and error message.

---

## WebSocket API

Two subscriptions push real-time updates. Messages are JSON envelopes with a `type` discriminator.

### Devices Subscription

- Path: GET `/v1/subscribe/devices` (WebSocket)
- Messages:
  - `DeviceAdd` { `type`: `add`, `item`: `device` }
  - `DeviceUpdate` { `type`: `update`, `item`: `devicePartialWithReference` }
  - `DeviceRemove` { `type`: `remove`, `item`: `deviceReference` }

`device.modelKey`: `nvr`, `camera`, `chime`, `light`, `viewer`, `speaker`, `bridge`, `doorlock`, `sensor`, `aiprocessor`, `aiport`, `linkstation`.

### Events Subscription

- Path: GET `/v1/subscribe/events` (WebSocket)
- Messages:
  - `EventAdd` { `type`: `add`, `item`: `event` }
  - `EventUpdate` { `type`: `update`, `item`: `event` }

`event.type`: `ring`, `sensorExtremeValues`, `sensorWaterLeak`, `sensorTamper`, `sensorBatteryLow`, `sensorAlarm`, `sensorOpened`, `sensorClosed`, `sensorMotion`, `lightMotion`, `motion`, `smartAudioDetect`, `smartDetectZone`, `smartDetectLine`, `smartDetectLoiterZone`.

### WebSocket Lifecycle

- The client exposes `onOpen`, `onClosed(code, reason)`, and `onError(Throwable)` callbacks for both device and event subscriptions.
- Binding usage:
  - Devices WS open → start periodic poll for fields not pushed via WS.
  - Devices/Event WS close → schedule reconnect (simple delay; upgrade to backoff as needed).

---

## Java Client Usage

The binding includes `UniFiProtectApiClient` (Jetty + Gson) to call the API and manage WebSockets.

```java
Gson gson = new GsonBuilder()
  .registerTypeAdapterFactory(new DeviceTypeAdapterFactory())
  .registerTypeAdapterFactory(new EventTypeAdapterFactory())
  .create();

Map<String, String> headers = Map.of("Authorization", "Bearer " + token);
try (UniFiProtectApiClient api = new UniFiProtectApiClient(URI.create(baseUrl), gson, headers)) {
  // REST
  Camera cam = api.getCamera(cameraId);
  CreatedRtspsStreams streams = api.createRtspsStream(cameraId, List.of(ChannelQuality.HIGH));
  byte[] jpeg = api.getSnapshot(cameraId, ForceHighQuality.TRUE);

  // WebSocket: devices
  api.subscribeDevices(add -> { /* handle add */ },
    upd -> { /* handle update */ },
    rem -> { /* handle remove */ },
    () -> { /* onOpen: start polling */ },
    (code, reason) -> { /* onClosed: schedule reconnect */ },
    err -> { /* onError */ });
}
```

---

## Notable DTOs

- `Camera`, `Light`, `Viewer`, `Chime`, `Sensor`, `Nvr` and simple devices: public fields mirroring schema.
- `Liveview` + `LiveviewSlot` (cycle settings); `RingSettings` (chimes); sensor config DTOs (e.g., `TemperatureSettings`).
- `CreatedRtspsStreams` / `ExistingRtspsStreams` (note serialized field `package`).
- Events under `org.openhab.binding.unifiprotect.internal.dto.events` with subtype classes per `type`.

---

## Polling vs. Streaming

- WebSockets deliver near-real-time updates for device adds/updates/removes and events.
- The binding performs an initial REST sync after a WS is opened, then periodically polls for values not conveyed via streaming (config-dependent interval).

---

## Error Handling & Timeouts

- Default HTTP timeout: 30 seconds per request. Adjust in client if needed.
- On non-2xx, the client attempts to parse `GenericError` and includes code/message in the thrown `IOException`.

---

## PTZ and Talkback

- PTZ endpoints return 204 on success; no response body.
- Talkback returns `TalkbackSession` with URL/codec parameters to publish audio to the device.

---

## File Uploads

- `POST /v1/files/{fileType}` expects `multipart/form-data` with part name `file`, content types such as `image/*` or `audio/*` as supported by the schema. Returns `FileSchema` with server-side name and path.
