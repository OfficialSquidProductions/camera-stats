# Original client dependency notices

These texts keep their original copyright and license terms; the application's MIT license does not replace them. `sources.json` pins their original source locations and SHA-256 digests. Skia source revision `a00c390e98` and native package `m132-a00c390e98-1` are the versions selected by Skiko 0.9.4.2. Native-package notice files are copied unchanged. PDFBox, FontBox and SQLite JDBC notices are copied unchanged from the resolved versioned JARs.

Run `python3 tools/build_client_notices.py` after a reviewed dependency/notice update. The tool verifies every original text and assembles `OPEN_SOURCE_NOTICES.txt`, which is copied into desktop resources and Android assets. The Today screen exposes the text locally without network access. JVM legal notices remain in the installed Windows runtime. Shared notices include components used only on Windows and optional upstream native-package notices.
