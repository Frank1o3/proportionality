# Proportionality

Proportionality is a Fabric mod that lets players change their physical size while proportionally adjusting their attributes to match. Players adjust their size through an in-game scale screen, while the server remains authoritative over the allowed range and applied attributes.

Proportionality is designed for both single-player and multiplayer environments, with scale values managed server-side and synchronized with connected clients.

> **Temporary dependency notice:** The latest version of Proportionality requires [FranklyLib](https://github.com/Frank1o3/franklylib), a custom library used for the scale-screen UI and other shared functionality. Until FranklyLib is verified on Modrinth, download the `.jar` from its [latest GitHub release](https://github.com/Frank1o3/franklylib/releases/latest) and place it in the same `mods` folder as Proportionality.

## Features

* Adjust player size through an in-game scale screen.
* Open the scale screen with a rebindable keybind.
* See the server's allowed minimum and maximum scale right in the UI.
* Server owners can limit the maximum player scale.
* Change player size using server commands when appropriate.
* Player attributes adjust proportionally based on their size.
* Configurable scaling behavior for individual attributes.
* Your scale is saved and automatically restored when you reconnect.
* Server operators can reload scaling configuration without restarting the server.

The mod currently scales the following attributes:

* Maximum health
* Movement speed
* Jump strength
* Attack damage
* Step height
* Entity interaction range
* Fall damage multiplier
* Player scale

## Player Interface

The scale adjustment screen is the primary way for players to change their size. Open it in-game, choose a value with the slider, and confirm the selection. The server validates every change and sends the committed value back to the client.

By default, the screen can be opened by pressing:

**K**

The keybind can be changed through Minecraft's standard **Controls** menu.

The slider's minimum and maximum values are provided by the connected server. This means server owners can set a maximum such as `2` or `16`, and players cannot select a larger size through the UI.

Regular players do not need command access to use the scale screen.

## Command

### `/scale reload`

Reloads the Proportionality server configuration without restarting the server — useful after changing the scaling configuration (for example, the exponents that control how attributes scale with player size). Requires moderator-level command permissions.

## Configuration

Server owners can control how attributes respond to changes in player scale, including the scaling parameters and exponents for individual attributes (health, movement speed, jump strength, attack damage, reach, and more).

`maxScaleLimit` controls the maximum size players may select. The effective maximum is the lower of `maxScaleLimit` and Minecraft's own `minecraft:scale` attribute maximum — for example, setting `maxScaleLimit` to `2` limits players to double size, while `16` permits up to 16x size where supported.

`scaleDataRetentionDays` controls automatic cleanup of inactive players' saved scale data. It defaults to `30`; set it to `0` to keep data indefinitely.

Run `/scale reload` after editing the config to apply changes without restarting the server.

## Small-Scale Players and AttributeFix

For players who want to use very small scales, it's recommended to also install **AttributeFix** alongside Proportionality.

Minecraft's vanilla attribute system has minimum and maximum limits on certain attributes. Depending on the attribute and scale, these limits can stop attributes from shrinking as far as Proportionality's scaling would otherwise allow. AttributeFix removes those vanilla limits, letting Proportionality scale more accurately at very small sizes.

AttributeFix is **optional** and not required to use Proportionality.

## How It Works

Proportionality is server-authoritative: the server decides the allowed scale range, applies the resulting attribute changes, and saves each player's scale so it persists across restarts. The client side is mainly the scale screen and keybind — it shows the range the server sends rather than enforcing its own limits, so server owners stay in control of scaling behavior across the whole server.

## Installation

1. Install **Fabric Loader** for the Minecraft version supported by the mod.
2. Install **Fabric API**.
3. Download the Proportionality `.jar` file.
4. Download the `.jar` from the [latest FranklyLib GitHub release](https://github.com/Frank1o3/franklylib/releases/latest) — required until FranklyLib's Modrinth listing is approved.
5. Place both `.jar` files in your Minecraft `mods` folder.

For multiplayer servers, Proportionality should be installed on the server. Clients should also have the mod installed to access the client-side scale adjustment screen and keybind.

For the best results when using very small player scales, install **AttributeFix** as an optional companion mod.

## Requirements

* Minecraft `26.2`
* Fabric Loader `0.19.3` or newer
* Fabric API
* Java 25 or newer

## License

Proportionality is licensed under the [MIT License](https://github.com/Frank1o3/Proportionality/blob/main/LICENSE).

Copyright (c) 2026 Frank1o3
