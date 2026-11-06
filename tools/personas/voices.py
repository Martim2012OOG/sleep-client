"""Built-in voices of the Personas mod (the Java list for Persona.java). Pictures: art.py (original drawings).
order of the numbers: pitch tone peakHz peakDb ring ringHz drive crush echo reverb vibrato vibratoHz tremolo bandLow bandHigh gainDb chorus whisper"""
import os, shutil, sys
K="pitch tone peakHz peakDb ring ringHz drive crush echo reverb vibrato vibratoHz tremolo bandLow bandHigh gainDb chorus whisper".split()
def v(**kw):
    return [kw.get(k,0) for k in K]
# id, name, tagline, category, emoji, values
V=[
("me","Just Me","Your own voice","all","1f642",v()),
# funny
("sponge","Square Sponge","Squeaky, nasal and always cheerful","funny","1f9fd",v(pitch=4.5,tone=.25,peakHz=1300,peakDb=6,vibrato=.12,vibratoHz=5.5)),
("chipmunk","Chip","Tiny and super fast sounding","funny","1f43f",v(pitch=9,tone=.3)),
("helium","Helium Balloon","Just breathed a whole balloon","funny","1f388",v(pitch=12,tone=.35)),
("clown","Silly Clown","Wobbly, bright and goofy","funny","1f921",v(pitch=5,tone=.3,peakHz=1500,peakDb=6,vibrato=.5,vibratoHz=8)),
("baby","Baby","Tiny voice, big ideas","funny","1f476",v(pitch=7,tone=.3,vibrato=.15,vibratoHz=7)),
("kid","Little Kid","Young and bright","funny","1f9d2",v(pitch=6,tone=.2)),
("grandpa","Grandpa","Old and shaky","funny","1f474",v(pitch=-3.5,tone=-.2,vibrato=.35,vibratoHz=5,tremolo=.1,bandHigh=4200)),
("grandma","Grandma","Sweet and a little wobbly","funny","1f475",v(pitch=2,tone=-.1,vibrato=.3,vibratoHz=5,bandHigh=5000)),
("dizzy","Dizzy","The room is spinning","funny","1f635_200d_1f4ab",v(vibrato=1.5,vibratoHz=1.5,tremolo=.3)),
("fast","Fast Forward","Talking at double speed","funny","26a1",v(pitch=7,tone=.2)),
("slow","Slow Motion","Sooo... slooow...","funny","1f422",v(pitch=-8,tone=-.3,vibrato=.1,vibratoHz=1)),
("cowboy","Cowboy","Howdy partner, nasal drawl","funny","1f920",v(pitch=-2,tone=-.1,peakHz=900,peakDb=5)),
("pirate","Pirate","Arr, gravelly sea dog","funny","1f3f4_200d_2620",v(pitch=-3,tone=-.2,drive=.2,vibrato=.15,vibratoHz=3)),
("santa","Santa","Ho ho ho, warm and deep","funny","1f385",v(pitch=-5,tone=-.3,reverb=.2,gainDb=1)),
("snowman","Snowman","Cold, shivering and cheerful","funny","26c4",v(pitch=3,tone=.2,tremolo=.35,reverb=.3)),
# scary
("hacker","Hooded Hacker","Deep, gritty, a little scary","scary","1f977",v(pitch=-7.5,tone=-.35,peakHz=180,peakDb=3,ring=.22,ringHz=55,drive=.32,crush=.3,reverb=.08,bandHigh=5200,gainDb=-4)),
("demon","Imp Lord","Growly with a hellish echo","scary","1f608",v(pitch=-9,tone=-.3,ring=.12,ringHz=40,drive=.5,echo=.3,reverb=.2,vibrato=.1,vibratoHz=4)),
("villain","Masked Villain","Heavy breathing behind a mask","scary","1f9b9",v(pitch=-6,tone=-.3,ring=.1,ringHz=30,drive=.25,reverb=.2,bandHigh=6000)),
("monster","Monster","Huge growling beast","scary","1f479",v(pitch=-11,tone=-.4,drive=.45,chorus=.3)),
("goblin","Goblin","Sneaky, raspy little thing","scary","1f47a",v(pitch=3,drive=.4,peakHz=1200,peakDb=6)),
("zombie","Zombie","Brains... slow and broken","scary","1f9df",v(pitch=-4,tone=-.2,drive=.35,crush=.1,vibrato=.6,vibratoHz=3,tremolo=.2)),
("vampire","Vampire","Breathy night voice","scary","1f9db",v(pitch=-3,tone=.1,reverb=.5,whisper=.25)),
("ghost","Boo","Hollow and floaty","scary","1f47b",v(pitch=-2,tone=.2,echo=.2,reverb=.75,vibrato=.5,vibratoHz=4.5,tremolo=.35)),
("skeleton","Skeleton","Rattly and thin","scary","1f480",v(pitch=1,tone=.4,crush=.3,tremolo=.5,bandLow=600)),
("doll","Creepy Doll","Sweet... too sweet","scary","1fa86",v(pitch=6,ring=.15,ringHz=220,reverb=.3,whisper=.3)),
("giant","Big Rock","Huge, slow and booming","scary","1f5ff",v(pitch=-10,tone=-.5,peakHz=120,peakDb=4,reverb=.18,gainDb=-3)),
# fantasy
("wizard","Old Wizard","Wise, echoing tower voice","fantasy","1f9d9",v(pitch=-4,tone=-.1,echo=.15,reverb=.6,vibrato=.1,vibratoHz=3)),
("elf","Elf","Light and magical","fantasy","1f9dd",v(pitch=4,tone=.3,reverb=.25,chorus=.15)),
("fairy","Fairy","Tiny sparkly voice","fantasy","1f9da",v(pitch=10,tone=.4,reverb=.55,vibrato=.2,vibratoHz=7,chorus=.3)),
("unicorn","Unicorn","Shiny and dreamy","fantasy","1f984",v(pitch=9,tone=.4,reverb=.3,chorus=.3)),
("dragon","Dragon","Fire in the throat","fantasy","1f409",v(pitch=-9,ring=.08,ringHz=25,drive=.55,reverb=.3,chorus=.25)),
("knight","Knight","Talking inside a helmet","fantasy","1f6e1",v(pitch=-2,ring=.1,ringHz=60,reverb=.2,bandHigh=3000)),
# animals
("cat","Kitty","Meow-ish and purry","animals","1f431",v(pitch=6,peakHz=2500,peakDb=6,vibrato=.4,vibratoHz=6)),
("mouse","Mouse","Squeak squeak","animals","1f42d",v(pitch=11,tone=.4,bandLow=400)),
("bear","Bear","Big, warm and grumbly","animals","1f43b",v(pitch=-7,tone=-.4,peakHz=200,peakDb=4,drive=.15)),
("lion","Lion","Roaring king","animals","1f981",v(pitch=-8,tone=-.3,drive=.5,chorus=.2)),
("frog","Frog","Croaky and bubbly","animals","1f438",v(pitch=-2,peakHz=600,peakDb=9,drive=.3,tremolo=.3)),
("duck","Duck","Quacky and nasal","animals","1f986",v(pitch=4,peakHz=1600,peakDb=10,drive=.2,bandLow=500)),
("pig","Piggy","Snorty and round","animals","1f437",v(pitch=3,peakHz=1000,peakDb=8,drive=.25)),
("bee","Busy Bee","Buzzy and tiny","animals","1f41d",v(pitch=8,ring=.4,ringHz=220,tremolo=.6)),
("bat","Cave Bat","Squeaky echoes in the dark","animals","1f987",v(pitch=8,echo=.35,reverb=.9)),
("fish","Underwater","Talking from the bottom of the sea","animals","1f420",v(pitch=-1,vibrato=.5,vibratoHz=2.5,tremolo=.25,reverb=.4,bandHigh=900)),
# robots & sci-fi
("robot","Bolt Bot","Metallic robot","scifi","1f916",v(tone=.1,ring=.85,ringHz=90,drive=.1,crush=.15)),
("cyborg","Cyborg","Half human, half machine","scifi","1f9be",v(pitch=-2,ring=.45,ringHz=140,crush=.3,chorus=.2)),
("alien","Little Green","Warbly visitor from space","scifi","1f47d",v(pitch=3,tone=.2,ring=.35,ringHz=350,echo=.1,reverb=.15,vibrato=.8,vibratoHz=6.5)),
("ufo","UFO Captain","Signals from the mothership","scifi","1f6f8",v(pitch=5,ring=.5,ringHz=600,echo=.25,vibrato=.9,vibratoHz=9)),
("astronaut","Astronaut","Radio from the space station","scifi","1f9d1_200d_1f680",v(drive=.1,crush=.15,reverb=.1,bandLow=300,bandHigh=3400)),
("glitch","Glitch","Broken game character","scifi","1f47e",v(pitch=2,ring=.3,ringHz=900,crush=.8,tremolo=.4)),
("8bit","8-Bit","Straight out of an old console","scifi","1f579",v(pitch=3,crush=1,bandHigh=4000)),
# places & devices
("phone","Old Phone","Tinny telephone line","places","260e",v(drive=.12,crush=.2,bandLow=350,bandHigh=3200,gainDb=3)),
("radio","Old Radio","Crackly AM station","places","1f4fb",v(drive=.3,crush=.25,bandLow=500,bandHigh=2800,gainDb=4)),
("megaphone","Megaphone","LOUD announcements","places","1f4e2",v(peakHz=1800,peakDb=6,drive=.6,bandLow=600,bandHigh=3600,gainDb=2)),
("tvhost","TV Host","Polished studio voice","places","1f4fa",v(peakHz=2500,peakDb=4,drive=.1,bandLow=120,bandHigh=7000,gainDb=2)),
("trailer","Movie Trailer","In a world... deep and epic","places","1f3ac",v(pitch=-5,tone=-.3,peakHz=120,peakDb=5,reverb=.25,gainDb=2)),
("stadium","Stadium","Announcer in a huge arena","places","1f3df",v(drive=.1,echo=.45,reverb=.7,bandLow=200,gainDb=3)),
("cathedral","Cathedral","Giant stone hall","places","26ea",v(echo=.2,reverb=1)),
("cave","Cave Echo","Talk from the inside of a cave","places","26f0",v(echo=.35,reverb=.9)),
("bathroom","Shower","Singing in the shower","places","1f6bf",v(peakHz=300,peakDb=4,reverb=.55)),
# effects
("whisper","Whisper","Only air, like a secret","effects","1f92b",v(tone=.3,gainDb=3,whisper=1)),
("crowd","Crowd","A whole group talking at once","effects","1f465",v(chorus=1)),
("bass","Mega Bass","Boom boom low end","effects","1f50a",v(pitch=-1,peakHz=90,peakDb=9,drive=.2,gainDb=2)),
]
CAT={"all":"All","funny":"Funny","scary":"Scary","fantasy":"Fantasy","animals":"Animals","scifi":"Robots & Space","places":"Places & Devices","effects":"Effects"}
if __name__=="__main__":
    # prints the Java list for Persona.java (the pictures are drawn by art.py)
    rows=[]
    for i,n,t,c,e,vals in V:
        nums=", ".join(("%gF"%x) if x!=int(x) else str(int(x)) for x in vals)
        rows.append('\t\t\tb("%s", "%s", "%s", "%s", %s)'%(i,n,t,c,nums))
    print(",\n".join(rows))
    print(len(V),"voices")
