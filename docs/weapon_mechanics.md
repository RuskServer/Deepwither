# Weapon Mechanics Summary

This document outlines the special combat mechanics associated with different weapon types in Deepwither, as implemented in `WeaponMechanicManager.java`.

---

## 1. 剣 (Sword)
- **Trigger:** Normal attack, provided the attack cooldown is sufficiently high (e.g., after a full swing).
- **Effect:** Performs a **sweep attack**.
    - Damages nearby entities within a `3.0` block horizontal and `1.5` block vertical radius around the main target for `50%` of the main attack's damage.
    - Hit enemies are knocked back slightly.
- **Visual/Sound:** A sweep particle effect and a sword sweep sound.

## 2. 大剣 (Greatsword)
- **Trigger:** Every **3rd consecutive hit** with a greatsword on the same target.
- **Effect:** Triggers a **Stun** and increased damage.
    - Stuns the victim for `0.3` seconds, applying severe slowness and jump boost to prevent movement.
    - The `3rd` hit's damage is increased by `50%`.
- **Visual/Sound:** An explosion particle effect, combined with wooden door and anvil landing sounds.
- **UI Feedback:** Displays "STUN!!" combat action, a special log message "大剣の強撃で相手を怯ませた！" (Greatsword's powerful blow staggered the opponent!), and a "大剣コンボ" (Greatsword Combo) progress bar.

## 3. 槍 (Spear)
- **Trigger:** Normal attack.
- **Effect:** Performs a **piercing attack (cleave)**.
    - Damages the main target and subsequent enemies in a line behind it.
    - The first extra target takes `50%` of the main damage.
    - Each subsequent target's damage is `decayed by 20%` (e.g., 50% -> 40% -> 32%).
    - Damage values below `0.5` are ignored.
- **Visual/Sound:** Critical hit particles along the thrust path, a strong attack sound, and a sweep particle effect on hit.
- **UI Feedback:** Displays "貫通!" (Pierce!) with the damage dealt for each additional target.

## 4. 斧 (Axe)
- **Trigger:** Every **3rd consecutive hit** with an axe on the same target.
- **Effect:** Triggers **Armor Crush**.
    - The `3rd` hit gains `50% defense bypass` (meaning the target's defense is reduced by 50% for this hit).
- **Visual/Sound:** Critical hit particles on impact, combined with an iron door attack sound.
- **UI Feedback:** Displays "ARMOR CRUSH!!" combat action, a special log message "50% 防御貫通攻撃！" (50% Defense Penetration Attack!), and an "斧コンボ" (Axe Combo) progress bar.

## 5. ハルバード (Halberd)
- **Trigger:** Normal attack.
- **Effect:** Performs a **wide-range sweep attack**.
    - Damages the main target and other nearby entities within a `3.5` block horizontal and `2.0` block vertical radius.
    - Cleave damage is `50%` of the main attack's damage.
    - If `3` or more targets (including the main target) are hit in total, the cleave damage is increased to `75%` (`50% * 1.5`).
- **Visual/Sound:** A sweep particle effect and a sword sweep sound. If the power bonus activates, additional critical particles and an iron golem attack sound are played.
- **UI Feedback:** Displays "HALBERD CLEAVE!!" combat action if the power bonus is active.

## 6. ハンマー (Hammer)
- **Trigger:** A **charged attack** is successfully executed (requires consuming a "hammer" charge).
- **Effect:** Triggers a powerful **area-of-effect crash**.
    - Deals `210%` (`70% * 3`) of the normal attack damage to enemies within a `4.0` block radius around the main target.
    - Knocks back all hit enemies within the area.
- **Visual/Sound:** Explosion emitter, sonic boom particles, combined with generic explosion and anvil landing sounds.
- **UI Feedback:** Displays "CRASH!!" combat action and a special log message "ハンマーの溜め攻撃を叩き込んだ！" (Hammer's charged attack landed!).
