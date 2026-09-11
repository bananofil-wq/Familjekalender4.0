# Family Control Center roadmap

This branch expands Familjekalendern toward a full family planning hub while preserving the current app on backup branch `backup-before-family-dashboard-2026-09-11`.

## Scope

- Week view alongside month view
- Family dashboard for today/tomorrow
- Conflict detection for overlapping responsibilities
- Event locations bound to activities, with tap-to-open navigation
- Pickup/dropoff ownership and logistics notes
- Smarter reminders and preparation notes
- Calendar imports: SportAdmin/iCal first, architecture ready for Google/Outlook
- Meal planning tied to shopping list
- Improved invite/onboarding flow
- Backup/history safeguards

## Delivery approach

Features are developed on `family-control-center` and should not replace the current stable app until the branch builds cleanly and the new UX is reviewable. The stable snapshot can be restored from `backup-before-family-dashboard-2026-09-11`.
