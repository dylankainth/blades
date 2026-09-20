# Kindred badge, contract

The badge is an ESP32-S3-BOX-3 worn on a lanyard with the **screen facing other
people**. It is a living nametag: it shows who you are, and when someone your
twin thinks you should meet walks up, it greets *them* by name in a colour
that matches their badge. The wearer hears the reason through their phone.

The badge is a dumb terminal. It polls one endpoint, draws the state it is
given, advertises one BLE token, and reports shakes. All logic lives in
`functions/src/box*.ts` and is derived from the same `matches/{matchId}` docs
the app and judge dashboard already use.

## Endpoints

Base: `https://us-central1-blades-a38f5.cloudfunctions.net`

### `GET /boxState?boxId=AB12CD`  (poll ~1 Hz)

```json
{
  "state": "match",
  "ownerName": "Keanu",
  "otherName": "Dylan",
  "colorHex": "#FF6B35",
  "bleToken": "9f3c1a7be2d04c55",
  "matchId": "uidA_uidB"
}
```

Any field except `state` may be `null`.

| `state` | Badge shows | Set when |
|---|---|---|
| `unpaired` | QR code of `kindred-box:<boxId>` + boxId text + Wi-Fi SSID | no owner yet |
| `paired_wave` | creature wakes and waves, "Hi, I'm `ownerName`'s Kindred" | 6 s after pairing |
| `idle` | nametag: `ownerName` large, calm creature (rings, 3 orbiting dots, blink) | default |
| `negotiating` | creature "chatting" animation | twins are talking, < 90 s |
| `match` | **whole screen floods `colorHex`**, creature waves, "Hi `otherName` 👋" | confirmed match, < 5 min, not yet met |
| `no_match` | small shrug, then back to idle | dismissed, < 8 s |
| `met` | celebration | both people confirmed, < 20 s |

Both badges in a pair receive the same `colorHex` (hashed from `matchId`), so
"find the other orange one" works from across a table.

### `POST /boxEvent`  `{"boxId":"AB12CD","event":"shake"}`

Send only while `state == "match"`. Returns `{"ok":true,"met":false|true}`.
If both people wear badges, both must shake within 10 s of each other, bump
the two badges together. If the other person has no badge, one shake is enough.

### `pairBox` (callable, signed-in app user)  `{boxId}`

`boxId` may be the raw id or the QR payload. Binds the badge to the caller's
twin, unbinds any badge they had before, mints the badge's BLE token and
registers it in `ble_sessions/{token}`. Fails with `not-found` if the badge has
never polled `boxState` (so a typo cannot create a ghost badge).

### `resetDemo` (callable, signed-in app user)

Deletes every match the caller is part of, plus the pair check-in docs, so the
same demo can be run again for the next judge.

## BLE

The badge advertises `bleToken` (8 bytes) exactly the way the phone does, see
`android/.../ble/BleConstants.kt` and `BleProximityService.kt`: legacy,
non-connectable, a single Service Data AD under the Kindred service UUID. No
name, no separate service-UUID list, the packet must fit in 31 bytes. Phones
resolve the token through `ble_sessions/{token}` like any other.

## boxId

Last 6 hex characters of the Wi-Fi MAC, uppercase.

## Wi-Fi

ESP32-S3 is 2.4 GHz only. The venue SSID `HackMIT.2026` is 5 GHz only and
cannot be used. `firmware/secrets.h` (gitignored) holds an ordered list of
networks to try; an empty password means an open network.

## Known shortcuts

- `boxState` and `boxEvent` are unauthenticated and `boxId` is guessable. They
  expose first names and accept a "shake". Fine for a demo; a real device would
  get a per-device secret at pairing.
- 1 Hz polling costs roughly five Firestore reads per badge per second.
