
Installation information
=======

This mod was made to allow for cobblemon battles with 2v1 for players

Starting a 2v1 battle
=======

No commands or OP needed — everything goes through Cobblemon's normal player interaction wheel:

1. Two players form a team: look at each other, open the wheel, pick **Battle → Multi (Invite)** and accept.
2. Either a team member looks at a solo player, or the solo player looks at a team member, and opens **Battle**.
   A **Multi** page is offered for that pairing; pressing **Challenge** sends a 2v1 challenge.
3. Any member of the challenged side accepts from the same wheel. The team fields one Pokémon each,
   the solo player fields two and acts twice per turn.

The solo player needs at least 2 healthy Pokémon and each team member at least 1.

`/battle2v1 <teamPlayer1> <teamPlayer2> <soloPlayer>` (OP level 2) is still available for admins/testing.

Cobbledex integration (optional)
=======

If [Cobbledex](https://github.com/Rafacasari/cobbledex) (1.3.0+) is installed, unlocking a Pokémon in
Cobbledex also unlocks its entry in Cobblemon's own Pokédex:

- A Cobbledex **caught** register marks the form as *owned*, a **seen** register marks it as *seen*
  (shiny state included). Cobblemon knowledge is never downgraded.
- On login the player's whole Cobbledex collection is replayed into their Cobblemon Pokédex, so
  progress made before installing this mod is picked up too.
- In Cobbledex **co-op mode** the shared collection is used: discoveries unlock the entry for every
  online player, and offline players catch up on their next login.

Cobbledex is not required; without it this feature is simply inactive. In the dev environment the
NeoForge build is pulled from Modrinth (`cobbledex_version` in `gradle.properties`).
