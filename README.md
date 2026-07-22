# Proportionality

Proportionality is a Fabric mod that lets players change their physical size while proportionally adjusting their attributes to match. Players adjust their size through an in-game scale screen, while the server remains authoritative over the allowed range and applied attributes.

Proportionality is designed for both single-player and multiplayer environments, with scale values managed server-side and synchronized with connected clients.

## Features

* Adjust player size through an in-game scale screen.
* Open the scale screen with a rebindable keybind.
* Receive the server's allowed minimum and maximum scale in the UI.
* Limit the server's maximum player scale through configuration.
* Change player size using server commands when appropriate.
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

## Player Interface

The scale adjustment screen is the primary way for players to change their size. Open it in-game, choose a value with the slider, and confirm the selection. The server validates every change and sends the committed value back to the client.

By default, the screen can be opened by pressing:

**K**

The keybind can be changed through Minecraft's standard **Controls** menu.

The slider's minimum and maximum values are provided by the connected server. This means server owners can set a maximum such as `2` or `16`, and players cannot select a larger size through the UI.

Regular players do not need command access to use the scale screen.

## Commands

Commands are primarily useful for server administration and for players who prefer a command-based workflow.

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

## Configuration

Proportionality uses a server-side configuration to control how attributes respond to changes in player scale.

The configuration allows the scaling behavior of individual attributes to be adjusted using configurable scaling parameters and exponents.

This makes it possible to fine-tune how attributes such as health, movement speed, jump strength, attack damage, and reach behave as a player's size changes.

`maxScaleLimit` controls the maximum size players may select. The effective maximum is the lower of `maxScaleLimit` and Minecraft's actual `minecraft:scale` attribute maximum. For example, setting `maxScaleLimit` to `2` limits players to double size; setting it to `16` permits up to 16x size when the attribute supports it.

`scaleDataRetentionDays` controls automatic cleanup of inactive players' saved scale data. It defaults to `30`; set it to `0` to keep data indefinitely. Entries are removed after the player has not joined for the configured number of days, keeping `<world>/data/proportionality_scales.json` from growing indefinitely.

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
* Synchronizing the allowed scale range and committed scale values with clients.
* Managing server-side scaling configuration.

The client-side component provides the primary player interface: the scale adjustment screen and its associated keybind. It displays the range received from the server rather than applying its own hardcoded limits.

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
