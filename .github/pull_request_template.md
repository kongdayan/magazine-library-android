## Summary

Describe the user-visible change and why it is needed.

## Area

- [ ] Discover / catalog
- [ ] Downloads
- [ ] Library
- [ ] Reader
- [ ] Settings, language or accessibility
- [ ] Build, CI or documentation

## Checklist

- [ ] `./gradlew :app:testDebugUnitTest :app:lintDebug` passes
- [ ] `python3 scripts/check-translations.py` passes (if strings changed)
- [ ] User-visible strings are in resources, not Java
- [ ] `CHANGELOG.md` updated for user-visible changes
- [ ] No cookies, tokens or copyrighted files are included in the change
