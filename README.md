# Proportionality

Proportionality is a primarily server-side Fabric mod that allows players to change their physical size while proportionally adjusting their attributes to match.

The mod also includes client-side functionality, including a scale adjustment screen and a keybind, allowing players to change their size without having to use server commands.

Proportionality is designed for both single-player and multiplayer environments, with scale values managed server-side and synchronized with connected clients.

## Features

* Change player size through an in-game GUI.
* Change player size using server commands.
* Proportionally adjust player attributes based on their size.
* Configurable scaling behavior for individual attributes.
* Server-side persistence of player scale values.
* Automatically restore saved scale values when players reconnect.
* Synchronize player scale values between the server and clients.
* Server operators can reload scaling configuration without restarting the server.
* Client-side keybind for quickly opening the scale adjustment screen.
* Multiplayer-compatible with server-authoritative scale management.

The mod currently scales the following attributes:

* Maximum health
* Movement speed
* Jump strength
* Attack damage
* Step height
* Entity interaction range
* Fall damage multiplier
* Player scale

## Scale Adjustment Screen

Players can change their size through the Proportionality scale adjustment screen.

By default, the screen can be opened by pressing:

**K**

The keybind can be changed through Minecraft's standard **Controls** menu.

The GUI is intended to provide a convenient way for regular players to adjust their size without needing access to server commands.

## Commands

Proportionality provides commands for changing player scale and managing the server's scaling configuration.

### `/scale set <value>`

Sets the executing player's scale to the specified value.

For example:

```text
/scale set 2
```

sets the player's target scale to `2`, making them approximately twice their normal size.

To return to the default player size, set the scale to `1`:

```text
/scale set 1
```

The maximum allowed scale is determined by the server configuration and the limits of Minecraft's scale attribute.

The command requires permission to send commands.

### `/scale reload`

Reloads the Proportionality server configuration without restarting the server.

This is useful when a server administrator changes the scaling configuration, such as the exponents that control how individual attributes scale with player size.

After reloading the configuration, the updated scaling rules are reapplied to players currently online.

This command requires moderator-level command permissions and is intended for server administrators.

> **Note:** Regular players can use the in-game scale adjustment screen instead of the `/scale set` command. The default keybind for opening the scale screen is **K**.


## Configuration

Proportionality uses a server-side configuration to control how attributes respond to changes in player scale.

The configuration allows the scaling behavior of individual attributes to be adjusted using configurable scaling parameters and exponents.

This makes it possible to fine-tune how attributes such as health, movement speed, jump strength, attack damage, and reach behave as a player's size changes.

The configuration can be reloaded using:

```text
/scale reload
```

This allows server administrators to adjust scaling behavior without restarting the server.

## Small-Scale Players and AttributeFix

For players who want to use very small scales, it is recommended to use **AttributeFix** alongside Proportionality.

Minecraft's vanilla attribute system imposes minimum and maximum limits on certain attributes. Depending on the attribute and the scale being used, these limits can prevent attributes from being reduced as far as the proportional scaling calculations would otherwise allow.

AttributeFix can remove or expand these vanilla attribute limits, allowing Proportionality to provide more accurate attribute scaling at very small player sizes.

AttributeFix is **optional** and is not required to use Proportionality.

> Proportionality does not require AttributeFix as a dependency. It is recommended as an optional companion mod for players who want to use extremely small player scales and require attributes to scale beyond Minecraft's vanilla attribute limits.

## Server-Side Design

Proportionality is primarily a server-side mod.

The server is responsible for:

* Calculating player scale values.
* Applying scaled attributes.
* Persisting player scale data.
* Synchronizing scale values with clients.
* Managing server-side scaling configuration.

The client-side component provides functionality that improves the player experience, primarily the scale adjustment screen and its associated keybind.

This design allows server administrators to control scaling behavior while still providing players with an easy-to-use interface for changing their own scale.

## Scale Persistence

Player scale values are stored server-side and persist across server restarts.

The scale data is stored in the world's `data` directory:

```text
<world>/data/proportionality_scales.json
```

When a player joins the server, their saved scale is loaded and synchronized with their client.

## Installation

1. Install **Fabric Loader** for the Minecraft version supported by the mod.
2. Install **Fabric API**.
3. Download the Proportionality `.jar` file.
4. Place the mod in your Minecraft `mods` folder.

For multiplayer servers, Proportionality should be installed on the server. Clients should also have the mod installed to access the client-side scale adjustment screen and keybind.

For the best results when using very small player scales, install **AttributeFix** as an optional companion mod.

## Requirements

* Minecraft `26.2`
* Fabric Loader `0.19.3` or newer
* Fabric API
* Java 25 or newer

## License

Proportionality is licensed under the [MIT License](LICENSE).

Copyright (c) 2026 Frank1o3
