<p align="center">
  <img src="src/main/resources/assets/villagerplus/icon.png" width="96" alt="Villager Plus icon">
</p>

<h1 align="center">Villager Plus</h1>

<p align="center">
  Smarter villager pathing and village guards for Minecraft Java Edition.
</p>

<p align="center">
  <a href="https://github.com/YOUR-USERNAME/villagerplus/actions/workflows/build.yml"><img src="https://github.com/YOUR-USERNAME/villagerplus/actions/workflows/build.yml/badge.svg" alt="Build"></a>
  <img src="https://img.shields.io/badge/Minecraft-26.3-62B47A" alt="Minecraft 26.3">
  <img src="https://img.shields.io/badge/loader-Fabric-DBD0B4" alt="Fabric">
  <img src="https://img.shields.io/badge/Java-25-E76F00" alt="Java 25">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue" alt="MIT license"></a>
</p>

---

## Features

### 🧱 Villagers that don't walk into walls

When a villager is heading somewhere but hasn't really moved for about 1.5 seconds, it reacts instead of pushing against the block forever:

1. **Opens a fence gate** directly in front of it.
2. **Hops up a one-block ledge**, slab or path block it's stuck against.
3. **Backs off and takes a detour** around the obstacle.

Vanilla villagers treat closed fence gates as walls and give up on anything behind them. With Villager Plus, a villager walks to the gate, opens it, carries on, and **closes it behind itself**, so zombies and your animals stay where they belong.

After 4 failed attempts in a row, a villager rests for 10 seconds rather than jittering in place.

### 🛡️ Village guards

| Action | Result |
| --- | --- |
| Right-click an adult villager with an **iron sword** | It becomes a guard, posted where it's standing |
| Right-click a guard with another **iron sword** | Moves its post to where the guard is now |
| **Sneak + right-click** a guard with an empty hand | Dismisses it; it drops the sword and goes back to normal life |

Guards:

- hold the sword, get **+20 max health**, and slowly regenerate
- patrol within **10 blocks** of their post
- attack hostile mobs within **16 blocks** and give up the chase past **24 blocks**
- fight back against any monster that hits them
- don't sleep, panic or trade while on duty
- leave creepers alone by default (so the village doesn't get blown up)
- drop their sword if they die

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft **26.3**.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 26.3.
3. Download `villagerplus-x.y.z.jar` from the [Releases](https://github.com/YOUR-USERNAME/villagerplus/releases) page.
4. Put both jars in your `.minecraft/mods` folder and launch the Fabric profile.

**Multiplayer:** all logic runs on the server, so only the server needs the mod. Installing it on a client is harmless.

## Configuration

All tuning values live in [`Config.java`](src/main/java/com/villagerplus/Config.java). Change them and rebuild.

| Setting | Default | What it does |
| --- | --- | --- |
| `STUCK_TICKS` | `30` | Ticks without progress before a villager reacts (20 ticks = 1 s) |
| `MAX_STRIKES` | `4` | Failed unstick attempts before it takes a break |
| `OPEN_FENCE_GATES` | `true` | Whether villagers use fence gates |
| `GUARD_BONUS_HEALTH` | `20.0` | Extra max health for guards |
| `GUARD_ATTACK_DAMAGE` | `6.0` | Damage per hit |
| `GUARD_DETECT_RADIUS` | `16.0` | How far guards spot hostile mobs |
| `GUARD_CHASE_RADIUS` | `24.0` | How far from their post guards will chase |
| `GUARD_PATROL_RADIUS` | `10` | Patrol area around the post |
| `GUARDS_FIGHT_CREEPERS` | `false` | Whether guards attack creepers |

## Building from source

Requires **JDK 25** (for example [Temurin 25](https://adoptium.net/)).

```bash
git clone https://github.com/YOUR-USERNAME/villagerplus.git
cd villagerplus
./gradlew build        # Windows: gradlew.bat build
```

The jar is written to `build/libs/`. To launch a test client with the mod loaded, run `./gradlew runClient`.

Every push to `main` is also built by GitHub Actions, and the jar can be downloaded from the run's **Artifacts** section.

### Troubleshooting

- **Could not resolve a plugin or dependency version.** Open <https://fabricmc.net/develop/>, select 26.3, and copy the Loom, Loader and Fabric API versions into `gradle.properties`.
- **Mixin can't find `customServerAiStep`.** Mojang renamed the villager AI method. Open the `Villager` class in your IDE, find the method that ticks the brain, and update the name in [`VillagerMixin.java`](src/main/java/com/villagerplus/mixin/VillagerMixin.java).
- **Other "cannot find symbol" errors** usually mean a Minecraft method was renamed; autocomplete on that class shows the new name.

## Project layout

```
src/main/java/com/villagerplus/
├── VillagerPlus.java        mod entrypoint
├── Config.java              tuning values
├── WallSense.java           stuck detection, ledge hopping, fence gates, detours
├── Guards.java              guard state (saved with the villager)
├── GuardAI.java             patrol and combat behaviour
├── GuardInteraction.java    right-click handling
└── mixin/VillagerMixin.java hooks into the villager's AI tick
```

## Releasing

Push a tag that starts with `v` and GitHub Actions builds the jar and publishes a release with it attached:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Remember to bump `mod_version` in `gradle.properties` and add an entry to [`CHANGELOG.md`](CHANGELOG.md) first.

## Contributing

Bug reports and pull requests are welcome. Please use the [issue templates](https://github.com/YOUR-USERNAME/villagerplus/issues/new/choose) so it's easy to reproduce problems.

## License

[MIT](LICENSE)

<sub>Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.</sub>
