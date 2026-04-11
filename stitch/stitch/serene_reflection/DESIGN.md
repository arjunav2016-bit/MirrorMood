# Design System Specification: The Ethereal Archive

## 1. Overview & Creative North Star
**Creative North Star: The Mindful Sanctuary**

This design system rejects the "utility-first" clutter of traditional health apps in favor of a high-end editorial experience. It is designed to feel like a private, digital sanctuary—a place of quiet reflection. We move beyond the "app" feel by utilizing **Organic Asymmetry** and **Tonal Depth**. 

Instead of rigid, boxed-in grids, the layout breathes. We use wide margins, overlapping "frosted" surfaces, and a sophisticated typographic scale to establish a supportive, analytical, and empathetic presence. Every interaction should feel like a soft exhale.

---

## 2. Colors & Surface Philosophy
The palette is rooted in emotional intelligence, utilizing Material-inspired tokens to create a responsive, mood-sensitive environment.

### The "No-Line" Rule
**Strict Mandate:** Designers are prohibited from using 1px solid borders for sectioning. Boundaries must be defined solely through background color shifts. 
- To separate a content block, place a `surface_container_lowest` card atop a `surface_container_low` background. 
- Use the `surface_variant` for subtle structural differentiation without introducing harsh lines that cause visual "noise" and mental stress.

### Surface Hierarchy & Nesting
Treat the UI as a series of physical layers—like stacked sheets of fine, semi-translucent paper.
- **Base Layer:** `surface` (#f8fafa)
- **Secondary Sections:** `surface_container_low` (#f0f4f4)
- **Interactive Cards:** `surface_container_lowest` (#ffffff)
- **High-Emphasis Callouts:** `surface_container_high` (#e3e9ea)

### Signature Textures & The Glass Rule
To achieve a premium "Mirror" aesthetic, use **Glassmorphism** for floating elements (e.g., navigation bars, modal overlays). 
- Use `surface` colors at 70% opacity with a `24px` backdrop-blur.
- **The Soulful Gradient:** For primary CTAs and mood-state headers, use a subtle linear gradient from `primary` (#3f627f) to `primary_container` (#b7dbfd) at a 135-degree angle. This adds a "glow" that flat colors cannot replicate.

---

## 3. Typography
The system uses a pairing of **Manrope** for expressive headers and **Inter** for functional reading. This creates an "Editorial-meets-Interface" vibe.

*   **Display (Manrope):** Large, airy, and authoritative. Used for daily mood summaries or "How are you?" prompts. 
    *   *Scale:* `display-lg` (3.5rem) to `display-sm` (2.25rem).
*   **Headline & Title (Manrope/Inter):** `headline-lg` (2rem) provides a clear anchor for new sections.
*   **Body (Inter):** Optimized for long-form reflection notes. 
    *   *Body-lg:* 1rem for journal entries.
    *   *Body-md:* 0.875rem for metadata and descriptions.
*   **Label (Inter):** `label-md` (0.75rem) in `on_surface_variant` for subtle timestamps.

---

## 4. Elevation & Depth
We eschew traditional "drop shadows" for **Tonal Layering**.

### The Layering Principle
Depth is achieved by "stacking" the surface-container tiers. 
- Placing a `surface_container_lowest` card on a `surface_container_low` section creates a natural lift. This mimics how light hits stacked paper, reducing cognitive load.

### Ambient Shadows
When a floating effect is required (e.g., a "Log Mood" FAB), use an **Ambient Shadow**:
- **Blur:** 32px to 48px.
- **Opacity:** 4% - 8%.
- **Color:** Use a tinted version of `on_surface` (#2c3435) rather than pure black to keep the shadows feeling "airy."

### The "Ghost Border" Fallback
If accessibility requires a container boundary, use a **Ghost Border**: 
- Token: `outline_variant` (#acb3b4) at **15% opacity**.
- **Forbid:** 100% opaque, high-contrast borders.

---

## 5. Components

### Buttons & CTAs
- **Primary:** Gradient fill (`primary` to `primary_container`). `xl` (3rem) corner radius. Use `on_primary` text.
- **Secondary:** `surface_container_high` fill with `primary` text. No border.
- **Tertiary:** Text-only with `primary` color, utilizing `3.5` (1.2rem) padding for a large hit area.

### Mood Chips
- **Style:** Use `secondary_container` for the background and `on_secondary_container` for the label.
- **Shape:** `full` (9999px) pill shape.
- **Interaction:** On selection, transition to a subtle glow using the `primary_fixed_dim` color.

### Cards & Lists
- **The Divider Ban:** Never use horizontal lines to separate list items. Use `spacing-4` (1.4rem) of vertical white space or alternate between `surface` and `surface_container_low` backgrounds.
- **Cards:** Use `lg` (2rem) corner radius. Padding should follow the `spacing-6` (2rem) token to ensure content doesn't feel "cramped."

### Input Fields
- **Soft Inputs:** Use `surface_container_low` for the input track. On focus, transition the background to `surface_container_lowest` and apply a Ghost Border. 
- **Error States:** Use `error` (#a83836) for text and `error_container` for a subtle, soft-wash background.

### Custom Component: The Mood Mirror
A large, semi-transparent circle (`primary_container` with 40% opacity and 60px blur) that shifts color based on the selected mood (Green/Focused, Yellow/Happy, Blue/Neutral, Purple/Tired). This sits *behind* the content as an atmospheric backdrop.

---

## 6. Do's and Don'ts

### Do
- **Do** use intentional asymmetry. Align a headline to the left and a summary card slightly offset to the right to create an editorial flow.
- **Do** lean heavily into the `xl` (3rem) and `lg` (2rem) roundedness scale for a friendly, approachable feel.
- **Do** use high-quality, 3D or feathered emojis for mood representation to add a layer of premium craftsmanship.

### Don't
- **Don't** use 1px solid borders. It shatters the "sanctuary" feel and introduces unnecessary tension.
- **Don't** use pure black for text or shadows. Always use the `on_surface` (#2c3435) or a tinted variation.
- **Don't** cram elements. If a screen feels full, increase the spacing using the `spacing-8` (2.75rem) or `spacing-10` (3.5rem) tokens. Breathing room is a functional requirement, not a luxury.