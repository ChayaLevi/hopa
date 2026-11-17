---
id: whatsapp.draft-message
app: whatsapp
platform: android
package: com.whatsapp
intent: write a message
params: [contact, message]
needs: [logged-in account]
updated: 2026-11-17
verified_on: Galaxy S24, not yet
---

# WhatsApp - open a chat and type a message, without sending it

The person says "write to Alex: running late". The phone opens the chat, types
the text, and stops with the text still in the field. Nobody receives anything:
sending is a separate file with an approval in front of it.

This is the safest useful WhatsApp playbook: it proves navigation, typing and
the stop before an outward action, and it cannot embarrass anybody.

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
```

The file ends here on purpose. There is no `send` step: the chat is open, the
text is in the field, and the run stops.

## What "it worked" looks like

The chat with the contact is in front, the message text is in the input, and the
send button has appeared (WhatsApp shows the microphone until there is text).
Nothing was delivered.

## When it does not match

| where it stops | what it means | what to do |
|---|---|---|
| `open` | the app did not come to the front, or a lock screen is in the way | `stop`; if an app lock is on, `ask` |
| `first-run` | the phone has never been set up: "Agree and continue" or a number check | `ask` - a person must register once |
| `search` | the search icon has no description on this build, or the top bar differs | `stop`; take the real label from a dump |
| `type-contact` | the field did not take the text | `stop`; do not retry blindly |
| `open-chat` | the search found a contact card, not a chat, or the title differs | `stop`; add the row's real label |
| `type-message` | the input refused the text, or the send button never appeared | `stop` |

## Three things that bite

1. **A group with the same name.** The header still shows the name, so the
   check passes - and the message would go to everyone. For the draft file this
   is harmless; for `send-message.md` the approval step must show the chat name
   to the person.
2. **Unsaved numbers.** If the contact is not in the address book, the title is
   a phone number, so `node_text: ["{{contact}}"]` fails. Use the number as the
   parameter in that case.
3. **The input has no stable description on some builds.** WhatsApp sometimes
   exposes the field only as `EditText`; then `type` works, and the check on
   `node_text` is what tells you it landed.

## What to confirm before trusting this file

- The search affordance: an icon with `desc: "Search"` on older builds, an
  inline field with a hint on newer ones. Both are listed; keep the one you see.
- The input and the send button: `desc: "Message"` and `desc: "Send"` on an
  English phone. Another language shows translated labels; take them from a
  dump.
- The chat header: it may be a `TextView` with the name, or a clickable row that
  opens the contact card. The assert only needs the name to be on screen.
