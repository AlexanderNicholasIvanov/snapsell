# SnapSell design handoff (from Claude Design)

Received 2026-09-14. The handoff text is reproduced verbatim below the
implementation notes. The HTML prototype was not committed; the text is the
reference the Compose implementation follows.

## Implementation notes (SnapSell engineering)

These are the places where the handoff is a prototype stand-in and the shipped
app deliberately differs. Everything visual is matched.

| Handoff says | We do | Why |
|---|---|---|
| Condition multiplier (New 1.40 … For parts 0.35) applied on top of the asking median | Not implemented. Suggested price comes from the backend, which already prices from comps filtered to the item's condition | Applying both would double-count condition and invent a $76 gap between Good and New with no evidence. Design decision 14. |
| Changing the local-sale factor recomputes every suggestion | Same, computed locally as `PricePoints.round(asking_median × factor)` from the stored quote | Identical to the backend formula, no network call. |
| Identify resolves at 900ms + 320ms × index, retry 900ms, re-price 850ms | Real network timings. The states (identifying, failed, ready, re-pricing) and their visuals are kept | Those were prototype fakes. |
| Return dialog 1500ms after tapping Open Facebook Marketplace | Shown when the app resumes from Facebook | Same idea, tied to the real lifecycle. |
| Comp rows open an eBay search URL | Rows open the listing's own `itemWebUrl` | We have the real listing. |
| Backend URL `https://snapsell-api.fly.dev` | `BuildConfig.BACKEND_URL` | Placeholder. |
| Bundle listing text generated from a template | Text comes from `POST /bundle`; the template shape informs the prompt | The LLM writes bundle copy, decision 8. |
| Review caption "Placeholder photo · IMG_0412.HEIC · 4 objects found" | "N objects found" | Placeholder. |
| Five layout variants | Defaults only: outlines, stack, rows, comfortable, guided | Pick one of each. |
| `sc-camel-*` identifiers in code snippets | Read as camelCase (`extraSmall`, `horizontalArrangement`, `conditionMultiplier`) | Export artefact of the design tool. |
| Cutout tiles as drop targets | Real cutouts from the segmenter, or the manual box crop | Prototype affordance. |

Fonts: Archivo (OFL) bundled as static instances in `res/font/` cut from the
Google Fonts variable file at weights 400, 600, 800. Icons: Lucide (ISC) as
vector drawables `res/drawable/ic_lucide_*.xml`.

## Handoff text (verbatim)

SNAPSELL — DESIGN HANDOFF
Android · Jetpack Compose · Material 3
Full app layout, nine screens
SnapSell is an Android app for selling used things locally: photograph one item or a pile, the app outlines each object, identifies it, prices it from real eBay listings, and stages a Facebook Marketplace listing.

This document specifies Sign in, Inventory, Capture, Review items, Confirm, Item detail, Bundle builder, Hand-off and Settings. It is a Material 3 structure wearing a Modernist skin — M3 layouts (FAB, chips, tabs, pinned bottom bar, top app bar) drawn with zero corner radius, 2dp rules, flush-left labels and a single red accent.

Fidelity: High. Colours, type, spacing, timings and copy are final. Match them.
These files are a reference: an HTML prototype of intended look and behaviour — not code to port. Rebuild it in Compose.
Placeholders: Photos only. Cutouts are empty drop targets; the camera and review frames are drawn grey.

Design tokens

Colour — light
Token	Hex	Used for
bg	#f3f2f2	Screen background, primary-button label colour
surface	#eae9e9	Cards, text-field fills
text	#201e1d	All ink
accent	#ec3013	Primary fill, selection, outlines, FAB
accent-600	#dd2b0f	Primary hover
accent-700	#ae1800	Accent-coloured text — bundle price, error copy
accent-100 / 800	#fff2ef / #7c1405	Tag fill and text — listed
neutral-100 / 800	#f8f4f4 / #444141	Tag fill and text — draft, skipped
neutral-300	#d7d3d3	Comp-thumbnail placeholder block
neutral-500	#9b9797	Comp ticks on the price scale
divider	#201e1d @ 40%	Every rule and border

Colour — dark
Token	Hex	Note
bg	#191817	Primary-button label on dark is this value, not white
surface	#262423
text	#f3f2f2
divider	#f3f2f2 @ 34%
accent	#ff563c	Lifted one ramp step for the dark ground
accent-400	#ff9783	Pressed state on dark
accent-100 / 800	#4d170e / #ffc4b8	Tag fill and text

Shape
Radius is 0 everywhere. One exception: the capture shutter, a circle. In Compose this means overriding the whole M3 shape scale, so chips, cards, text fields, dialogs and the FAB all inherit it — none of M3's 8–28dp defaults should survive.

val ModernistShapes = Shapes(
    extraSmall = RectangleShape, small = RectangleShape, medium = RectangleShape,
    large = RectangleShape, extraLarge = RectangleShape,
)

Type — Archivo (400 / 600 / 800)
Headings and body are the same family. Weight and size carry the hierarchy.

Role	Size	Weight	Tracking	Where
Display price	46sp	800	−0.03em	Item detail, suggested price
Screen hero	30sp	800	−0.025em	Hand-off title
Bundle price	34sp	800	−0.02em	Bundle summary
Sold range	30sp	800	−0.02em	Item detail
Card price	28sp	800	—	Confirm card
Section head	21sp	800	—	Item detail name, dialog title
App-bar title	18sp	800	—	Every inner screen
Row price	19sp	800	—	Inventory row
Stat value	20sp	800	—	Median / low / high
Row title	15sp	800	—	Inventory row, hand-off step
Tab label	14sp	800	—	Items / Listings
Body	13–15sp	400	—	Paragraphs, comp titles
Field label	12sp	400	—	Text-field labels, @70%
Micro label	9.5–10sp	400	+0.09–0.10em, UPPER	SUGGESTED, TOTAL, ASKING MEDIAN — @72%, not lower
Tag	10–11sp	400	+0.08em, UPPER	Status chips
Sign-in wordmark is 46sp / 800 at −0.035em.

Spacing, elevation, motion
Scale is 4 / 8 / 12 / 16 / 24 / 32 dp. Screen gutter is 16dp; Sign in and Hand-off use 20–24dp because they are text-first. Only two things cast a shadow — the FAB and the hand-off dialog, both shadow-lg (0 12dp 32dp rgba(45,43,43,.22) light, rgba(0,0,0,.65) dark). Cards do not float; fills and 1–2dp rules separate them.

Motion	Duration	Curve
Card / panel enter	220ms, 6dp upward	ease
Spinner rotation	700ms	linear, infinite
Skeleton pulse	1200ms, 0.35→0.8 alpha	ease-in-out, infinite, 200ms stagger
Identify, per item	900ms + 320ms × index	—
Retry	900ms	—
Re-price	850ms	—
Marketplace return dialog	1500ms after tap	—

Shared component patterns
Build these once; every screen composes them.

Top app bar: Height 56dp, 2dp bottom divider. Back arrow in a 48dp touch box, icon 22dp at stroke 2.2. Title Archivo 800 / 18sp, flush left, weight 1f. Trailing actions are 48dp boxes with 21dp icons. No centring, no tonal surface, no elevation.

Pinned bottom bar: 2dp top divider, padding 12 / 16 / 16dp, 8dp gap. Primary is accent-filled, min-height 54dp, label flush left at 16dp, 15sp / 800. Secondary is a 1dp divider outline at the same height. With two buttons the primary takes weight 1f.

Status chip: 10sp uppercase, +0.08em, padding 3 / 8dp, zero radius. draft neutral fill · listed accent-100 fill with accent-800 text · sold and skipped 1dp accent outline.

Condition chip row: Five chips — New, Like new, Good, Fair, For parts — always all five, always visible, one tap to change. Min-height 40dp, 10dp horizontal padding, 12.5sp, nowrap. Unselected is a 1.5dp divider border; selected is accent fill with a bg label at weight 800. Wraps to two rows at 412dp; that is expected.

Cutout tile: Cutouts always sit on pure white, in both themes, with a 1dp divider border. Sizes: 34dp compact row, 40dp bundle row, 48dp filmstrip, 60dp inventory row, 84dp confirm card, 190dp full-width on item detail.

Text field: Surface fill, 1dp divider border, zero radius, min-height 44dp (48dp on detail). Label above at 12sp / 70%. Focus is a 2dp accent border — never the M3 indicator line.

Flush-left labels are load-bearing: A button wider than its label starts the text at the left padding edge, trailing icon and all. In Compose: Button(contentPadding = PaddingValues(horizontal = 16.dp)) with an inner Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start). M3's centred default is wrong throughout this design.

Screens

1 · Sign in — Get a Google account, or let a debug build through.
Full-bleed background, 24dp side gutter, no app bar. Content is bottom-weighted: the upper block is pushed down with weight 1f and bottom arrangement, the buttons pin to the bottom with 28dp padding.
- 44 × 44dp solid accent square, 20dp above the wordmark
- "SnapSell" — 46sp / 800 / −0.035em
- Tagline — 16sp, 75% opacity, max 300dp: "Photograph your stuff. Get prices from real eBay listings."
- Primary, min-height 56dp: a 20dp bg-filled square holding a 12sp accent "G", then "Sign in with Google", 12dp gap, all flush left
- Secondary, 52dp: "Continue without sign-in (dev)" — debug builds only
No-Firebase state: the Google button is replaced by a 2dp-bordered notice, 14 / 16dp padding — "Sign-in unavailable" at 14sp / 800, then 13sp body: "This build has no Firebase configuration. Add google-services.json and rebuild to enable Google sign-in." The dev button stays.

2 · Inventory (home) — Everything the app knows you own, and the way into the camera.
App bar: "SnapSell" at 19sp / 800, no back arrow, then two icon actions — layers (bundle builder) and sliders-horizontal (settings).
Tab row directly under it with a 2dp bottom divider: Items and Listings, each weight 1f, min-height 50dp, count appended at 400 weight / 55%. The active tab carries a 3dp accent underline at full opacity; inactive drops to 50%. No M3 indicator animation, no ripple pill.
Row: Item, comfortable (default) — Padding 14 / 16dp, 1dp bottom divider. 60dp cutout · name 15sp/800 single-line ellipsised · condition 12sp @60% · status chip · right column with price 19sp/800 and SUGGESTED / FINAL at 10sp uppercase @72%.
Row: Item, compact (variant) — Padding 7 / 16dp, 34dp cutout, 14sp name, 11sp condition · status meta, 15sp price, no chip.
Row: Listing — Title 15sp/800, "1 item · Good" meta, price right, then a chip row with Mark as sold as a ghost button when status is listed.
Empty state is flush left at 64 / 28dp, not centred: a 56dp square outline holding a camera icon, "Nothing here yet" at 23sp / 800, then "Point the camera at one item, or at a whole pile. SnapSell separates them out."
FAB is a 64 × 64dp square — accent fill, 26dp camera icon, shadow-lg, 16dp from the right, 22dp from the bottom. Square, not M3's rounded FAB. Reserve 96dp of bottom scroll padding so it never covers the last row.

3 · Capture — Take the photo. Nothing else on screen.
Full-bleed camera preview. Four 28dp white corner brackets at 3dp stroke, inset 20dp, the top pair at y = 72dp so they clear the back arrow. Back arrow top-left in a 52dp box, white. One line of guidance 100dp from the bottom at 13sp uppercase +0.06em, 90% white: "Point at one item, or a pile".
Shutter: 76dp circle, 3dp white ring, 6dp gap, white fill, 30dp accent camera glyph, centred, 24dp from the bottom. The only circle in the app.

4 · Review items — Confirm which objects in the photo are for sale.
App bar "Review items". The photo sits at 14 / 16dp with a 4:3 aspect ratio and a 1dp border. Caption beneath at 11sp / 50%: Placeholder photo · IMG_0412.HEIC · 4 objects found.
Outlines are positioned over the photo as percentages of its box. At runtime they come from the segmenter's bounding boxes.
Selected outline: 2.5dp solid accent. Deselected: 2dp dashed white @75%. Each carries a 22dp numbered badge at its top-left corner at −2dp offset — accent fill with a white numeral when selected, white fill with ink when not. Tapping an outline toggles it.
Chips below mirror the outlines in the default mode. Each is a 2dp-bordered pair: a 44dp "Object N" button plus a 40dp retake icon button divided by a 2dp rule; the selected pair turns accent-filled. The filmstrip variant replaces them with full-width rows — 24dp square checkbox, 48dp cutout, "Object N" and % of frame, and a Retake ghost button.
Add item enters box mode: a scrim over the photo, a dashed 2dp white rectangle and "Drag a box around the object". The button label becomes Place box; confirming adds the object. Retake photo returns to Capture.
Outliner-unavailable state: a 2dp accent-bordered notice above the photo — "On-device outlining unavailable" at 13sp / 800 accent-700, then "Your device can't run the object outliner. Draw a box around each thing you want to sell." — and the screen uses the filmstrip list instead of tappable outlines.
Bottom bar: one primary, Identify N items, disabled and relabelled "Select at least one object" when nothing is selected.

5 · Confirm — Correct the identification before any price is fetched.
This is the rule the whole product hangs on: never price an unconfirmed item. Nothing writes to the suggested-price map before the user taps Confirm.
App bar "Confirm", with 2/4 confirmed at 11sp uppercase / 55% on the right. One card per selected item, 14dp gap, surface fill, 1dp divider border, no padding on the card itself — the sections own their padding. Enter animation: 220ms fade plus a 6dp rise.
Header row, 14dp padding: 84dp cutout on white, then the right column, whose contents depend on state.
- Identifying — 16dp accent spinner and "IDENTIFYING" at 12sp uppercase @65%, then two skeleton bars 13dp tall in divider colour, pulsing, the second at 60% width.
- Failed — "Couldn't identify this one" at 14sp / 800 accent-700, "The model returned no confident match.", and a 40dp Retry secondary button.
- Ready — the Name field.
Body, 14dp padding and 12dp gaps: Brand and Model side by side at weight 1f each; the condition chip row under a 12sp "Condition" label; a Notes field placeholdered "Scratches, missing parts, pickup details".
After Confirm the card grows a price block above a 2dp rule: "SUGGESTED PRICE" at 10sp uppercase @72% over the figure at 28sp / 800, with a Detail → button on the right. Editing name, model or condition swaps that button for a small spinner and "RE-PRICING", then shows the new number.
Before Confirm the card ends with a 2dp rule and two 48dp buttons: Remove (secondary) and Confirm (primary, weight 1f). Footer note at 12sp / 55%: "Nothing is priced until you confirm the identification. Change the name, model or condition afterwards and the price is fetched again."
Bottom bar: Confirm all (secondary, only when more than one card is outstanding) and Done (primary).

6 · Item detail — Show the price, and the evidence behind it.
- 190dp white cutout band with a 2dp bottom divider.
- Name at 21sp / 800 and Brand · Model · Condition at 12sp @60%, 16dp gutter.
- Price row, baseline-aligned: "SUGGESTED" micro-label over the figure at 46sp / 800 / −0.03em, and a 126dp-wide Final price field beside it (48dp tall, 19sp / 800). The final price overrides the suggestion everywhere else in the app.
- Three-cell stat strip between a 2dp top rule and a 1dp bottom rule, cells divided by 1dp verticals: ASKING MEDIAN · LOW · HIGH. Label 9.5sp uppercase +0.09em @55%, value 20sp / 800.
- Comp line at 11.5sp @60%: From 24 active good listings · local factor 85%.
- All-conditions note, when the exact-condition search returned nothing: a 3dp accent left border on a surface fill — "No fair-condition comps were found, so these 9 use every condition. Treat the number as rough."
- Five comps under a COMPARABLE LISTINGS micro-label and a 2dp rule. Each row: 46dp white thumbnail, title at 12.5sp clamped to two lines, condition at 11sp @55%, right-aligned total at 15sp / 800 with TOTAL beneath, and an external-link icon. Tapping opens the listing in the browser.
- Estimated sold range in its own 2dp-bordered box: micro-label, the range at 30sp / 800, an outlined tag reading ESTIMATE, NOT SALES DATA, then "Modelled down from what sellers are asking. eBay does not publish completed-sale prices through the API."
The estimate label is non-negotiable. The number must never look like observed sales.
Bottom bar: Add to bundle (secondary — selects this item and opens the bundle builder) and List on Marketplace (primary).

7 · Bundle builder — Price several items as one lot, with arithmetic the user can see.
App bar "Bundle". A PRICED ITEMS micro-label, then a multi-select list: 24dp square checkbox with accent fill and a bg tick when on, 40dp cutout, name 14sp / 800, condition, price 16sp / 800. Tapping the row toggles it.
Summary card — 16dp margin, 2dp border, 16dp padding, three bands:
- Sum of 3 items at 13sp @75%, total at 19sp / 800.
- Above a 1dp rule: "Bundle discount" with a 66dp right-aligned numeric field (40dp tall, 800) and a %, then a 50–100 slider with an accent thumb and 50% / 100% end labels. Field and slider are bound both ways.
- Above a 2dp rule: BUNDLE PRICE micro-label and the result at 34sp / 800 / accent-700, with the arithmetic spelled out underneath at 11.5sp @60%: $344 × 80% = $275.
Write listing (secondary, full width, disabled when nothing is selected) reveals an editable title field and a 150dp description textarea.
Bottom bar: List on Marketplace, disabled when the selection is empty.

8 · Hand-off — The manual step. It must read as guided, not broken.
App bar "Hand-off". Title at 30sp / 800 — "Two things are ready", becoming "Waiting for you to come back" once the user leaves. Then 15sp @75%: "Marketplace won't let an app post for you, so SnapSell gets everything ready and you paste it in."
Guided variant, default: a 2dp-topped list of three steps, each a 16dp-tall row with a 1dp divider — a 28dp numbered square (accent fill when done, divider outline when not), title 15sp / 800, body 13sp @65%, and a 20dp accent check when complete.
1. Photos saved — "3 cutouts written to your Resale album." (done)
2. Text copied — "Title and description are on the clipboard." (done)
3. Paste it into Marketplace — "Facebook opens next. Pick the photos, long-press to paste." (completes once the user has left)
Both variants end with a 1dp-bordered clipboard preview: ON YOUR CLIPBOARD, the title at 14sp / 800, and the first line of the description at 12.5sp @70%.
Bottom bar: one 58dp primary, Open Facebook Marketplace, with a leading external-link icon.
Return dialog: a bottom-anchored sheet over an rgba(20,18,17,.6) scrim — 16dp inset, 2dp border, shadow-lg, 20dp padding: "Did it go up?" at 21sp / 800, "Tell SnapSell what happened so the item lands in the right place.", then stacked full-width buttons — Listed (primary, 50dp), Skipped (secondary, 50dp), Try again (ghost, 44dp, flush left). Listed sets status listed and lands on the Listings tab; Skipped sets skipped and lands on Items; Try again dismisses and stays put.

9 · Settings — The one number the user is allowed to tune, plus sign-out.
Three blocks separated by 2dp rules, 24dp apart.
- Local-sale factor — label at 16sp / 800 with the current value right-aligned at 26sp / 800 accent-700. Explanation at 13sp @70%: "Local buyers pay less than eBay buyers. Suggested prices are this share of the eBay asking median." Then a 50–120 slider, default 85, with 50% · 85% default · 120% beneath. Changing it re-prices every suggestion in the app.
- Backend URL — a read-only field at 60% opacity.
- Sign out — full-width secondary with a log-out icon, flush left.

Assets
Icons — Lucide, stroke 2, or 2.2 for navigation chevrons. Used: camera, chevron-left, chevron-right, plus, check, refresh-cw, external-link, layers, sliders-horizontal, log-out.
Photographs — placeholders. Comp thumbnails come from the eBay API with the listing.

Variants in the prototype (defaults listed first): Review selection outlines · filmstrip; Confirm layout stack · focus; Detail evidence rows · scale; Inventory density comfortable · compact; Hand-off guided · minimal. Plus three build-state toggles: no-Firebase sign-in, dev sign-in button, outliner unavailable.
