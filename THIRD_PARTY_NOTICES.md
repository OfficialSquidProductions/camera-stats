# Third-party and asset notices

Camera Stats application source is MIT licensed, Copyright (c) 2026 Squid Productions.

The Apple builds link system frameworks supplied by Apple, including SwiftUI, Foundation, PDFKit, CoreGraphics, CoreText, CryptoKit, Observation and the operating system's SQLite library. Frameworks and platform fonts are not copied into this repository; use remains subject to the platform's terms. SQLite itself is in the public domain; see <https://sqlite.org/copyright.html>.

`assets/brand/SP_GREEN.png` is the user-supplied Squid Productions mark. Keep this source file unchanged. The site banner is the existing Squid Productions suite banner. Names and marks are trademarks of their owner, and the MIT code license grants no trademark rights.

The built-in “The Last Light” fictional test script is the supplied Production Desk sample, reused at the owner's explicit request. Demo camera settings and report entries are fictional fixtures created for Camera Stats. No personal production report or camera footage is bundled.

No RED SDK, Blackmagic RAW SDK, vendor decoder binary, external OCR model, commercial display-font file, or proprietary camera media is redistributed. Future format and model integrations require their own license/redistribution review and notices; the application's MIT license cannot license those materials.


## Android / Windows development clients

The new clients use Kotlin and kotlinx.serialization (Apache-2.0), Jetpack Compose / AndroidX and Compose Multiplatform (Apache-2.0), Skiko/Skia and their transitive notices, Xerial SQLite JDBC (Apache-2.0 / BSD notices; SQLite is public domain), Apache PDFBox/FontBox and pdfbox-graphics2d (Apache-2.0), and the bundled Eclipse Temurin/OpenJDK runtime (GPL-2.0 with the Classpath Exception). Original dependency license/notice resources are retained in their JARs; Android packaging merges duplicate notice texts rather than excluding them. No vendor camera SDK or proprietary font is bundled. Build/test tools and their original licenses remain separate from the MIT application source.

Primary license sources: [Kotlin](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt), [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization/blob/master/LICENSE.txt), [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/blob/master/LICENSE.txt), [AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt), [SQLite JDBC](https://github.com/xerial/sqlite-jdbc/blob/master/LICENSE), [PDFBox](https://github.com/apache/pdfbox/blob/trunk/LICENSE.txt), [pdfbox-graphics2d](https://github.com/rototor/pdfbox-graphics2d/blob/master/LICENSE), [OpenJDK license and Classpath Exception](https://openjdk.org/legal/gplv2+ce.html).
