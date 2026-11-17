---
id: whatsapp.send-message
app: whatsapp
platform: android
package: com.whatsapp
intent: send a message
params: [contact, message]
needs: [logged-in account, approval]
updated: 2026-11-17
verified_on: Galaxy S24, not yet
---

# WhatsApp - send a message, with a yes in front of it

Same opening as `draft-message.md`, then one irreversible step: tap **Send**.
The phone asks the person first, and only a yes gets to the tap.

This is the only WhatsApp file that can reach another human. Treat it as the
example of how an outward action is gated, not as the default.

## Steps

```yaml
- id: open
  do: launch
  value: com.whatsapp
  wait_ms: 4000
  expect:
    package: com.whatsapp
  on_mismatch: stop

- id: first-run
  do: assert
  expect:
    absent: ["Agree and continue", "Verify your number"]
  on_mismatch: ask

- id: search
  do: tap
  anchor:
    desc: ["Search"]
    text: ["Search", "Ask Meta AI or Search"]
  wait_ms: 3000
  expect:
    present:
      type: ["EditText"]
  on_mismatch: stop

- id: type-contact
  do: type
  anchor:
    type: ["EditText"]
  value: "{{contact}}"
  wait_ms: 3000
  expect:
    node_text: ["{{contact}}"]
  on_mismatch: stop

- id: open-chat
  do: tap
  anchor:
    text: ["{{contact}}"]
    index: 0
  wait_ms: 4000
  expect:
    node_text: ["{{contact}}"]
    present:
      desc: ["Message"]
  on_mismatch: stop

- id: type-message
  do: type
  anchor:
    type: ["EditText"]
  value: "{{message}}"
  wait_ms: 2000
  expect:
    node_text: ["{{message}}"]
    present:
      desc: ["Send"]
  on_mismatch: stop

- id: approve
  do: assert
  needs_approval: true
  approval_text: "Send this to {{contact}}?"
  expect:
    present:
      desc: ["Send"]
  on_mismatch: stop

- id: send
  do: tap
  anchor:
    desc: ["Send"]
  wait_ms: 4000
  expect:
    node_text: ["{{message}}"]
    present:
      desc: ["Message"]
  on_mismatch: stop
```

## How to read the two checks after sending

- `node_text: ["{{message}}"]` - the text is still on screen, now as a sent
  bubble.
- `present: {desc: ["Message"]}` - the input is empty again, so the placeholder
  is back. That is what tells you the message left the field.

If the text is gone from the screen entirely, the send may have failed and the
bubble not rendered yet: `on_mismatch: stop`, and say so rather than "sent".

## What "it worked" looks like

The bubble with the text sits in the chat, the input is empty, and the chat name
matches the contact the person approved.

## When it does not match

| where it stops | what it means | what to do |
|---|---|---|
| `first-run` | the phone is not set up for WhatsApp | `ask` |
| `open-chat` | a group or a contact card, not the chat | `stop`; never send on a guess |
| `approve` | the person said no, or did not answer | `stop` - nothing is sent |
| `send` | the send button was not there, or the bubble did not appear | `stop`; check for "Message not sent" and report it |

## The group trap, in full

A name like "Mum" is usually one chat. A name like "Work" is often a group. The
header shows the same string either way, so the screen alone does not prove who
receives it. Two defences, both required:

1. **The approval text carries the chat name**, so the person sees who it is.
2. **The first match wins** (`index: 0`). If the search shows a contact card
   above the chat, tapping it opens a profile, and `open-chat` fails - which is
   the safe outcome.

If the person needs to send to a group on purpose, write a separate file with
the group's exact name and its own approval line. Do not reuse this one.

## What to confirm before trusting this file

- The same labels as `draft-message.md`: search, input, send.
- Whether WhatsApp asks "Send to unsaved number?" for a phone-number contact -
  if it does, that is an extra step and an extra approval.
- Online state: offline sends queue with a clock icon. The bubble may show the
  text while the message is not delivered. The receipt should not claim
  delivery, only that the send tap happened.
