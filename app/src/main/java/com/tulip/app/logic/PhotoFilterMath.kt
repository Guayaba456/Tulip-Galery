package com.tulip.app.logic

enum class FilterPreset(val label: String) {
    NONE("Original"), BW("Blanco y negro"), SEPIA("Sepia"), COOL("Frío"), WARM("Cálido"), ROSE("Rosa suave"), SAGE("Salvia")
}

/** Matrices 4x5 (20 floats, mismo formato que android.graphics.ColorMatrix). Sin dependencias de Android. */
object PhotoFilterMath {
    val identity: FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    /** Aplica `first` y después `second`. */
    fun then(first: FloatArray, second: FloatArray): FloatArray {
        val r = FloatArray(20)
        for (i in 0 until 4) {
            for (j in 0 until 5) {
                var s = 0f
                for (k in 0 until 4) s += second[i * 5 + k] * first[k * 5 + j]
                if (j == 4) s += second[i * 5 + 4]
                r[i * 5 + j] = s
            }
        }
        return r
    }

    fun saturation(s: Float): FloatArray {
        val lr = 0.213f; val lg = 0.715f; val lb = 0.072f
        val i = 1f - s
        return floatArrayOf(
            i * lr + s, i * lg, i * lb, 0f, 0f,
            i * lr, i * lg + s, i * lb, 0f, 0f,
            i * lr, i * lg, i * lb + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** contrast: 1 = sin cambio. brightness: -1..1 (desplazamiento sobre 255). */
    fun contrastBrightness(contrast: Float, brightness: Float): FloatArray {
        val t = (0.5f * (1f - contrast) + brightness) * 255f
        return floatArrayOf(
            contrast, 0f, 0f, 0f, t,
            0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    private fun tint(r: Float, g: Float, b: Float, dr: Float = 0f, dg: Float = 0f, db: Float = 0f) = floatArrayOf(
        r, 0f, 0f, 0f, dr,
        0f, g, 0f, 0f, dg,
        0f, 0f, b, 0f, db,
        0f, 0f, 0f, 1f, 0f,
    )

    fun preset(p: FilterPreset): FloatArray = when (p) {
        FilterPreset.NONE -> identity
        FilterPreset.BW -> saturation(0f)
        FilterPreset.SEPIA -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        FilterPreset.COOL -> tint(0.92f, 1f, 1.08f)
        FilterPreset.WARM -> tint(1.08f, 1f, 0.92f)
        FilterPreset.ROSE -> tint(1.05f, 0.97f, 1.02f, 6f, 0f, 4f)
        FilterPreset.SAGE -> tint(0.97f, 1.04f, 0.97f, 0f, 4f, 0f)
    }

    fun build(preset: FilterPreset, brightness: Float, contrast: Float, saturation: Float): FloatArray {
        var m = then(saturation(saturation), contrastBrightness(contrast, brightness))
        m = then(m, preset(preset))
        return m
    }
}
