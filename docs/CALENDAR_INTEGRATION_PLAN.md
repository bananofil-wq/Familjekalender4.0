# Calendar integration plan

## Goal
Familjekalendern should behave like a first-class Android calendar app, not an isolated family planner.

## 1. Easy calendar import
- Import `.ics` files from the Android file picker.
- Import `webcal://` and `https://` iCalendar subscription links.
- Parse and preserve title, start/end time, all-day status, location, description, recurrence and external UID where available.
- Preview import before saving, with duplicate detection.
- Let the user assign imported events to a family member or `Hela familjen`.
- Support one-time import and subscribed calendars that can be refreshed later.
- Keep source metadata so imported events can be updated without creating duplicates.

## 2. Android calendar provider import/sync
- Read calendars/events already present on the phone through Android Calendar Provider after explicit user permission.
- Let the user choose which device calendars to import/sync.
- Preserve event locations and links where possible.
- Avoid silently modifying external calendars. Any write-back must be explicit.

## 3. Open calendar-event links in Familjekalendern
- Register appropriate Android intent filters so calendar-event compatible links/files can be offered to Familjekalendern.
- Handle `.ics` files and calendar invitation intents shared from other apps.
- Route imported event data into a review screen before adding it to the family calendar.

## 4. Default / preferred calendar app behavior
Android does not provide one universal `default calendar app` role across all calendar actions on every Android version/vendor. Therefore implement the closest supported behavior:
- Register Familjekalendern for calendar-event intents and supported `.ics`/iCalendar links.
- Add a Settings action explaining how to choose Familjekalendern when Android shows the app chooser and, where the OS exposes relevant default-app settings, deep-link the user there.
- Verify behavior on the target Android version/device instead of claiming a system default role that Android may not expose.

## 5. Event locations
- Manual events can store a place/address.
- Imported events preserve `LOCATION`.
- Event detail shows the place clearly.
- Tapping the location can open navigation/maps.

## Acceptance criteria
- User can import an `.ics` calendar/event with only a few taps.
- User can subscribe to an iCalendar URL without re-entering events manually.
- Duplicate imports do not create repeated events.
- Imported location appears on the event.
- Android can offer Familjekalendern as a handler for supported calendar files/intents.
- Settings clearly expose calendar import and Android integration controls.
