# STLTranslator

Simple import/export facility for Sterolithography .stl files, with support for:

- Both Binary and ASCII format variants
- Compressed (GZiped) files on-the-fly both import and export
- Basic sanity-checking of both imported and exported objects. Errors shown for:
    - Face normals inverted
    - Face normals mismatch
    - Degenerate triangles
    - Meshes that are not solids


To build, you will need the gradle build system installed on your computer, in addition to an appropriate JDK.

- Edit gradle.properties so that the `aoiLocation` property points to an appropriate ArtOfIllusion.jar
The default assumes a development build rooted in a sibling to the plugin directory.
- On the command line, run `gradle jar`. The plugin is output in `Plugins/`

