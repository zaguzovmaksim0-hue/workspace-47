# Spanish presentation and region recovery

The user requested Spanish-first public documentation, the visible brand Firma Mobile, version 0.2.11 without the optimized suffix, and moving manual certificate lock from the primary action list into the certificate menu. Package and version code remain unchanged. Historical technical documents, protocol IDs and third-party licence text retain their identity.

Region diagnosis on the existing device: location was enabled and coarse permission granted. Fused and network requests each timed out after 12 seconds; GPS delivered one fix. This proves a location reached the application, not why the subsequent geocoder/region resolution failed. No precise coordinates were recorded in the project and no new geocoding service was introduced.

Recovery changes: reuse a system fix only if its monotonic age is at most two minutes and accuracy at most 10 km; retry the existing Android geocoder once for an IO failure/empty response within the existing timeout; surface geocoding versus location-timeout failures; cancel automatic detection when the user manually chooses a region; catch unexpected detector failures rather than leaving a stuck loading state.

Unit tests cover the menu action, visible branding, fresh/stale location bounds, bounded geocoder retry, cached-provider priority, distinct error reporting and manual selection winning over a late result. CI verifies the actual optimized APK label and version. No E2E or real-portal operation. Original device failure is not claimed conclusively repaired until the user confirms a successful region detection.
