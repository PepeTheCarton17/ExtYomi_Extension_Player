# FlexibleAdapter (vendored)

This module is a verbatim copy of the `flexible-adapter` library from
[arkon/FlexibleAdapter](https://github.com/arkon/FlexibleAdapter) at commit
`c80135339bcff5f7f8c2c2380329dfc155b26232` (Apache-2.0; see `LICENSE`).

It is vendored because JitPack can no longer build that fork: its legacy
`eu.davidea:grabver:1.0.1` build plugin needs `nu.studer:java-ordered-properties:1.0.1`,
which is no longer published. The only functional change is the Gradle build script,
updated to a modern Android library plugin and the dependency versions already used by ExtYomi.
