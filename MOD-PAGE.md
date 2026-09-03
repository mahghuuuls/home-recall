<span style="color:#d6a100">**AI usage disclaimer:** This mod was developed with AI-agent assistance using [this agent workflow](https://github.com/mahghuuuls/minecraft-1.12.2-mod-agent-workflow). The project owner reviewed the work during development.</span>

This mod adds a recall system like the Hearthstone in World of Warcraft or the recall in MOBA games. By default it is an item you equip in a new inventory slot, but it can be configured to need no item at all. Press a key, wait a few seconds, and you are back at your bed. Acting or taking damage cancels the recall.

- Craft a **Recall Stone** (an ender pearl, two diamonds, and stone) and hover it: the tooltip tells you what it does and which key to press.
- Put it in the Recall Stone screen, opened from the button beside your inventory. While it sits there, press **R** (rebindable) to start a recall.
- Moving, attacking, using or placing anything, taking a hit, or pressing the key again breaks the cast. Stand still and you arrive at your bed, or at the world spawn if you have none.
- Works from any dimension.
- The stone stays with you through death by default.

**Configuration**

Cast length, cross-dimension travel, damage cancelling, the world-spawn fallback, death behavior, a starter stone for new players, and a switch that makes recall an innate ability with no stone at all. The recipe and the whole stone system can be turned off. Changes take effect on the next game start.

```
requireRecallStone=true
castTimeSeconds=6
allowCrossDimension=true
keepRecallStoneOnDeath=true
```

Install it on both the client and the server. It requires [Inventory Button Bar](https://www.curseforge.com/minecraft/mc-mods/inventory-button-bar), which supplies the button.

Source: https://github.com/mahghuuuls/home-recall
