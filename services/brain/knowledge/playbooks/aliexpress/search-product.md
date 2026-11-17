---
id: aliexpress.search-product
app: aliexpress
platform: android
package: com.alibaba.aliexpresshd
intent: find a product
params: [query, pick]
needs: []
updated: 2026-11-17
verified_on: Galaxy S24, not yet
---

# AliExpress - search for a product, read the results, open one

The person says "find a usb-c cable on AliExpress". The phone opens AliExpress,
types the query, reads the result list and answers with it. If the person also
asked to open one, the run continues by tapping the result number `pick`.

This is the read-only half of AliExpress. Buying lives in its own file with an
approval, and it is not written yet on purpose.

## Steps

```yaml
- id: open
  do: launch
  value: com.alibaba.aliexpresshd
  wait_ms: 5000
  expect:
    package: com.alibaba.aliexpresshd
  on_mismatch: stop

- id: dismiss-popup
  do: tap
  anchor:
    desc: ["Close", "Skip", "No thanks", "Not now"]
    text: ["Skip", "No thanks", "Not now"]
  optional: true
  wait_ms: 2000
  expect:
    package: com.alibaba.aliexpresshd
  on_mismatch: stop

- id: search-open
  do: tap
  anchor:
    desc: ["Search"]
    text: ["Search on AliExpress", "Search"]
  wait_ms: 3000
  expect:
    present:
      type: ["EditText"]
  on_mismatch: stop

- id: type-query
  do: type
  anchor:
    type: ["EditText"]
  value: "{{query}}"
  wait_ms: 2000
  expect:
    node_text: ["{{query}}"]
  on_mismatch: stop

- id: submit
  do: tap
  anchor:
    desc: ["Search"]
    type: ["ImageButton", "Button"]
    index: 0
  wait_ms: 6000
  expect:
    absent: ["No results", "No matches"]
  on_mismatch: stop

# The results themselves. This is the answer when the person asked to find,
# not to open: the collected rows go back as a list of title and price.
- id: results
  do: collect
  from:
    type: ["RecyclerView"]
  rows:
    title:
      type: ["TextView"]
      take: 1
    price:
      text_matches: "^(US \\$|€|£|CA \\$|AU \\$)"
  take: 5
  wait_ms: 6000
  expect:
    list_at_least: 3
  on_mismatch: stop

# Only reached when the person asked to open one. `pick` is 0-based; it
# defaults to the first result.
- id: open-product
  do: tap
  anchor:
    text_matches: "^(US \\$|€|£|CA \\$|AU \\$)"
    index: "{{pick}}"
  when: pick
  wait_ms: 5000
  expect:
    present:
      text: ["Add to cart", "Add to Cart", "Buy now"]
  on_mismatch: stop
```

## What "it worked" looks like

- **Find only:** the answer is a short list of titles with prices, taken from
  the results page in the order the phone shows them. Nothing was tapped.
- **Find and open:** the same list is on screen, then one product page opens,
  and it offers `Add to cart`. Nothing was added and nothing was paid.

## When it does not match

| where it stops | what it means | what to do |
|---|---|---|
| `open` | the app did not come to the front, or a sign-in wall is the first screen | `stop`; if a login is required, `ask` - the person signs in once |
| `dismiss-popup` | there was no popup - it is `optional`, so the run continues | nothing |
| `search-open` | the search bar has no description on this build | `stop`; add the label you see in the dump |
| `submit` | the magnifier is not an `ImageButton`, or the keyboard was the only submit | `stop`; add the right type or the keyboard action |
| `results` | fewer than three rows, or the container is not a `RecyclerView` | see below |
| `open-product` | the tapped row did not open a product page | `stop`; use the title as the anchor instead |

**If `results` fails, the list shape is different.** Two fallbacks, in order:

1. **No `RecyclerView`.** Some builds use a `ListView` or a plain `ViewGroup`
   with a fixed set of cards. Change `from.type` to what the dump shows.
2. **Rows without a currency node.** If the price is split into a symbol and an
   amount, or drawn without text, drop the `price` field and collect titles
   only. A list of titles is still a useful answer; a guessed price is not.

## Three things that bite

1. **The currency anchor is a guess.** AliExpress shows prices in the account's
   currency. Add the symbol you see; without it, `results` fails even though the
   list is on screen.
2. **Popup on launch.** Coupon banners cover the search bar. `dismiss-popup`
   closes them with `Close` or `Skip`; if a build names the button differently,
   add the label instead of tapping blindly.
3. **Titles repeat.** The search bar still holds the query, and some cards repeat
   it in the title. `take: 1` takes the first `TextView` **inside a row**, so the
   search bar is never part of a row - that is why the container matters.

## What to confirm before trusting this file

- **The package.** `com.alibaba.aliexpresshd` is the global app; AliExpress
  Business and regional builds differ. Confirm with `pm list packages`.
- **The results container.** Take one dump of the results page and check the
  container type and the depth of a card. If cards are nested two levels deep,
  `from` must point at the level whose children are the cards.
- **The title node.** In some builds the title is a `TextView` with a maximum of
  two lines and the price sits in a sibling; `take: 1` handles that.
- **`pick` behaviour.** Try `pick: 0`, `pick: 2` and one out-of-range value
   (`pick: 9`): out of range must fail with `stop`, never open a random card.
