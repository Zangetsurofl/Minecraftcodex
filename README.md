# LawAndOrder

Paper 1.20+ plugin (Java 21) that adds Police, Criminal, and Civilian gameplay.

## Build and install

```bash
mvn clean package
```

Copy `target/LawAndOrder.jar` into the Paper server's `plugins/` directory and restart. Configure the jail location, sentence duration, wanted limit, hide chance, fee, and all player-facing messages in `plugins/LawAndOrder/config.yml`.

## Roles and commands

Roles are controlled with permissions: `laworder.police`, `laworder.criminal`, and `laworder.admin`. Players without either faction permission are civilians. The resolved faction, wanted level, jail expiry, and gang membership are persisted in `players.yml`.

| Command | Permission | Purpose |
| --- | --- | --- |
| `/duty on\|off` | police | Set on-duty status. |
| `/wanted <player>` | police | Add one wanted star. |
| `/arrest <player>` | police | Jail wanted targets; at maximum wanted level no duty status is required. |
| `/fine <player> <amount>` | police | Withdraw a fine through Vault when its economy provider is available. |
| `/crime` | criminal | Add one wanted star to yourself. |
| `/hide` | criminal | Pay the configured fee and attempt to clear wanted status. |
| `/gang create <name>` | criminal | Create a gang. |
| `/gang invite <player>` | gang leader | Invite a player. |
| `/gang join` | criminal | Accept the latest invitation. |
| `/gang leave` | criminal | Leave a gang. |
