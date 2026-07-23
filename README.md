# Sukuna Prototype

A Minecraft NeoForge mod that adds dynamic slash visual effects with customizable appearance, damage, and behavior.

## Features

- **Dynamic Slash VFX System**: Spawns animated slash effects with customizable colors, thickness, and outline
- **Multiplayer Support**: Client-server synchronization for consistent visual effects across all players
- **Highly Configurable**: Control slash appearance and damage through GameRules, commands, and config files
- **Performance Optimized**: Efficient rendering with frustum culling and dirty-flag sorting
- **Keybinding Support**: Spawn random slash effects with a configurable hotkey

## Requirements

- **Minecraft**: 1.21.1
- **NeoForge**: 1.21.1 (latest recommended)

## Installation

1. Download the latest release of the mod
2. Place the `.jar` file in your `mods` folder
3. Launch Minecraft with the NeoForge 1.21.1 profile
4. Configure keybindings and settings as desired

## GameRules

All slash behavior can be controlled via Minecraft's `/gamerule` command or the mod's convenience commands:

### `sukunaprototype:slashMaxRate`
- **Description**: Maximum slashes per second while holding the spawn key
- **Range**: 1-60
- **Default**: 7
- **Unit**: slashes/sec

### `sukunaprototype:slashThickness`
- **Description**: Real-time slash line thickness (affects already-spawned slashes)
- **Range**: 0-3000
- **Default**: 160 (0.16 blocks)
- **Unit**: milliblocks (×1000)

### `sukunaprototype:slashOutline`
- **Description**: Real-time outline rim thickness
- **Range**: 0-1000
- **Default**: 120 (0.12 blocks)
- **Unit**: milliblocks (×1000)

### `sukunaprototype:slashDamage`
- **Description**: Damage dealt by each slash
- **Range**: 0-100000
- **Default**: 6000 (6 hearts = 12 HP)
- **Unit**: millihearts (×1000)

### `sukunaprototype:slashIgnoreInvulnerable`
- **Description**: Whether slashes ignore entity invulnerability frames
- **Values**: true/false
- **Default**: false
- **Note**: When true, slashes reset invulnerableTime to 0 before dealing damage

## Commands

The mod provides convenience commands for quick configuration without typing full gamerule names:

### View Current Value
Run the command without arguments to see the current setting:
```
/sukunaprototype slashMaxRate
/sukunaprototype slashThickness
/sukunaprototype slashOutline
/sukunaprototype slashDamage
/sukunaprototype slashIgnoreInvulnerable
```

### Set New Value
Provide a value to update the setting:
```
/sukunaprototype slashMaxRate <1-60>
/sukunaprototype slashThickness <0-3000>
/sukunaprototype slashOutline <0-1000>
/sukunaprototype slashDamage <0-100000>
/sukunaprototype slashIgnoreInvulnerable <true|false>
```

**Examples:**
```
/sukunaprototype slashDamage 12000          # Set damage to 12 hearts
/sukunaprototype slashThickness 80          # Set thickness to 0.08 blocks (thinner slash)
/sukunaprototype slashIgnoreInvulnerable true   # Bypass invulnerability frames
```

## Keybindings

Configure keybindings in **Options → Controls → Key Binds → Sukuna Prototype**:

- **Slash: Random All**: Spawns random slash effects at the player's location

## Configuration

The mod's configuration file is located at `config/sukunaprototype-common.toml` (automatically created on first launch).

### Available Options

#### `SLASH_DAMAGE` (double)
- **Description**: Default slash damage in hearts
- **Default**: 6.0
- **Range**: 0.0 to 50.0
- **Note**: Can be overridden by the `slashDamage` GameRule

#### `ENABLE_DEBUG_LOGGING` (boolean)
- **Description**: Enable detailed debug logging for VFX system
- **Default**: false
- **Use Case**: Enable when troubleshooting visual effect issues or reporting bugs

### Example Configuration
```toml
[general]
    SLASH_DAMAGE = 6.0
    ENABLE_DEBUG_LOGGING = false
```

## Usage Examples

### Dealing More Damage
```
/sukunaprototype slashDamage 24000
```
Sets slash damage to 24 hearts (48 HP) - enough to one-shot most mobs.

### Creating Thinner, Precise Slashes
```
/sukunaprototype slashThickness 80
/sukunaprototype slashOutline 60
```
Makes slashes thinner and more precise-looking (0.08 block line, 0.06 block outline).

### Rapid Slash Spawning
```
/sukunaprototype slashMaxRate 30
```
Allows up to 30 slashes per second when holding the keybind - useful for creating dense effect patterns.

### Ignoring Invulnerability Frames
```
/sukunaprototype slashIgnoreInvulnerable true
```
Allows slashes to damage enemies multiple times in quick succession, bypassing Minecraft's standard i-frame system.

## Multiplayer Compatibility

The mod is fully multiplayer-compatible:
- **Server-side**: Controls slash damage, spawn logic, and GameRule enforcement
- **Client-side**: Handles visual effects rendering only
- **Synchronization**: Slash spawn events are automatically synced from server to all clients

To use on a server:
1. Install the mod on the server (in the `mods` folder)
2. Clients must also have the mod installed to see visual effects
3. Server operators can configure GameRules and commands for all players

## Performance

The mod is designed for minimal performance impact:
- **Efficient Rendering**: Only renders effects visible in the camera frustum
- **Optimized Updates**: Sorting only occurs when effects are added/removed, not every tick
- **Configurable Limits**: Maximum of 1000 active effects (oldest removed when limit reached)
- **Debug Logging**: Disabled by default to avoid log spam

## Troubleshooting

### Slashes not appearing
- Ensure the mod is installed on both client and server
- Check that you're not at the MAX_EFFECTS limit (1000 active slashes)
- Verify keybinding is properly configured

### Performance issues
- Reduce `slashMaxRate` to spawn fewer effects
- Disable `ENABLE_DEBUG_LOGGING` if accidentally enabled
- Check that you're not spawning thousands of slashes simultaneously

### Slashes dealing no damage
- Verify `slashDamage` GameRule is not set to 0
- Check that the server has the mod installed (visual effects work client-only, but damage requires server)

## License

This mod is provided as-is for use in Minecraft with NeoForge 1.21.1.

## Credits

Visual effects system inspired by Jujutsu Kaisen's Sukuna character abilities.
