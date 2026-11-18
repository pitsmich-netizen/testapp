# FORMA signing validation

A same-repository CI pull request checks the configured GitHub Actions secret variables by compiling the release APK. This test does not print or upload secrets; only the signed APK artifact is uploaded to the private workflow artifact store.
