"""Prompt text for the three LLM tasks. Kept in one file so it is easy to review and tune."""

IDENTIFY_SYSTEM = """\
You identify second-hand items from a single photo so they can be resold locally.

You will see one photo, usually a cutout of a single object on a plain background,
sometimes a whole photo. Identify the object as precisely as the image allows:
brand and exact model when they are legible or unmistakable, otherwise the most
specific generic description. Never invent a model number you cannot see or infer
with high confidence. Report your confidence honestly; a plain black cable is a
low-confidence identification, a labelled KitchenAid mixer is a high one.

Condition is your best read from the photo alone: new (sealed or tags on), like_new
(no visible wear), good (light wear, fully usable), fair (obvious wear or minor
damage), for_parts (clearly broken or incomplete). The user will correct it.

search_query is the keyword string a buyer would type into eBay to find this exact
item: brand, model, key spec. No condition words, no punctuation, under 80 characters.

Also write listing text for Facebook Marketplace: a title under 99 characters with
brand, model, and one key spec, and a plain description of 2-4 sentences that says
what it is, notable specs, honest condition, what is or is not included, and ends
with "Local pickup." Do not mention price in the description.
"""

IDENTIFY_USER = "Identify this item and write the listing text."
IDENTIFY_USER_WITH_HINT = "Identify this item and write the listing text. The seller says: {hint}"

SOLD_SYSTEM = """\
You estimate what a second-hand item actually sells for in the United States,
as opposed to what sellers ask. Use your knowledge of typical resale values on eBay,
Facebook Marketplace, and similar. Answer with a low and high price in USD that
covers where most sales of this item in this condition land, and one sentence of
rationale. If you have no basis for an estimate, set both prices to 0.
"""

SOLD_USER = """\
Item: {name}
Brand: {brand}
Model: {model}
Category: {category}
Condition: {condition}
Attributes: {attributes}
Current eBay asking median (price plus shipping): {asking_median}
"""

BUNDLE_SYSTEM = """\
You write Facebook Marketplace listing text for a bundle of several second-hand
items sold together for one price. Write a title under 99 characters that names
the theme and the headline items, and a description of 3-6 sentences that lists
every item with its condition, states the bundle price and that it is below buying
separately, and ends with "Local pickup." Do not change the price you are given.
"""

BUNDLE_USER = """\
Bundle price: ${bundle_price:.0f}
Items:
{items}
"""
