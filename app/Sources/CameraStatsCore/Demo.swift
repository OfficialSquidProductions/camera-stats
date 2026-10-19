import Foundation

public extension Report {
    static let demoScript = #"""
Title: The Last Light

1 EXT. COASTAL ROAD - DAWN

A battered station wagon follows the coast. The lighthouse is a pin of light in the mist.

MAYA
We should have left yesterday.

ELI
And missed all this?

2 INT. STATION WAGON - DAY

Maya unfolds an old map. A red circle marks the lighthouse.

MAYA
That's where he said he'd be.

ELI
Then that's where we go.

3 EXT. LIGHTHOUSE - DAY

The car rolls to a stop. JONAH waits at the foot of the lighthouse, holding a brass key.

JONAH
I wasn't sure you'd come.

MAYA
Neither was I.

4 INT. LIGHTHOUSE STAIRWELL - DAY

Jonah leads Maya up a winding staircase. She runs a hand along the worn stone.

JONAH
One hundred and twelve steps. Your father counted every morning.

MAYA
Of course he did.

5 INT. LANTERN ROOM - DAY

Eli opens a wooden box. Inside: a letter, a photograph, and a compass.

ELI
This has your name on it.

MAYA
Let me see.

6 EXT. LIGHTHOUSE - DUSK

Maya and Jonah sit on the seawall. The tide comes in below them.

MAYA
How long do you think he waited?

JONAH
As long as he needed to.

7 INT. LANTERN ROOM - NIGHT

Maya turns the brass key. The old mechanism wakes with a low, steady hum.

MAYA
There you are.

8 EXT. COASTAL ROAD - NIGHT

Eli closes the car door. They look back. The lighthouse sweeps across the water.

ELI
Ready?

MAYA
Now I am.

"""#
    static func demo() -> Report {
        var r = Report.fixture()
        r.id = UUID().uuidString; r.entries = []; r.history = []; r.revision = 0
        r.values["title"] = "The Last Light · DEMO"
        r.values["day"] = "01"; r.values["demoScript"] = demoScript
        r.values["director"] = "Demo director (fictional)"; r.values["dp"] = "Demo DP (fictional)"
        r.values["dit"] = "N/A — student covers all duties"
        r.values["cycle"] = "DEMO 01"; r.values["professor"] = "Demo instructor (fictional)"
        r.defaults["roll"] = "A001"; r.defaults["mag"] = "Demo SSD 01"
        let descriptions = ["EXT. COASTAL ROAD — DAWN · Maya and Eli drive toward the lighthouse.", "INT. STATION WAGON — DAY · Maya unfolds the map; red circle marks the lighthouse.", "EXT. LIGHTHOUSE — DAY · Jonah greets Maya with the brass key.", "INT. LIGHTHOUSE STAIRWELL — DAY · Maya retraces her father's morning ritual.", "INT. LANTERN ROOM — DAY · Eli discovers the letter, photograph, and compass.", "EXT. LIGHTHOUSE — DUSK · Maya and Jonah talk on the seawall as the tide comes in.", "INT. LANTERN ROOM — NIGHT · Maya turns the brass key; the mechanism wakes.", "EXT. COASTAL ROAD — NIGHT · Maya and Eli look back at the lighthouse."]
        for scene in 1...8 {
            for take in 1...3 {
                r.defaults["lens"] = scene == 2 || scene == 5 ? "50mm" : "35mm"
                r.defaults["temperature"] = scene == 1 || scene == 8 ? "4300K / 0" : "5600K / 0"
                let id = r.next(scene:String(scene),device:"Built-in demo fixture")
                let i = r.entries.firstIndex{$0.id == id}!
                r.entries[i].values["remarks"] = descriptions[scene-1] + (take == 1 ? " Wide master. Hold through the pause." : take == 2 ? " Medium coverage. Clean performance." : " Detail / alternate. Check continuity against master.")
                r.entries[i].values["continuity"] = scene == 3 || scene == 7 ? "Keep the brass key in Maya's right hand; compare handover and lantern-room insert." : scene == 5 ? "Letter, photograph, compass in wooden box. Preserve prop positions between takes." : "Preserve eyelines and screen direction between Maya, Eli, and Jonah as applicable."
                r.entries[i].values["dpNotes"] = take == 2 ? "DP select — fictional demo selection" : "Preserve soft coastal light; reference only."
                r.entries[i].values["sound"] = "DEMO-S001 / scene-\(scene)-take-\(take)"
                r.entries[i].reviewed = true
                if take == 2 {r.entries[i].flags.insert("Circle")}
                if scene == 1 || scene == 8 {r.entries[i].flags.insert("MOS")}
                if scene == 7 && take == 3 {r.entries[i].flags.insert("Pickup")}
            }
        }
        r.record("Created fictional The Last Light demo report. No footage is included.",device:"Built-in demo fixture")
        return r
    }
}
