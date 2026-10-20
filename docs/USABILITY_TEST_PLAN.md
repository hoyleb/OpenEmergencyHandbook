# Hands-on usability and resilience study

This is a repeatable test plan, not completed user evidence. Use fictional household data and simulated situations; the exercise tests finding information, not the correctness of unreviewed emergency procedures.

## Participants and setup

Start with 12 unfamiliar participants, six using English and six German, including at least four participants who use large text, a screen reader or another relevant accessibility aid. These are proposed project targets, not a statistical proof of safety. Expand the sample when recurring failures appear. Record device model/OS, app build, language, font/display scale, orientation, assistive technology and network state.

Install offline assets before the session, disable Wi-Fi/mobile data and avoid coaching. Randomise task order to reduce learning effects. Ask participants to describe what they are looking for without directing their taps. Record wrong-guide selections as well as time to the correct heading; completing a task after help is a separate outcome.

## Tasks and measures

| Task | Correct destination/outcome | Record |
| --- | --- | --- |
| Find adult CPR, then child/infant CPR | `adult-cpr`, `child-cpr` | First correct page, wrong age/procedure selections, time, assistance |
| Find information for unclear water contamination | `water`, `water-advisories` | Correct warnings found; misunderstandings for reviewer follow-up |
| Locate each disaster topic | `power`, `cyber`, `earthquake`, `fallout`, `flood`, `pandemic` | Findability, labels, scrolling and mistaken guide choices |
| Search with English/German words and one typo | Relevant title; approximate label noticed | Search reformulations, filter confusion, wrong-match assumptions |
| Follow a related guide and go Back | Original position/query/filter restored | Lost position, unexpected destination, recovery time |
| Use 200% text and landscape | All essential controls reachable; long guide readable | Clipping, horizontal scrolling, selection/dialog/keyboard problems |
| TalkBack/switch-access walkthrough | Headings, grouped guide cards, sections, source state and controls understood | Focus order, duplicate narration, unnamed controls, traps |
| Save a plan and restore its JSON backup | Explicit preview and correct restored notes/checklist/bookmarks | Confidence, wrong-file/cancel handling, privacy understanding |
| Recover after interrupted/failed export | Current plan remains usable; incomplete file identified | Error clarity and ability to retry |

Initial design targets: at least 90% unassisted correct guide-finding within 20 seconds for direct home topics, and no unresolved wrong-age/wrong-procedure selection pattern. Report raw counts and participant timings; do not convert this small pilot into a clinical-readiness or universal-accessibility claim. Any dangerous misunderstanding goes to a qualified content reviewer even if the navigation target is met.

## Physical-device checks

- Ben's Xiaomi plus at least one other OEM: fresh install, update with the same signing key, JSON backup before an intentional key migration, restore, force-stop, reboot, no SIM/network and incorrect clock.
- Portrait/landscape, one hand, bright/dim conditions, large display size as well as 200% text, keyboard open/closed, very long German notes and titles, source disclosures and text selection.
- TalkBack and switch access on real hardware; verify focus after tab changes, section jumps, dialogs and returning from the dialler. Automated role/state assertions do not establish this behaviour.
- Local and cloud-backed document providers: cancel picker, provider unavailable, denied URI, no writable location and insufficient storage. Do not post real contact/medical notes in feedback.
- Low battery/storage and interrupted process: verify the previous plan and an external backup remain available. Emulator fixture failures cover selected code paths, not every OS/OEM failure mode.

## Session record

Record: anonymous session ID; app commit/version; device/OS; language; accessibility settings; task ID; elapsed seconds; first selected guide ID; final guide ID; assistance; outcome; participant comment; observer issue; severity; linked fix/retest. Retain failed attempts. Re-run a task after a fix with a new unfamiliar participant where possible.
