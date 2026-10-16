# Release publication guard

Ordinary pushes to the default branch must not overwrite an already-published Networks version.

The build still creates and validates the versioned JAR on every relevant run. Before the publication step, the
workflow checks whether the target `v<version>` release already exists.

- Default-branch push + existing release: skip publication.
- Default-branch push + no release for the version: publish the first canonical asset.
- Explicit workflow dispatch or a version tag: publication remains allowed and may intentionally update that release.

This keeps development/integration commits at the current version from silently replacing a public JAR. A new public
runtime build therefore requires an intentional version bump (for example 1.0.57) or an explicit manual/tagged release.
