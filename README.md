# Casino

Paper 1.20+ / Java 21 casino plugin with GUI slot machines, blackjack, poker table, roulette, and Truth-or-Lie party game.

## Build

```bash
mvn clean package
```

Install `target/Casino.jar` in a Paper server's `plugins` folder. The SQLite database is created at `plugins/Casino/casino.db`; Vault is used automatically when present, otherwise Casino stores balances internally. `Citizens` is optional: create NPCs named `Casino Slots`, `Casino Blackjack`, `Casino Poker`, `Casino Roulette`, or `Casino TruthLie`; named armor stands work as a no-dependency alternative.

## Usage

Players use `/casino <slots|blackjack|poker|roulette|truthlie>` as an admin/testing entry point, but normal play is through named casino NPCs and inventory GUI buttons. `/casino bet <amount>` sets the currently selected stake (within configured limits). All player-facing text is in `messages.yml`; payouts, bets, questions, and CustomModelData values are in `config.yml`.

### Resource-pack models

`CustomModelData` references are intentionally code-only: chips use `1000`, slot symbols use `1101`–`1105`, and cards begin at `2001`. Supply matching PNG/item models in your server resource pack.
