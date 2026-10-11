# ADSkip 图标

名称使用 ADSkip，直接表达跳过广告。图标将播放三角与跳过终点竖条结合；白色图形中的斜向留白表现越过一次打断，薄荷绿竖条提示回到播放。紫色沿用初版界面的主色。

本次使用内置 image_gen 生成新图标，原始输出保存为 `adskip-icon-source.png`，标准 1024 × 1024 导出为 `adskip-icon.png`。Android 自适应图标使用 432 × 432 资源，核心图形位于中央安全区；不在图片中预制外部圆角。另提供 Android 13 的单色图标和通知小图标。

生成提示词：

> Use case: logo-brand. Asset type: final production Android launcher icon for ADSkip, an app that skips skippable ads to resume watching drama. Create ONE 1024x1024 square icon asset, not a mockup or icon grid. Full bleed rich indigo-purple background matching #6557EE with a very subtle luminous tonal gradient. One distinctive bold white skip-forward symbol at center: a rounded play triangle pointing right, with a short vertical mint-green destination bar to its right; a small negative-space diagonal cut within the triangle subtly expresses jumping past an interruption. Feel calm, precise, premium and friendly. Vector-like crisp geometries, generous negative space, softly rounded corners within the glyph, very restrained depth, no elaborate texture. All important symbol content must fit in the centered 55% of the square, so Android circle/squircle adaptive masks never clip it. The purple canvas reaches every edge: do NOT bake in rounded-square corners, do not add outer margins. No text, no letters, no AD badge, no shield, no crossed-out symbols, no upward arrow, no device or scene, no watermark.
