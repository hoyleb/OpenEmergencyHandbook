# Usability goals and remaining evidence

The 0.3/0.4 previews prioritise finding the right guide and reading the first action under stress. No new clinical instructions were added in this usability pass.

## Implemented flows

- Compact home: first aid, safe water and all six disaster topics are direct actions, followed by preparation and the full library. A short unreviewed-preview status remains visible.
- Persistent Help, Search, Saved and Plan controls, plus the 112 dialler and language control. Core actions do not require discovering a horizontal scroll strip.
- Search accepts English/German titles and keywords, ranks title/keyword matches above body-only mentions, and explicitly labels typo-based suggestions. Empty results offer category reset and first-aid browsing. It never silently opens a guessed protocol.
- Back restores the previous related guide or search, including its query/filter/position. The navigation stack survives recreation. Top-level tabs retain their root position while remaining predictable.
- Reader starts with title, draft status, summary and guide sections. Section jumps are available near the top; bookmarks, text-size choices and optional screen-on mode are in More. Screen-on is off at cold start, is restricted to reading while the app is foreground, and is cleared on leaving the reader/backgrounding.
- Saved guides and a six-item recent history are local. Recent history can be cleared separately from bookmarks and the household plan.
- Controls have at least 52 dp height (64 dp for home actions), headings expose accessibility semantics, and cards announce their title/summary as one button. At larger system fonts the action grid becomes one column and bottom navigation becomes two rows; text containers wrap.
- The first-run language follows English/German device locale; the user's explicit preference takes precedence.

## Automated evidence

The Android test suite covers first-screen urgent-action visibility, required bundled guides, bilingual ranked/approximate search, first-aid findability, related-guide Back restoration after recreation, bookmarks, section jumps, reader text/language persistence, screen-on opt-in/clearing, 200% system font navigation, dark palette, household persistence/export/cancellation and no internet permission.

CI tests offline on APIs 21, 23 and 36 and publishes screenshots of normal home, a reader section, 200% English/German home/reader dark home and landscape. The 200% test changes the system setting on API 36 and uses an in-process resource-configuration fixture on APIs 21/23, where the settings shell command does not notify the running app. It asserts the effective activity font scale on both, plus scaled density on APIs 21/23. This tests the actual large-text layout without claiming the old OS settings integration was exercised. Screenshots and passing assertions establish these specific conditions; they are not a substitute for real user sessions or TalkBack testing. See the linked workflow/release for the actual pass/fail result.

## Still needed before claiming high usability

- Timed tasks with unfamiliar users: locate CPR for an adult/infant, compare water warnings, find fallout protection and return to a previous search. Measure completion, wrong-guide selections and misunderstandings.
- Physical phones, including the user's device: portrait/landscape, one hand, gloves where relevant, very bright/dim conditions, update without data loss, no SIM/network, force-stop and restart.
- Actual TalkBack/switch-access sessions, focus order, keyboard navigation and source-panel announcements; contrast/accessibility-scanner assessment for all interactive states.
- Validate the entire 200% reading flow, not just the initial screens. Check very long titles, German terms, selection, dialogs and the keyboard on several OEMs.
- Hands-on validation of the implemented household backup/restore and corrupted-data fallback; real low-storage/export-failure sessions and authenticated content updating.
- Reviewed action diagrams and language comprehension work with qualified reviewers. Better navigation does not establish medical accuracy.

Design references (checked 19 October 2026):
- https://developer.android.com/guide/topics/ui/accessibility/views/apps-views
- https://developer.android.com/guide/topics/ui/accessibility/views/principles-views
- https://developer.android.com/guide/navigation/principles
- https://developer.android.com/about/versions/14/features

The 0.4 suite also tests actual landscape orientation, keyboard dismissal on returning home, grouped card/heading roles and source disclosure state. Restore confirmation keeps its actions outside the scrolling preview, including at 200% text; old feedback is cancelled before a new confirmation or activity transition. All three Android APIs passed 35 tests each in the [verified 0.4 run](VALIDATION_0_4.md).

The standalone HTML edition has shared Android search acceptance cases, responsive urgent actions, category reset and related-guide navigation. Chromium, Firefox and WebKit passed JavaScript and no-JavaScript checks, including 200% CSS text and offline reading of all 60 bilingual guides. These are desktop automation results, not actual Safari or physical-browser evidence.

See [USABILITY_TEST_PLAN.md](USABILITY_TEST_PLAN.md) for the uncompleted human/device study and [BACKUP_AND_RECOVERY.md](BACKUP_AND_RECOVERY.md) for backup/failure behaviour.
