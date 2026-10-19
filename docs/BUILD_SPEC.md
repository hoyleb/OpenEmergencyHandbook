# Open Emergency Handbook — build specification

Version: 0.1 • 19 October 2026

Status: implementation requirements, not a completed app or a reviewed survival manual.

## 1. Product contract

Build a downloadable, free, open-source emergency and survival app by extending an existing open-source application. Initial target: Android, with direct APK distribution and Google Play. Maintain a separately usable, printable offline handbook from the same content.

The user requires coverage of power outages, cyberattacks, earthquakes, nuclear fallout, floods and pandemics, plus first aid and obtaining safe drinking water. These are mandatory release scope, not optional future packs.

Non-negotiable requirements:

- Free to download and use. Every emergency article, diagram, checklist and search feature is free. No ads, subscriptions, time limits or premium emergency modules.
- Open source: publish application source, build instructions, tests, original content, content-generation tooling, attribution and licences. Included third-party content must permit our intended redistribution and adaptation; record exceptions precisely.
- Independently downloadable: publish a signed standalone APK, its SHA-256 checksum, matching source release and an offline HTML/printable handbook. Store availability must not be the only distribution route.
- Offline from first launch after installation. Bundle all essential articles, images, fonts, navigation and search indexes. Do not require a first-run sign-in, asset download or licence validation.
- No compulsory account, cloud processing or telemetry. Household plans stay on the device; export and delete must work offline.
- English and German content for the initial public release; reviewed translations with equivalent practical coverage. Clearly label regional differences.
- Maintain an explicit coverage matrix and disclose gaps. Do not claim that any app guarantees survival or contains literally every possible answer.
- Distinguish evergreen instructions from live information. Never present cached warnings, water advisories, evacuation routes, shelter availability or outbreak-specific recommendations as current without a verified update.

## 2. Starting repository and implementation approach

Candidate: https://github.com/ligi/SurvivalManual

Inspection found a native Android/Kotlin application, GPLv3 COPYING file, and a separate Markdown content collection referenced as a Git submodule. The README describes offline operation and an original basis in US Army survival material. This is a useful foundation, not proof that its existing medical or survival content is current.

Implementation tasks:

1. Fork into the project owner's account, preserve upstream history and establish an upstream remote. Work on a feature branch; choose a distinct name, icon and application ID. Do not imply official agency endorsement.
2. Read repository instructions, inspect the full dependency tree and content submodule, pin exact commits, build the unchanged app, and establish a reproducible baseline before modification.
3. Audit inherited content and illustrations individually. App-code licensing does not by itself settle content ownership, accuracy or reuse rights. Quarantine unsupported legacy advice.
4. Keep content in a dedicated versioned directory/package with a portable manifest. It must build into Android and the standalone handbook without depending on GitHub or a wiki at runtime.
5. Update the Android toolchain, SDK targets and dependencies to requirements current at submission. Do not treat the inspected upstream configuration as store-ready.
6. Use native offline navigation and search. Reuse existing components where suitable; avoid an unnecessary rewrite.
7. An iOS release is a separate workstream. The current Android project is not an iPhone application. Check GPL distribution compatibility and any necessary permissions before committing to an Apple App Store port.

## 3. Required content matrix

Each scenario must cover preparation, immediate protective actions, the first day, extended disruption, recovery, common mistakes, vulnerable people and indications to seek professional help. These are editorial categories; do not invent a universal safe timeline for an event.

| Module | Required practical coverage |
| --- | --- |
| Power outage | Determining whether the fault is local; electrical hazards; lighting; battery conservation; safe alternative heat and cooking; carbon monoxide prevention; generators and backfeeding hazards; refrigerated/frozen food; medical equipment; water and sewage interruption; payment failures; safe restoration. |
| Cyberattack: personal | Recognising account compromise, fraud, malware and ransomware; using a trusted separate device; account recovery; session revocation; backups and clean recovery; payment-provider and bank contacts; preserving evidence; household communication plans. Avoid universal instructions to erase or power off every affected device. |
| Cyberattack: infrastructure | Loss of communications, power, water, banking, payments, healthcare and supply chains; trustworthy information; offline contacts; cash and essential documents; coping with simultaneous outages; avoiding scams and misinformation. |
| Earthquake | Protection during shaking for indoors, outdoors, vehicles and mobility impairments; aftershocks; structural damage and utility hazards; entrapment and signalling; injuries; family reunification; evacuation and re-entry. Include tsunami applicability for coastal travel, clearly distinguished from inland settings. |
| Nuclear fallout | Distinguish detonation/blast, fallout, reactor release and other radiation incidents; immediate cover and shelter selection; reducing outside-air contamination where appropriate; self-decontamination; separating contamination from irradiation; protected food/water; communication; shelter relocation when a building is unsafe; evacuation under competent guidance; iodine limitations and authority-directed use. Do not provide invented local fallout forecasts or phone-sensor radiation readings. |
| Flood | Early preparation; moving to safety; water and electricity; avoiding floodwater and flooded roads; basement entrapment; evacuation; contaminated water/food; sanitation; returning safely; cleaning, mould and utilities. Distinguish flash flooding from slower river flooding and groundwater ingress. |
| Pandemic | Different transmission routes and pathogen-specific limits; respiratory protection and ventilation where relevant; hygiene and sanitation; reducing household transmission; safer caregiving; protecting vulnerable people; continuity of medication and essential supplies; symptoms requiring urgent help; vaccination/testing/treatment information that needs current local advice; handling healthcare disruption and misinformation. Do not apply one respiratory-virus protocol to all outbreaks. |
| First aid | Scene safety; calling for help and alternatives when networks fail; unresponsiveness; CPR/AED; choking; severe bleeding; wounds; burns; head/spinal injury; fractures; shock; poisoning; seizures; stroke/heart-attack recognition; anaphylaxis; hypothermia/heat illness; infection prevention. Separate adult, child and infant protocols where required. Reviewed source illustrations and clearly defined limits of lay care are essential. |
| Safe water | Stored drinking water; safer source selection; contamination assessment; collection and containers; settling/prefiltration limitations; boiling; approved disinfection methods and product-specific instructions; filter performance and limitations; safe storage; avoiding recontamination; drinking/cooking versus washing uses; infant and immunocompromised needs; boil-water, do-not-drink and do-not-use advisories. |
| Food and nutrition | No-cook foods; dietary needs and allergies; rotation/storage; outage food safety; safe preparation; contamination after floods/fallout; feeding infants; avoiding unsafe foraging dependence. |
| Hygiene and waste | Handwashing under limited water; emergency toilets; waste segregation/storage; menstrual care; nappies; cleaning products and incompatible mixtures; pest control. |
| Shelter and temperature | Staying warm/dry, safe cooling, clothing and bedding; temporary shelter; building safety; fire and carbon monoxide risks; shelter-in-place versus evacuation. |
| Communication and navigation | Emergency numbers by supported region; radio information; offline family contacts; meeting plans; signalling; optional offline maps with date/status; locating help without assuming a map marker is an open shelter or safe water supply. |
| Household preparation | Household size and accessibility needs; local supply calculator with sourced assumptions; prescription planning; medical-device power; pets; evacuation bag; documents; neighbours; personal emergency plans; recovery and mental wellbeing. |

Cross-scenario combinations are mandatory: flood plus sewage contamination; earthquake plus cold/power loss; pandemic plus supply interruption; cyberattack plus unavailable mobile networks; fallout plus a damaged shelter.

## 4. Safety-sensitive distinctions the content must preserve

These are content requirements to verify against sources, not complete treatment instructions:

- Killing microorganisms is different from removing chemical or radioactive contamination. Boiling and disinfecting are not solutions for all water contamination. A cloth is not a microbial purifier. Filters vary by contaminant and specification.
- A flood evacuation instruction and a radiation shelter instruction may conflict. Present conditions and priorities, not a universal instruction to stay indoors or leave.
- Ventilation advice for airborne infection differs from temporary sheltering against contaminated outdoor air. Combined-hazard guidance requires expert review and must explain applicable conditions.
- A building threatened by fire, collapse or rising water is not automatically safe because sheltering is recommended for another hazard.
- Potassium iodide is not general radiation protection; do not promote unsupervised preventive dosing or dosing calculators.
- Generators, grills and improvised heaters introduce serious secondary risks. Address these directly in outage guidance.
- Outbreak instructions depend on the pathogen and current evidence. Stored general guidance cannot establish the current disease, legal isolation rules, approved treatment or vaccine eligibility.
- A smartphone cannot infer radiation levels, certify drinking water or certify building stability using ordinary phone sensors.

## 5. Content structure and editorial workflow

Each article needs a stable ID, title, language, scenario tags, phase tags, intended audience, applicable region, action summary, detailed explanation, conditions/exceptions, avoid/do-not-do guidance, escalation guidance, diagrams, source references, reuse basis, review date, reviewer role, next review date and revision history.

Maintain separate provenance for each substantive claim when an article combines multiple sources. Distinguish faithfully reproduced material, translated material and original adaptations. Pin citations to document editions or retrieval dates. Preserve source copies only where permitted.

Workflow: source identification → reuse assessment → extraction/drafting → source comparison → subject review → translation review → offline usability check → release approval. AI may assist drafting, indexing, terminology and gap checks. AI output alone is not a medical or technical review. Record genuine reviews; never fabricate reviewer credentials or review status.

Use explicit draft, reviewed and withdrawn states. Public emergency navigation must not silently expose draft protocols. Until review is complete, keep the build labelled as a development preview. Review existing upstream material too.

All original reusable content should use an appropriate free-content licence, such as CC BY 4.0, subject to compatibility with included material. Preserve distinct licences for third-party works rather than relabelling them. Prefer content usable without additional permission and record any adaptation or commercial-use restrictions before inclusion. Do not copy proprietary SAS or Red Cross app content on the assumption that it is freely reusable.

## 6. User experience

Home screen: Help now; Prepare; Search; My household; About/sources.

Help now exposes every required disaster plus first aid and safe water without scrolling through a long library. Design target: a critical action card reachable within two taps from the home screen. Search must work with common English/German terms and spelling errors, such as blackout, Stromausfall, bleeding, Blutung and fallout.

Each action card has a concise summary, essential conditions, diagrams, a next-step route and optional detail. Large touch targets, readable type, screen-reader labels, adjustable text and high contrast are required. Do not rely on colour alone or put essential instructions into unlabelled images. Diagrams must remain readable without animation/video. Audio is optional and must not require a network service.

Display edition/review information accessibly without making the user open a source website to obtain essential instructions. External links are supporting evidence, not substitutes for bundled guidance. Show cached/live status explicitly for any optional live data. Do not require GPS to read localised content or make emergency calls conditional on any app permission.

## 7. Downloads, updates and resilience

- Publish a universal signed Android APK for supported architectures where practical; also prepare the required store bundle. Publish exact sizes, minimum supported Android version, checksums and release notes.
- Protect signing keys outside the repository and document backup/key continuity. Never check secrets into the source tree.
- The base package contains the complete required handbook. Optional maps or videos must not be prerequisites for essential instructions.
- Publish a self-contained HTML handbook with local assets plus a printable PDF generated from the same approved source. These reduce dependence on Android or one app build.
- Permit free export of household plans in accessible formats; warn appropriately when exporting sensitive information, without blocking emergency use.
- Optional content updates must be authenticated, integrity checked, schema validated and installed atomically. Never download executable code as a content update. Retain a known working edition and survive interruption, lack of storage and invalid content.
- Offline guidance never expires into a lockout. Identify material whose review is overdue; do not erase the only available instructions when offline.
- Define a correction and withdrawal process. A later validated update can replace unsafe content; do not imply that an offline device can receive an immediate recall.
- No always-running cloud service is necessary for reading or search. Keep battery use low and publish a printable edition for loss of device power.

## 8. Acceptance criteria

Release only when all of these have evidence:

1. A fresh installation with networking disabled opens successfully, with no prior app session or asset download. Every required article, diagram, search result and internal link works offline after force-stop and device restart.
2. A machine-readable coverage matrix links every required topic to approved article IDs in both languages. Missing, draft-only, untranslated or uncited items fail the public-release content check. Automated checks establish presence and consistency; human review establishes adequacy.
3. Every shipped source and asset has documented redistribution rights and attribution. Every required high-consequence procedure has a real qualified review record appropriate to its subject. Reviewer availability is an external dependency; do not fake or bypass it.
4. Test actual scenarios and common errors: chemically contaminated water, suspected fallout, respiratory outbreak, flooded basement, prolonged cold-weather outage, compromised email and infrastructure loss. Verify applicable conditions and conflicting advice with reviewers.
5. Measure findability with users unfamiliar with the app: locating first aid, safe water and each disaster module. Include low vision, large text, screen reader and one-handed navigation.
6. App permissions denied, absent SIM/GPS, incorrect device clock, low storage, interrupted update and corrupted package leave essential bundled guidance readable. Preserve last known good content.
7. Pin dependencies; produce licence/dependency inventories; run appropriate static/security checks; verify the published APK signature/checksum; rebuild the matching source release. Claim reproducible binaries only after a reproducibility check succeeds.
8. Verify emergency numbers and regional labels. Static data cannot promise that a phone network, hospital, shelter or road is operational.
9. Complete privacy, accessibility and current store-policy requirements; seek production access where required. A working APK is not proof of store approval.
10. Publish a candid coverage/review report and known limitations. Do not advertise guaranteed survival, universal medical capability or official endorsement.

## 9. Implementation sequence and deliverables

### A. Establish the fork

Deliver a baseline build, upstream/version record, code/content licence audit, dependency inventory, initial coverage matrix and separate application identity. Preserve upstream attribution. If authentication or repository creation is unavailable, provide a local branch/source bundle and explicit remaining publishing steps; never invent a remote fork.

### B. Build the offline product

Implement portable content schema, manifest validator, local search, emergency navigation, accessibility, household planning, regional labels, offline source information and the standalone handbook build. Use clearly labelled fixture content in development rather than pretending fixtures are reviewed instructions.

### C. Complete and review all required modules

Populate the full matrix, resolve conflicting sources, review first aid/water/radiation/structural/electrical/infection content with suitable professionals and review German translations. Sample articles alone do not satisfy the public release scope.

### D. Distribute and validate

Produce a free development APK for device testing, then a reviewed beta with all modules. Test failure conditions and usability. Prepare store metadata, screenshots, privacy/health declarations, support/corrections contact and GPL source availability. Run any required store testing. Publish the approved signed release and handbook using the owner's developer account.

Do not silently purchase developer memberships, enter contracts or claim an app is published when only a local build exists. Technical preparation and submission readiness can proceed while account-dependent steps remain documented.

## 10. Source starting points

These are authoritative research starting points, not blanket grants to redistribute everything on each site. Verify currency, exact claims, country applicability and the reuse rights of every selected asset. Initial source checks: 19 October 2026.

- Existing application and GPL: https://github.com/ligi/SurvivalManual and https://github.com/ligi/SurvivalManual/blob/master/COPYING
- German civil protection and household preparedness: https://www.bbk.bund.de/DE/Warnung-Vorsorge/Vorsorge/vorsorge_node.html
- German power outage guidance: https://www.bbk.bund.de/DE/Warnung-Vorsorge/Vorsorge/Stromausfall/stromausfall_node.html
- German earthquake guidance: https://www.bbk.bund.de/DE/Themen/Risikomanagement/Baulicher-Bevoelkerungsschutz/Schutz-vor-geologischen-Gefahren/Erdbeben/erdbeben_node.html
- German natural-hazard guidance, including flood preparation: https://www.bbk.bund.de/DE/Warnung-Vorsorge/Vorsorge/Mit-Naturgefahren-umgehen/mit-naturgefahren-umgehen_node.html
- German personal cyber incident support: https://www.bsi.bund.de/DE/IT-Sicherheitsvorfall/Buergerinnen-und-Buerger/buergerinnen-und-buerger.html?pos=4
- CDC emergency water treatment: https://www.cdc.gov/water-emergency/about/
- CDC radiation guidance: https://www.cdc.gov/radiation-emergencies/about/what-to-do.html
- CDC food/water/medicine after radiation incidents: https://www.cdc.gov/radiation-emergencies/safety/food-drinking-water-medicine-safety.html
- CDC reuse policy, including third-party material exceptions: https://www.cdc.gov/other/agencymaterials.html
- WHO emergency water, sanitation and hygiene technical notes: https://www.who.int/teams/environment-climate-change-and-health/water-sanitation-and-health/environmental-health-in-emergencies/technical-notes-on-wash-in-emergencies
- WHO infection prevention in emergencies: https://www.who.int/emergencies/operations/ipc-wash
- RKI preparedness and response: https://www.rki.de/DE/Themen/Infektionskrankheiten/Preparedness-und-Response/nationale-Krisenplanung/Rahmenkonzept_Epidemische_bedeutsame_Lagen.html
- IFRC first-aid guidelines: https://www.ifrc.org/document/ifrc-international-first-aid-resuscitation-and-education-guidelines-2025
- Google Play health content policy: https://support.google.com/googleplay/android-developer/answer/16679511?hl=en
- Google Play testing for applicable personal accounts: https://support.google.com/googleplay/android-developer/answer/14151465?hl=en
- Google Play target SDK requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Apple review guidelines for a future separate iOS workstream: https://developer.apple.com/app-store/review/guidelines/

## 11. Instructions for the coding harness

Treat this as a requirements document. Begin by inspecting and building the actual upstream repository; do not claim implementation from this brief alone. Produce small reviewable changes and maintain a task list linking each requirement to code, content and evidence. Never mark review, licensing, device testing, signed release or store submission complete without the corresponding evidence. Clearly separate completed code from unreviewed content and external account/reviewer dependencies.

The finished outcome is a free downloadable application with all six disaster modules, first aid and safe water available entirely offline, supported by auditable content and a standalone handbook. A source-code skeleton, a handful of samples or a collection of online links is not the finished product.
