name: Build
on: [push, workflow_dispatch]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 8
      - name: Download Gradle 2.14.1
        run: |
          wget -q https://services.gradle.org/distributions/gradle-2.14.1-bin.zip
          unzip -q gradle-2.14.1-bin.zip
      - name: Build
        run: ./gradle-2.14.1/bin/gradle setupCIWorkspace build --no-daemon
      - uses: actions/upload-artifact@v4
        with:
          name: Waypoints
          path: build/libs/*.jar
