# Releasing

Releases are built by GitHub Actions from a tag. No local machine or secret is required for a
normal release because the community key is committed.

## Tag a release

1. Update `CHANGELOG.md` and `app/build.gradle` (`configuredVersion` default) for the next
   version.
2. Commit and push to `main`; CI must be green.
3. Create and push an annotated tag:

```sh
git tag -a v1.4.0 -m "Magazine Library 1.4.0"
git push origin v1.4.0
```

The `Release` workflow then:

- checks out the tag and derives `versionName` from it (`v1.4.0` → `1.4.0`);
- runs unit tests, lint, the translation check and a release build;
- renames the four APKs to `MagazineLibrary-<abi>.apk` (the release title carries the
  version) so stable `releases/latest/download/...` links keep working;
- writes `SHA256SUMS` and `release.json` (version, commit, size and digest per file);
- publishes a GitHub Release using the matching `CHANGELOG.md` section as notes.

## Version scheme

`versionCode = major * 10000 + minor * 100 + patch`, so releases are always installable over
the previous one. Never reuse a version code or move a published tag.

## Signing

The committed `signing/community.jks` is public by design: it keeps sideload upgrades working
for everyone. If you publish your own fork, replace it and document the new key. For Google
Play, use a private key supplied through `SIGNING_*` environment variables; see
[BUILDING](BUILDING.md#signing).

## Rolling back

Titles and notes can be edited on GitHub after publication. If a build is broken, delete the
release and tag, fix the issue with a new patch version, and publish that instead of moving the
tag.
