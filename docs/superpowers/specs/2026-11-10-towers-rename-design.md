# Towers Rename Design

## Intent
Rename the Minecraft plugin project from Questline to Towers so its visible name, command, Java identifiers, namespaces, persisted-data locations, and build artifact consistently use `towers`.

## Goals and Constraints
- Plugin name, root Java package/class, command and permission, resource-pack sound namespace, PDC namespace, persisted file naming, documentation, IDE module, and Maven artifact use Towers/towers.
- Java 21, Paper API 1.21.7, Maven, and existing behavior remain unchanged.
- This is a full identity change: existing Questline data files and PDC tags are not migrated and existing tagged entities will not be recognized under the new namespace.
- Build verification must run `mvn test` and `mvn package` (or `mvn clean package`); never commit generated `target/` output or JAR files.

## Approaches
- Rename only display name and command: rejected because package, namespaces, and persistence would remain inconsistent with the requested full rename.
- Rename all project identifiers in place: chosen because the user explicitly requested that everything be renamed. Update code/resources/docs and move source package paths together.

## Design
Set plugin name/command/permission and user-facing command strings to Towers/towers. Rename `VLADMIX90.questline` to `VLADMIX90.towers`, `Questline` main class to `Towers`, and Questline command classes to Towers equivalents. Replace internal PDC/sound namespace and data-folder naming. Update README, Maven artifact metadata, and IDEA module metadata. Leave historical notes and generated build output alone where they are not active project inputs.

## Non-goals
No compatibility aliases, data migration, gameplay changes, or unrelated cleanup.
