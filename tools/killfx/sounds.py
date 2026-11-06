"""Sound design of every kill effect: real Minecraft sounds that fit what happens, at the right moment of the animation.
Replaces the sounds the effect builders wrote (particles stay). Format: (time, sound id, volume, pitch)."""

BITE_BIG = [("entity.ravager.attack", 1.0, 0.8), ("entity.evoker_fangs.attack", 0.9, 0.7)]
BITE = [("entity.evoker_fangs.attack", 1.0, 1.0), ("entity.player.attack.strong", 0.6, 1.2)]
GULP = ("entity.generic.drink", 0.8, 0.6)
CHEW = ("entity.generic.eat", 0.8, 0.8)
BURP = ("entity.player.burp", 1.0, 0.8)


def at(t, *snds):
    return [(t, s, v, p) for (s, v, p) in snds]


SOUNDS = {
    "pacman": [(0.2, "block.note_block.bit", 0.5, 1.6), (0.4, "block.note_block.bit", 0.5, 1.2), (0.6, "block.note_block.bit", 0.5, 1.6),
               (0.84, "block.note_block.bit", 0.6, 2.0)]
              + at(1.0, ("block.note_block.bit", 0.9, 0.7), ("entity.generic.eat", 1.0, 0.7))
              # the classic "game over" jingle going down
              + [(1.1 + i * 0.09, "block.note_block.bit", 0.7, p) for i, p in enumerate((1.9, 1.7, 1.5, 1.35, 1.2, 1.05, 0.9, 0.75))]
              + [(1.4, *CHEW), (1.68, *CHEW), (1.96, *CHEW), (2.24, *BURP)]
              + [(t, "block.note_block.bit", 0.45, 1.6 if i % 2 == 0 else 1.2) for i, t in enumerate((2.5, 2.7, 2.9, 3.1))],
    "shark": [(0.05, "block.bubble_column.upwards_ambient", 1.0, 0.8), (0.3, "block.bubble_column.upwards_ambient", 1.0, 0.7),
              (0.55, "ambient.underwater.enter", 0.8, 1.2), (0.72, "entity.player.splash.high_speed", 1.0, 0.7),
              (0.72, "entity.generic.splash", 0.8, 0.6)] + at(1.0, *BITE_BIG) + [(1.1, *GULP),
              (1.95, "entity.player.splash.high_speed", 1.0, 0.6), (1.95, "entity.dolphin.splash", 1.0, 0.7)],
    "mimic": [(0.27, "block.wood.step", 1.0, 0.8), (0.54, "block.wood.step", 1.0, 0.8), (0.81, "block.wood.step", 1.0, 0.8),
              (0.84, "block.chest.open", 1.0, 0.6), (0.9, "entity.ravager.roar", 0.5, 1.6), (1.1, "block.chest.close", 1.0, 0.6)]
             + at(1.1, *BITE) + [(1.44, *CHEW), (1.69, *CHEW), (1.94, *CHEW), (2.02, *BURP), (2.04, "entity.experience_orb.pickup", 1.0, 0.8),
                                  (2.1, "block.chain.place", 1.0, 1.4), (2.55, "block.wood.step", 1.0, 0.8), (2.85, "block.wood.step", 1.0, 0.8),
                                  (3.15, "block.wood.step", 1.0, 0.8)],
    "frog": [(0.0, "entity.frog.long_jump", 1.0, 0.7), (0.45, "entity.frog.step", 1.0, 0.6), (0.62, "entity.frog.ambient", 1.0, 0.5),
             (0.9, "entity.frog.tongue", 1.0, 0.8), (0.98, "entity.slime.squish_small", 1.0, 0.8), (1.25, "entity.frog.eat", 1.0, 0.6),
             (1.3, *GULP), (1.6, *BURP), (1.85, "entity.frog.long_jump", 1.0, 0.8)],
    "flytrap": [(0.05, "block.rooted_dirt.break", 1.0, 0.6), (0.25, "block.rooted_dirt.break", 1.0, 0.6), (0.45, "block.rooted_dirt.break", 1.0, 0.6),
                (0.55, "block.big_dripleaf.tilt_up", 1.0, 0.6), (0.55, "block.grass.break", 1.0, 0.5),
                (1.0, "block.big_dripleaf.tilt_down", 1.0, 0.7)] + at(1.0, *BITE) + [(1.35, *CHEW), (1.65, *CHEW), (1.95, *BURP),
                                                                                     (2.2, "block.rooted_dirt.break", 1.0, 0.6)],
    "ufo": [(0.1, "entity.phantom.flap", 1.0, 0.5), (0.4, "block.beacon.ambient", 1.0, 1.8), (0.65, "block.beacon.activate", 1.0, 1.6),
            (0.9, "block.beacon.power_select", 1.0, 1.4), (1.3, "block.beacon.ambient", 1.0, 1.5), (1.85, "entity.enderman.teleport", 1.0, 1.4),
            (2.0, "block.beacon.deactivate", 1.0, 1.6), (2.25, "entity.firework_rocket.launch", 1.0, 1.4)],
    "blackhole": [(0.05, "block.portal.ambient", 1.0, 1.4), (0.05, "block.respawn_anchor.charge", 1.0, 0.6), (0.6, "block.conduit.ambient.short", 1.0, 0.6),
                  (1.35, "entity.enderman.teleport", 1.0, 0.5), (1.6, "block.conduit.deactivate", 1.0, 0.7),
                  (1.85, "entity.firework_rocket.large_blast", 1.0, 0.8), (1.85, "block.amethyst_block.chime", 1.0, 1.2)],
    "rocket": [(0.05, "entity.tnt.primed", 1.0, 1.2), (0.4, "block.fire.ambient", 1.0, 1.0), (0.78, "entity.firework_rocket.launch", 1.0, 0.7),
               (0.8, "entity.firework_rocket.shoot", 1.0, 0.6), (1.6, "entity.firework_rocket.large_blast", 1.0, 0.9),
               (1.62, "entity.generic.explode", 0.5, 1.4), (1.85, "entity.firework_rocket.twinkle", 1.0, 1.0)],
    "whale": [(0.15, "entity.elder_guardian.ambient", 1.0, 0.4), (0.4, "block.bubble_column.upwards_ambient", 1.0, 0.6),
              (0.55, "entity.player.splash.high_speed", 1.0, 0.5), (0.55, "entity.generic.splash", 1.0, 0.5)] + at(1.05, *BITE_BIG)
             + [(1.15, *GULP), (1.35, "entity.dolphin.splash", 1.0, 0.5), (1.4, "entity.elder_guardian.ambient", 0.8, 0.5),
                (1.95, "entity.player.splash.high_speed", 1.0, 0.4), (1.95, "entity.generic.splash", 1.0, 0.5)],
    "dragon": [(0.1, "entity.ender_dragon.growl", 0.7, 1.4), (0.3, "entity.ender_dragon.flap", 0.8, 1.3), (0.64, "entity.ender_dragon.flap", 0.8, 1.3),
               (0.9, "entity.phantom.swoop", 1.0, 0.8)] + at(1.15, *BITE_BIG) + [(1.3, "entity.ender_dragon.shoot", 0.8, 1.2),
               (1.45, "entity.ender_dragon.flap", 0.8, 1.3), (1.8, "entity.ender_dragon.flap", 0.8, 1.3), (2.15, "entity.ender_dragon.flap", 0.8, 1.3)],
    "worm": [(0.05, "block.sand.break", 1.0, 0.5), (0.22, "block.sand.break", 1.0, 0.5), (0.4, "block.sand.break", 1.0, 0.5),
             (0.55, "entity.ravager.roar", 0.8, 1.3), (0.6, "block.gravel.break", 1.0, 0.5)] + at(1.0, *BITE_BIG)
            + [(1.1, *GULP), (1.9, *BURP), (2.1, "block.sand.break", 1.0, 0.5), (2.2, "block.gravel.break", 1.0, 0.5)],
    "snake": [(0.1, "entity.cat.hiss", 0.8, 0.5), (0.4, "entity.silverfish.ambient", 1.0, 0.6), (0.85, "entity.cat.hiss", 1.0, 0.4),
              (0.95, "entity.phantom.swoop", 0.6, 1.4)] + at(1.1, ("entity.evoker_fangs.attack", 1.0, 1.3), ("entity.spider.hurt", 0.6, 0.8))
             + [(1.2, *GULP), (1.45, *BURP), (1.6, "entity.silverfish.ambient", 1.0, 0.5)],
    "croc": [(0.1, "entity.generic.swim", 1.0, 0.6), (0.5, "entity.generic.swim", 1.0, 0.6), (0.9, "entity.player.splash", 1.0, 0.7)]
            + at(1.05, ("entity.evoker_fangs.attack", 1.0, 0.6), ("entity.ravager.attack", 0.8, 1.0))
            + [(1.25, "entity.generic.splash", 1.0, 0.6), (1.5, "entity.player.splash.high_speed", 1.0, 0.7), (2.2, "entity.generic.splash", 1.0, 0.5)],
    "trex": [(0.05, "entity.ravager.step", 1.0, 0.5), (0.3, "entity.ravager.step", 1.0, 0.5), (0.55, "entity.ravager.roar", 1.0, 0.6),
             (0.56, "entity.warden.roar", 0.5, 1.2)] + at(1.1, ("entity.ravager.attack", 1.0, 0.6), ("entity.warden.attack_impact", 0.8, 1.0))
            + [(1.3, *GULP), (1.8, "entity.ravager.roar", 0.7, 0.9)],
    "toilet": [(0.05, "block.stone.break", 1.0, 0.7), (0.4, "block.copper_bulb.turn_on", 1.0, 1.4), (0.6, "block.lever.click", 1.0, 0.8),
               (0.65, "block.bubble_column.whirlpool_inside", 1.0, 1.0), (0.65, "item.bucket.empty", 1.0, 0.6),
               (0.95, "block.bubble_column.whirlpool_ambient", 1.0, 1.2), (1.25, "ambient.underwater.enter", 1.0, 1.2),
               (1.42, "block.wooden_trapdoor.close", 1.0, 0.7), (2.05, "block.stone.break", 1.0, 0.7)],
    "hand": [(0.1, "entity.phantom.swoop", 1.0, 0.6), (0.7, "entity.player.attack.sweep", 0.8, 0.6), (1.12, "entity.player.attack.knockback", 1.0, 0.6),
             (1.14, "entity.item.pickup", 1.0, 0.5), (1.3, "entity.phantom.swoop", 1.0, 0.8), (1.9, "entity.firework_rocket.twinkle", 0.8, 1.2)],
    "kraken": [(0.2, "entity.squid.ambient", 1.0, 0.4), (0.4, "entity.guardian.ambient", 1.0, 0.5), (0.55, "entity.player.splash.high_speed", 1.0, 0.5),
               (1.0, "entity.slime.squish", 1.0, 0.6), (1.1, "entity.squid.squirt", 1.0, 0.5), (1.3, "ambient.underwater.enter", 1.0, 0.8),
               (1.3, "entity.generic.splash", 1.0, 0.5), (1.95, "entity.player.splash", 1.0, 0.6)],
    "pig": [(0.1, "entity.pig.ambient", 1.0, 0.8), (0.3, "entity.pig.step", 1.0, 0.8), (0.55, "entity.pig.step", 1.0, 0.8), (0.85, "entity.pig.ambient", 1.0, 1.2)]
           + at(1.05, ("entity.hoglin.attack", 1.0, 1.4), ("entity.generic.eat", 1.0, 0.8))
           + [(1.35, *CHEW), (1.65, *CHEW), (1.85, *BURP), (2.0, "entity.pig.ambient", 1.0, 1.4), (2.2, "entity.pig.step", 1.0, 0.8)],
    "ghost": [(0.1, "entity.allay.ambient_without_item", 1.0, 0.6), (0.5, "entity.vex.ambient", 0.8, 0.8), (0.85, "entity.vex.charge", 1.0, 0.8),
              (1.15, "entity.allay.item_taken", 1.0, 0.6), (1.16, "entity.phantom.bite", 0.8, 1.4), (1.5, *BURP),
              (1.7, "entity.allay.ambient_with_item", 1.0, 1.2)],
    "eagle": [(0.1, "entity.parrot.fly", 1.0, 0.5), (0.3, "entity.phantom.ambient", 0.6, 1.6), (0.8, "entity.phantom.swoop", 1.0, 1.0),
              (1.05, "entity.player.attack.knockback", 1.0, 0.7), (1.07, "entity.item.pickup", 1.0, 0.4), (1.3, "entity.ender_dragon.flap", 0.6, 1.8),
              (1.62, "entity.ender_dragon.flap", 0.6, 1.8), (1.7, "entity.phantom.ambient", 0.6, 1.7), (1.94, "entity.ender_dragon.flap", 0.6, 1.8)],
    "piano": [(0.25, "entity.phantom.swoop", 1.0, 0.4), (0.6, "entity.phantom.swoop", 1.0, 0.6), (1.0, "block.anvil.land", 1.0, 0.6),
              (1.0, "entity.generic.explode", 0.4, 1.6)]
             + [(1.02 + i * 0.02, "block.note_block.harp", 1.0, p) for i, p in enumerate((0.5, 0.63, 0.75))]
             + [(1.35 + i * 0.15, "block.note_block.harp", 1.0, p) for i, p in enumerate((1.0, 1.26, 1.5, 2.0))],
    "tornado": [(0.05, "entity.breeze.idle_air", 1.0, 0.6), (0.3, "item.elytra.flying", 0.8, 1.0), (0.8, "entity.breeze.wind_burst", 1.0, 0.7),
                (1.1, "entity.breeze.idle_air", 1.0, 0.8), (1.6, "entity.wind_charge.wind_burst", 1.0, 0.8), (1.62, "entity.breeze.shoot", 1.0, 0.8),
                (2.2, "entity.breeze.idle_air", 0.8, 1.0)],
    "gift": [(0.2, "entity.phantom.swoop", 1.0, 0.7), (1.0, "block.wool.place", 1.0, 0.5), (1.0, "entity.generic.big_fall", 1.0, 0.8),
             (1.36, "block.chest.locked", 0.8, 1.4), (1.48, "block.chest.locked", 0.8, 1.6), (1.65, "entity.firework_rocket.blast", 1.0, 1.2),
             (1.65, "entity.player.levelup", 0.8, 1.2), (1.8, "entity.firework_rocket.twinkle", 1.0, 1.2)],
    "cannon": [(0.05, "entity.minecart.riding", 0.6, 0.8), (0.75, "entity.tnt.primed", 1.0, 1.4), (1.02, "entity.generic.explode", 1.0, 1.4),
               (1.03, "entity.firework_rocket.blast_far", 1.0, 0.6), (1.15, "entity.firework_rocket.large_blast", 1.0, 1.0),
               (1.3, "entity.firework_rocket.twinkle", 1.0, 1.0)],
}


def apply(fx_id, anim):
    snd = SOUNDS.get(fx_id)
    if not snd:
        return
    anim.events = [e for e in anim.events if not e[1].startswith("s ")]
    for t, sid, v, p in snd:
        anim.events.append((round(t, 3), "s %s %s %s" % (sid, v, p)))
