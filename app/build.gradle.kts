import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.geom.CubicCurve2D
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.charizard.charify"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jtcharizard.charify"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"
    }

    signingConfigs {
        create("charifyDebug") {
            storeFile = file("charify-debug.keystore")
            storePassword = "charifydebug"
            keyAlias = "charify"
            keyPassword = "charifydebug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("charifyDebug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("charifyDebug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

val generateCharifyWallpapers = tasks.register("generateCharifyWallpapers") {
    doLast {
        val outDir = file("src/main/res/drawable-nodpi")
        outDir.mkdirs()
        val w = 720
        val h = 1280

        data class Palette(val name: String, val top: Color, val bottom: Color, val glow: Color, val line: Color)
        val palettes = listOf(
            Palette("theme_neon_amber.png", Color(10, 6, 4), Color(32, 11, 5), Color(255, 118, 28), Color(255, 185, 90)),
            Palette("theme_midnight_pulse.png", Color(3, 7, 18), Color(2, 12, 30), Color(42, 106, 255), Color(88, 184, 255)),
            Palette("theme_aurora_wave.png", Color(4, 8, 16), Color(3, 28, 30), Color(25, 230, 186), Color(120, 93, 255)),
            Palette("theme_ocean_frequency.png", Color(2, 10, 17), Color(2, 31, 45), Color(28, 172, 225), Color(120, 235, 255)),
            Palette("theme_synth_glow.png", Color(8, 3, 17), Color(24, 4, 40), Color(210, 35, 255), Color(255, 91, 210))
        )

        palettes.forEachIndexed { idx, p ->
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.paint = GradientPaint(0f, 0f, p.top, w.toFloat(), h.toFloat(), p.bottom)
            g.fillRect(0, 0, w, h)

            for (i in 0 until 7) {
                val alpha = 28 + i * 6
                g.color = Color(p.glow.red, p.glow.green, p.glow.blue, alpha.coerceAtMost(85))
                val size = 260 + i * 80
                val x = ((idx * 97 + i * 123) % (w + 260)) - 180
                val y = ((idx * 151 + i * 211) % (h + 320)) - 200
                g.fill(Ellipse2D.Double(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble()))
            }

            g.stroke = BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            for (i in 0 until 8) {
                g.color = Color(p.line.red, p.line.green, p.line.blue, 24 + i * 3)
                val y = 140 + i * 130
                val curve = CubicCurve2D.Double(-120.0, y.toDouble(), 170.0, (y - 180).toDouble(), 520.0, (y + 210).toDouble(), 840.0, (y + 20).toDouble())
                g.draw(curve)
            }

            g.color = Color(255, 255, 255, 8)
            for (i in 0 until 25) {
                val x = (i * 91 + idx * 47) % w
                val y = (i * 173 + idx * 89) % h
                g.fillOval(x, y, 2 + i % 3, 2 + i % 3)
            }
            g.dispose()
            ImageIO.write(image, "png", outDir.resolve(p.name))
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(generateCharifyWallpapers)
}
