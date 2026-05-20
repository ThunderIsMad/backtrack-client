# Solo Backtrack Mod-Client

## What this Space is for
This project is a specialized Minecraft mod-com.yourname.backtrack.client built on Minecraft Forge 1.12.2. It is designed to provide utility and combat advantages through various modules, most notably the **Backtrack** module.

### Core Features
- **Backtrack**: Simulates a controlled lag environment by delaying specific entity packets (entity movement, teleportation, and head rotation). This allows players to "hit through time" by attacking the delayed position of an entity, effectively increasing combat range and reliability.
- **AutoSprint**: Automatically keeps the player sprinting, with customizable conditions such as requiring forward movement or allowing sprinting while sneaking.
- **FullBright**: Increases game gamma to provide maximum visibility in dark areas, with multiple presets (Soft, Normal, Max, Custom).
- **AutoRespawn**: Automatically reschedules a respawn after death with a configurable delay, allowing for faster return to gameplay.
- **Reach**: Extends the player's attack distance by a configurable amount, allowing for hits from up to 6.0 blocks away.
- **KeepSprint**: Automatically maintains the player's sprinting state while moving forward, ensuring maximum mobility during combat.
- **HUD & GUI**: Features a customizable HUD to display active modules and a ClickGUI for easy setting management.

## How to use it

### Installation
1. Ensure you have **Minecraft Forge 1.12.2** installed.
2. Clone or download this repository.
3. For development:
   - Run `gradlew genIntellijRuns` (for IntelliJ IDEA) or `gradlew genEclipseRuns` (for Eclipse).
   - Import the project into your IDE as a Gradle project.
4. To build the mod:
   - Run `gradlew build`. The resulting JAR file will be in `build/libs/`.

### In-Game Usage
- **Open ClickGUI**: Press `RIGHT SHIFT` (RSHIFT) to open the settings menu. Here you can toggle modules and adjust their specific settings (e.g., Backtrack delay, Gamma level, AutoRespawn timer).
- **Default Keybinds**:
  - `G`: Toggle AutoSprint
  - `H`: Toggle FullBright
  - `J`: Toggle AutoRespawn
  - `B`: Toggle Backtrack
- **HUD Management**: Use the HUD Editor (accessible via ClickGUI) to reposition module information on your screen.

### Backtrack Configuration
Backtrack is the primary module of this com.yourname.backtrack.client. Key settings include:
- **Delay**: The maximum time (in ms) to delay packets.
- **Range**: The maximum distance to entities for Backtrack to activate.
- **Priority**: Choose between "Closest" or "Lowest HP" for target prioritization.

---
*Note: This mod is intended for educational and utility purposes. Use responsibly.*
