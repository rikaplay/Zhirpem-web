package com.RIKAPLAY.zhirpem_app

import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Стекло нижней панели в духе Liquid Glass (AGSL, Android 13+).
 *
 * Шейдер работает над картинкой «content»: на панели это снимок экрана под ней.
 *  - по периметру капсулы у края картинка втягивается изнутри (скос стекла), есть цветная кайма,
 *    блик сверху-слева и свечение, следующее за линзой;
 *  - под пузырём-линзой всё, что лежит под панелью, увеличивается;
 *  - центр панели остаётся ровным, поэтому иконки и положение линзы не смещаются.
 */
internal const val PANEL_AGSL = """
uniform shader content;
uniform float2 panel;
uniform float bevel;
uniform float strength;
uniform float lensX;
uniform float lensGlow;
uniform float2 lensCenter;
uniform float lensRadius;
uniform float lensAmt;
uniform float bodyAmt;
uniform float cornerR;

float2 warp(float2 sp) {
    float2 d = sp - lensCenter;
    float r = length(d);
    if (lensAmt <= 0.0 || r >= lensRadius) {
        return sp;
    }
    float k = r / lensRadius;
    return lensCenter + d * (1.0 - lensAmt * (1.0 - k * k));
}

// Линза вдоль короткой оси панели: у средней линии картинка увеличивается, к краям возвращается
// к исходной. У нижней панели это вертикальное сжатие-растяжение, у боковой — горизонтальное.
float2 body(float2 sp) {
    float2 c = panel * 0.5;
    float hs = min(c.x, c.y);
    if (c.x >= c.y) {
        float ny = clamp((sp.y - c.y) / hs, -1.0, 1.0);
        return float2(sp.x, c.y + (sp.y - c.y) * (1.0 - bodyAmt * (1.0 - ny * ny)));
    }
    float nx = clamp((sp.x - c.x) / hs, -1.0, 1.0);
    return float2(c.x + (sp.x - c.x) * (1.0 - bodyAmt * (1.0 - nx * nx)), sp.y);
}

half4 main(float2 p) {
    // расстояние до края скруглённого прямоугольника (капсула — частный случай)
    float2 hp = panel * 0.5;
    float2 cp = p - hp;
    float2 qd = abs(cp) - (hp - float2(cornerR));
    float2 qm = max(qd, float2(0.0));
    float dist = cornerR - (length(qm) + min(max(qd.x, qd.y), 0.0));
    if (dist < 0.0) {
        return content.eval(p);
    }
    if (dist > bevel) {
        return content.eval(warp(body(p)));
    }
    float t = 1.0 - dist / bevel;
    float2 n;
    if (qd.x > 0.0 && qd.y > 0.0) {
        n = normalize(qm) * sign(cp);
    } else if (qd.x > qd.y) {
        n = float2(sign(cp.x), 0.0);
    } else {
        n = float2(0.0, sign(cp.y));
    }
    float shift = strength * t * t * bevel;
    float keep = smoothstep(0.9, 1.0, t);

    half4 c = mix(content.eval(warp(body(p - n * shift))), content.eval(p), keep);
    half rr = mix(content.eval(warp(body(p - n * shift * 1.14))), content.eval(p), keep).r;
    half bb = mix(content.eval(warp(body(p - n * shift * 0.86))), content.eval(p), keep).b;
    half3 rgb = half3(rr, c.g, bb);

    float facing = clamp(dot(n, normalize(float2(-0.5, -1.0))) * 0.5 + 0.5, 0.0, 1.0);
    float near = exp(-pow((p.x - lensX) / (min(panel.x, panel.y) * 1.6), 2.0));
    float glow = t * t * t * (0.10 + 0.25 * facing + 0.35 * lensGlow * near);
    rgb += half3(c.a * glow);
    rgb = min(rgb, half3(c.a));
    return half4(rgb, c.a);
}
"""

@RequiresApi(33)
internal fun createPanelShader(): RuntimeShader = RuntimeShader(PANEL_AGSL)

/**
 * @param lensAmt сила увеличения под линзой (0 — выключено)
 * @param cornerR радиус скругления формы в пикселях (у капсулы — половина высоты)
 * @param bodyAmt сила линзы по всему телу панели (0 — выключено)
 * @param blurPx лёгкая матовость фона перед преломлением (0 — без блюра)
 */
@RequiresApi(33)
internal fun buildPanelEffect(
    shader: RuntimeShader,
    width: Float,
    height: Float,
    bevel: Float,
    strength: Float,
    lensX: Float,
    lensGlow: Float,
    lensCy: Float = height / 2f,
    lensRadius: Float = 0f,
    lensAmt: Float = 0f,
    blurPx: Float = 0f,
    bodyAmt: Float = 0f,
    cornerR: Float = height / 2f
): androidx.compose.ui.graphics.RenderEffect {
    shader.setFloatUniform("panel", width, height)
    shader.setFloatUniform("bevel", bevel)
    shader.setFloatUniform("strength", strength)
    shader.setFloatUniform("lensX", lensX)
    shader.setFloatUniform("lensGlow", lensGlow)
    shader.setFloatUniform("lensCenter", lensX, lensCy)
    shader.setFloatUniform("lensRadius", lensRadius)
    shader.setFloatUniform("lensAmt", lensAmt)
    shader.setFloatUniform("bodyAmt", bodyAmt)
    shader.setFloatUniform("cornerR", cornerR)
    val refract = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
    val effect = if (blurPx > 0.5f) {
        val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        android.graphics.RenderEffect.createChainEffect(refract, blur) // сначала блюр, потом преломление
    } else refract
    return effect.asComposeRenderEffect()
}
