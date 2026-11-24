#!/usr/bin/env python3
"""Generate the shared A2UI fixtures (ADR-0027).

Writes ``spec/fixtures/a2ui/surfaces.json``, ``replies.json`` and
``builders.json``. Every HOPA client replays them: the Kotlin core in
``core/contracts`` today, the C# and ArkTS renderers later (ADR-0018). The
cases are written here as code so a variant ("the payment card, with another
amount") is one line, and the JSON stays the reviewed contract.

``tools/checks/check-a2ui.py`` validates the output against the HOPA and the
upstream schemas. ``--check`` fails when the committed files are stale.
"""

from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "spec" / "fixtures" / "a2ui"
V = "1.0"
CAT = "https://hopa.dev/contracts/a2ui/hopa-catalog.v1.json"
ACK = "I understand money may leave my account."


def bind(path):
    return {"@path": path}


def text(id_, value, variant=None):
    c = {"id": id_, "component": "Text", "text": value}
    if variant:
        c["variant"] = variant
    return c


def button(id_, child, name, variant=None, context=None):
    c = {"id": id_, "component": "Button", "child": child}
    if variant:
        c["variant"] = variant
    event = {"name": name}
    if context is not None:
        event["context"] = context
    c["action"] = {"event": event}
    return c


def create(sid, components=None, data=None):
    body = {"surfaceId": sid, "catalogId": CAT}
    if components is not None:
        body["components"] = components
    if data is not None:
        body["dataModel"] = data
    return {"version": V, "createSurface": body}


# ---------------------------------------------------------------- builders
def approval_messages(req, sid):
    payment = req["isPayment"]
    content = ["title", "body"]
    comps = [
        {"id": "root", "component": "Card", "child": "content"},
        None,  # content, filled below
        text("title", bind("/approval/title")),
        text("body", bind("/approval/body")),
    ]
    approval = {"title": req["title"], "body": req["body"]}
    data = {"approval": approval}
    if payment:
        notice = {"id": "notice", "component": "PaymentNotice", "amount": bind("/payment/amount")}
        pay = {"amount": req["amount"]}
        if req["merchant"]:
            notice["merchant"] = bind("/payment/merchant")
            pay["merchant"] = req["merchant"]
        if req["consequence"]:
            notice["consequence"] = bind("/payment/consequence")
            pay["consequence"] = req["consequence"]
        notice["acknowledge"] = ACK
        comps.append(notice)
        content.append("notice")
        data["payment"] = pay
    else:
        if req["amount"]:
            comps.append(text("amountLine", bind("/approval/amountLine")))
            content.append("amountLine")
            approval["amountLine"] = "Amount: " + req["amount"]
        if req["merchant"]:
            comps.append(text("merchantLine", bind("/approval/merchantLine")))
            content.append("merchantLine")
            approval["merchantLine"] = "Merchant: " + req["merchant"]
    comps.append(text("actionLine", bind("/approval/actionLine"), "caption"))
    content.append("actionLine")
    approval["actionLine"] = "Action: " + req["action"]["kind"]
    content.append("buttons")
    comps += [
        {"id": "buttons", "component": "Row", "children": ["deny", "allow"], "justify": "end"},
        button("deny", "denyLabel", "deny", "borderless"),
        text("denyLabel", "Deny"),
        button("allow", "allowLabel", "approve", "primary"),
        text("allowLabel", "Allow"),
    ]
    comps[1] = {"id": "content", "component": "Column", "children": content}
    return [create(sid, comps, data)]


def choice_messages(question, options, sid):
    ids = [f"option{i}" for i in range(len(options))]
    comps = [
        {"id": "root", "component": "Card", "child": "content"},
        {"id": "content", "component": "Column", "children": ["question"] + ids},
        text("question", bind("/ask/question")),
    ]
    for i, option in enumerate(options):
        comps.append(button(f"option{i}", f"option{i}Label", "choose", context={"value": option}))
        comps.append(text(f"option{i}Label", bind(f"/ask/options/{i}")))
    return [create(sid, comps, {"ask": {"question": question, "options": options}})]


plain_req = {
    "title": "Open Settings?",
    "body": "HOPA wants to open Wi-Fi settings on your phone.",
    "amount": "",
    "merchant": "",
    "consequence": "",
    "isPayment": False,
    "action": {"kind": "launch"},
}
pay_req = {
    "title": "Confirm payment",
    "body": "Money may leave your account.",
    "amount": "12.40",
    "merchant": "Example Shop",
    "consequence": "HOPA cannot undo a payment.",
    "isPayment": True,
    "action": {"kind": "tap"},
}
pay_min_req = dict(pay_req, merchant="", consequence="", amount="")
sensitive_req = dict(plain_req, title="Send the message?", body="To Dana: on my way", amount="5", merchant="Bank", action={"kind": "type"})

builders = {
    "version": V,
    "approvals": [
        {"name": "a plain approval", "surfaceId": "appr_tsk_42_1", "request": plain_req,
         "messages": approval_messages(plain_req, "appr_tsk_42_1")},
        {"name": "a payment with every detail", "surfaceId": "appr_tsk_01h_2_1", "request": pay_req,
         "messages": approval_messages(pay_req, "appr_tsk_01h_2_1")},
        {"name": "a payment with no details still shows the notice", "surfaceId": "appr_t_3", "request": pay_min_req,
         "messages": approval_messages(pay_min_req, "appr_t_3")},
        {"name": "a non-payment with an amount shows plain lines", "surfaceId": "appr_t_4", "request": sensitive_req,
         "messages": approval_messages(sensitive_req, "appr_t_4")},
    ],
    "choices": [
        {"name": "three options", "surfaceId": "ask_turn_7_1", "question": "Which account?",
         "options": ["Personal", "Work", "Family"],
         "messages": choice_messages("Which account?", ["Personal", "Work", "Family"], "ask_turn_7_1")},
    ],
}

# ---------------------------------------------------------------- surfaces
plain = approval_messages(plain_req, "s1")[0]
payment = approval_messages(pay_req, "s2")[0]
choice = choice_messages("Which account?", ["Personal", "Work"], "s3")[0]


def with_components(message, sid, mutate):
    m = copy.deepcopy(message)
    m["createSurface"]["surfaceId"] = sid
    mutate(m["createSurface"]["components"], m["createSurface"])
    return m


def idx(components, id_):
    return next(i for i, c in enumerate(components) if c["id"] == id_)


cases = []


def ok(name, messages, state="ready", **extra):
    cases.append(dict({"name": name, "ok": True, "state": state, "messages": messages}, **extra))


def bad(name, messages, code, path, schema):
    cases.append({"name": name, "ok": False, "schema": schema, "error": {"code": code, "path": path}, "messages": messages})


ok("a plain approval card in one message", [plain])
ok("a payment card with a notice", [payment])
ok("a choice card", [choice])
streamed = copy.deepcopy(plain)
sid = "s4"
comps = streamed["createSurface"].pop("components")
data = streamed["createSurface"].pop("dataModel")
streamed["createSurface"]["surfaceId"] = sid
ok("a card streamed in three messages, data before components", [
    streamed,
    {"version": V, "updateDataModel": {"surfaceId": sid, "path": "/", "value": data}},
    {"version": V, "updateComponents": {"surfaceId": sid, "components": comps}},
])
bad("components bound to data not sent yet", [
    copy.deepcopy(streamed) | {"createSurface": dict(streamed["createSurface"], surfaceId="s4b")},
    {"version": V, "updateComponents": {"surfaceId": "s4b", "components": comps}},
], "VALIDATION_FAILED", f"/updateComponents/components/{idx(comps, 'title')}/text", True)
s5 = with_components(plain, "s5", lambda c, b: None)
ok("a data update replaces one value", [
    s5,
    {"version": V, "updateDataModel": {"surfaceId": "s5", "path": "/approval/title", "value": "Open Settings now?"}},
], expect={"text": {"title": "Open Settings now?"}})
s6 = copy.deepcopy(plain)
s6["createSurface"]["surfaceId"] = "s6"
ok("a surface can be deleted", [s6, {"version": V, "deleteSurface": {"surfaceId": "s6"}}], state="deleted")
s7 = copy.deepcopy(plain)
s7["createSurface"]["surfaceId"] = "s7"
s7["createSurface"].pop("components")
ok("a surface without a root is pending", [s7], state="pending")

# --- refused by the schema
m = copy.deepcopy(plain); m["version"] = "v1.0"; m["createSurface"]["surfaceId"] = "r1"
bad("the version must be the string 1.0", [m], "UNSUPPORTED", "/version", False)
m = copy.deepcopy(plain); m["version"] = "0.9"; m["createSurface"]["surfaceId"] = "r2"
bad("an older version is refused", [m], "UNSUPPORTED", "/version", False)
m = with_components(plain, "r3", lambda c, b: c.__setitem__(idx(c, "title"), {"id": "title", "component": "Image", "url": "https://example.com/x.png"}))
bad("a component outside the HOPA catalog", [m], "VALIDATION_FAILED", f"/createSurface/components/{idx(plain['createSurface']['components'], 'title')}/component", False)
m = with_components(plain, "r4", lambda c, b: c[idx(c, "allow")].__setitem__("action", {"functionCall": {"@call": "openUrl", "args": {"url": "https://example.com"}}}))
bad("a functionCall action", [m], "UNSUPPORTED", f"/createSurface/components/{idx(plain['createSurface']['components'], 'allow')}/action/functionCall", False)
def hyphen(c, b):
    c[idx(c, "allow")]["id"] = "allow-button"
    c[idx(c, "buttons")]["children"] = ["deny", "allow-button"]
m = with_components(plain, "r5", hyphen)
# The first problem in message order wins: the row names the bad id before the button defines it.
bad("an id with a hyphen", [m], "VALIDATION_FAILED", f"/createSurface/components/{idx(plain['createSurface']['components'], 'buttons')}/children/1", False)
m = copy.deepcopy(plain); m["createSurface"]["surfaceId"] = "r6"; m["createSurface"]["catalogId"] = "https://a2ui.org/specification/v1_0/catalogs/basic/catalog.json"
bad("another catalog", [m], "UNSUPPORTED", "/createSurface/catalogId", False)
m = copy.deepcopy(plain); m["createSurface"]["surfaceId"] = "r7"; m["createSurface"]["sendDataModel"] = True
bad("sendDataModel true", [m], "UNSUPPORTED", "/createSurface/sendDataModel", False)
m = with_components(plain, "r8", lambda c, b: c[idx(c, "title")].__setitem__("color", "red"))
bad("an unknown component property", [m], "VALIDATION_FAILED", f"/createSurface/components/{idx(plain['createSurface']['components'], 'title')}/color", False)
bad("a function call message", [{"version": V, "callRendererFunction": {"functionCallId": "f1", "callFunction": {"@call": "openUrl", "catalogId": CAT, "args": {}}}}], "UNSUPPORTED", "/callRendererFunction", False)
m = copy.deepcopy(plain); m["createSurface"]["surfaceId"] = "r10"; m["deleteSurface"] = {"surfaceId": "r10"}
bad("two payloads in one message", [m], "VALIDATION_FAILED", "", False)
m = with_components(plain, "r11", lambda c, b: c[idx(c, "title")].__setitem__("text", {"@call": "formatString", "args": {"value": "${/approval/title}"}}))
bad("a function call in a value", [m], "UNSUPPORTED", f"/createSurface/components/{idx(plain['createSurface']['components'], 'title')}/text/@call", False)
m = with_components(plain, "r12", lambda c, b: c[idx(c, "content")].__setitem__("children", {"componentId": "title", "path": "/items"}))
bad("a children template", [m], "UNSUPPORTED", f"/createSurface/components/{idx(plain['createSurface']['components'], 'content')}/children", False)
bad("updateDataModel needs a value", [{"version": V, "updateDataModel": {"surfaceId": "r13", "path": "/a"}}], "VALIDATION_FAILED", "/updateDataModel/value", False)

# --- valid A2UI, refused by HOPA's graph rules
m = with_components(plain, "g1", lambda c, b: c[idx(c, "allow")].__setitem__("child", "missing"))
bad("a child that does not exist", [m], "VALIDATION_FAILED", f"/createSurface/components/{idx(plain['createSurface']['components'], 'allow')}/child", True)
def cycle(c, b):
    c[idx(c, "buttons")]["children"] = ["deny", "allow", "content"]
m = with_components(plain, "g2", cycle)
bad("a cycle", [m], "VALIDATION_FAILED", f"/createSurface/components/{idx(plain['createSurface']['components'], 'buttons')}/children/2", True)
m = with_components(plain, "g3", lambda c, b: c.append(text("stray", "never shown")))
bad("a component nobody contains", [m], "VALIDATION_FAILED", f"/createSurface/components/{len(plain['createSurface']['components'])}", True)
def notice_in_row(c, b):
    c[idx(c, "content")]["children"].remove("notice")
    c[idx(c, "buttons")]["children"] = ["notice", "deny", "allow"]
m = with_components(payment, "g4", notice_in_row)
bad("a payment notice inside a row", [m], "UNALLOWED_PARENT", f"/createSurface/components/{idx(payment['createSurface']['components'], 'notice')}", True)
def button_holds_column(c, b):
    c[idx(c, "allow")]["child"] = "inner"
    c.append({"id": "inner", "component": "Column", "children": ["allowLabel"]})
m = with_components(plain, "g5", button_holds_column)
bad("a button that holds a column", [m], "UNALLOWED_CHILD", f"/createSurface/components/{idx(plain['createSurface']['components'], 'allow')}/child", True)
def nested_card(c, b):
    c[idx(c, "content")]["children"].append("inner")
    c.append({"id": "inner", "component": "Card", "child": "innerText"})
    c.append(text("innerText", "boxed"))
m = with_components(plain, "g6", nested_card)
bad("a card that is not the root", [m], "UNALLOWED_PARENT", f"/createSurface/components/{len(plain['createSurface']['components'])}", True)
m = with_components(plain, "g7", lambda c, b: c[idx(c, "title")].__setitem__("text", bind("/approval/nothing")))
bad("a binding to a missing value", [m], "VALIDATION_FAILED", f"/createSurface/components/{idx(plain['createSurface']['components'], 'title')}/text", True)
m = with_components(plain, "g8", lambda c, b: c.append(copy.deepcopy(c[idx(c, "title")])))
bad("the same id twice in one message", [m], "VALIDATION_FAILED", f"/createSurface/components/{len(plain['createSurface']['components'])}/id", True)
bad("an update before the surface exists", [{"version": V, "updateComponents": {"surfaceId": "g9", "components": [text("root", "x")]}}], "INTEGRITY_ERROR", "/updateComponents/surfaceId", True)
m = copy.deepcopy(plain); m["createSurface"]["surfaceId"] = "g10"
bad("a surface id used twice", [m, copy.deepcopy(m)], "INTEGRITY_ERROR", "/createSurface/surfaceId", True)
m = copy.deepcopy(plain); m["createSurface"]["surfaceId"] = "g11"
bad("a surface id cannot come back after a delete", [m, {"version": V, "deleteSurface": {"surfaceId": "g11"}}, copy.deepcopy(m)], "INTEGRITY_ERROR", "/createSurface/surfaceId", True)
m = with_components(plain, "g12", lambda c, b: c.__setitem__(idx(c, "root"), {"id": "root", "component": "PaymentNotice", "amount": "1", "acknowledge": "ok"}))
bad("a payment notice as the root", [m], "UNALLOWED_PARENT", f"/createSurface/components/{idx(plain['createSurface']['components'], 'root')}", True)
m = copy.deepcopy(plain); m["createSurface"]["surfaceId"] = "g13"; m["createSurface"]["dataModel"]["approval"]["title"] = 42
bad("a binding to a number", [m], "VALIDATION_FAILED", f"/createSurface/components/{idx(plain['createSurface']['components'], 'title')}/text", True)

# --- HOPA's approval rules on a valid surface
approval_cases = []


def approval_case(name, message, is_payment, ok_, reason=None):
    approval_cases.append({"name": name, "isPayment": is_payment, "ok": ok_, "reason": reason, "messages": [message]})


approval_case("the built plain card passes", plain, False, True)
approval_case("the built payment card passes", payment, True, True)
def drop_notice(c, b):
    c[idx(c, "content")]["children"].remove("notice")
    del c[idx(c, "notice")]
approval_case("a payment without a notice", with_components(payment, "a1", drop_notice), True, False, "a payment card needs one PaymentNotice")
def drop_deny(c, b):
    c[idx(c, "buttons")]["children"] = ["allow"]
    del c[idx(c, "deny")]
    del c[idx(c, "denyLabel")]
approval_case("no deny button", with_components(plain, "a2", drop_deny), False, False, "an approval card needs exactly one deny button")
def extra_button(c, b):
    c[idx(c, "buttons")]["children"].append("pay")
    c.append(button("pay", "payLabel", "pay"))
    c.append(text("payLabel", "Pay now"))
approval_case("a third button", with_components(plain, "a3", extra_button), False, False, "an approval card only has approve and deny buttons")
def two_approves(c, b):
    c[idx(c, "deny")]["action"] = {"event": {"name": "approve"}}
approval_case("two approve buttons", with_components(plain, "a4", two_approves), False, False, "an approval card needs exactly one approve button")
def context_on_approve(c, b):
    c[idx(c, "allow")]["action"]["event"]["context"] = {"amount": "0.01"}
approval_case("an approve button with context", with_components(plain, "a5", context_on_approve), False, False, "approval buttons carry no context")
approval_case("a notice on a non-payment card is allowed", with_components(payment, "a6", lambda c, b: None), False, True)


# --- a card someone else sent, checked against the typed request it came with (Phase 3)
received_cases = []


def received(name, request, messages, shown, reason=None):
    received_cases.append({"name": name, "request": request, "a2ui": messages, "shown": shown, "reason": reason})


def sent(request, sid, mutate=None, data=None):
    m = approval_messages(request, sid)[0]
    if mutate:
        mutate(m["createSurface"]["components"], m["createSurface"])
    if data:
        data(m["createSurface"]["dataModel"])
    return [m]


received("the built plain card is shown", plain_req, sent(plain_req, "r1"), True)
received("the built payment card is shown", pay_req, sent(pay_req, "r2"), True)
received("a payment with no details is shown", pay_min_req, sent(pay_min_req, "r3"), True)
received("a plain card with amount and merchant lines is shown", sensitive_req, sent(sensitive_req, "r4"), True)
def reworded_action(c, b):
    b["dataModel"]["approval"]["actionLine"] = "This will type text for you"
received("a sender may change wording that is not the request", plain_req, sent(plain_req, "r5", reworded_action), True)
def add_divider(c, b):
    c[idx(c, "content")]["children"].insert(2, "rule")
    c.append({"id": "rule", "component": "Divider"})
received("a sender may add layout", plain_req, sent(plain_req, "r6", add_divider), True)
received("another amount on a payment", pay_req,
         sent(pay_req, "r7", data=lambda d: d["payment"].update(amount="1240.00")), False,
         "the PaymentNotice does not show the request's amount")
received("another merchant on a payment", pay_req,
         sent(pay_req, "r8", data=lambda d: d["payment"].update(merchant="Other Shop")), False,
         "the PaymentNotice does not show the request's merchant")
def drop_merchant(c, b):
    del c[idx(c, "notice")]["merchant"]
received("a payment that hides the merchant", pay_req, sent(pay_req, "r9", drop_merchant), False,
         "the PaymentNotice does not show the request's merchant")
received("a payment that hides the consequence", pay_req,
         sent(pay_req, "r10", data=lambda d: d["payment"].update(consequence="")), False,
         "the PaymentNotice does not show the request's consequence")
def soft_ack(c, b):
    c[idx(c, "notice")]["acknowledge"] = "OK"
received("a payment with another acknowledgement", pay_req, sent(pay_req, "r11", soft_ack), False,
         "the PaymentNotice asks for HOPA's own acknowledgement")
received("another title", plain_req,
         sent(plain_req, "r12", data=lambda d: d["approval"].update(title="Open Wi-Fi?")), False,
         "the card does not show the request's title")
received("another body", plain_req,
         sent(plain_req, "r13", data=lambda d: d["approval"].update(body="Nothing happens.")), False,
         "the card does not show the request's body")
received("a plain card that hides the amount", sensitive_req,
         sent(sensitive_req, "r14", data=lambda d: d["approval"].update(amountLine="Amount: see app")), False,
         "the card does not show the request's amount")
received("a plain card that hides the merchant", sensitive_req,
         sent(sensitive_req, "r15", data=lambda d: d["approval"].update(merchantLine="Merchant: -")), False,
         "the card does not show the request's merchant")
received("a payment notice on a card that is not a payment", plain_req,
         [with_components(payment, "r16", lambda c, b: b["dataModel"]["approval"].update(
             title=plain_req["title"], body=plain_req["body"]))], False,
         "a card that is not a payment has no PaymentNotice")
def swap_labels(c, b):
    c[idx(c, "allowLabel")]["text"] = "Deny"
    c[idx(c, "denyLabel")]["text"] = "Allow"
received("swapped button labels", plain_req, sent(plain_req, "r17", swap_labels), False,
         "the approve button reads Allow")
def deny_reads_ok(c, b):
    c[idx(c, "denyLabel")]["text"] = "OK"
received("a deny button that reads OK", plain_req, sent(plain_req, "r18", deny_reads_ok), False,
         "the deny button reads Deny")
def a11y_on_allow(c, b):
    c[idx(c, "allow")]["@accessibility"] = {"label": "Deny"}
received("an approve button with a spoken label", plain_req, sent(plain_req, "r19", a11y_on_allow), False,
         "approval buttons carry no accessibility text of their own")
received("the approval rules run first", pay_req,
         [with_components(payment, "r20", drop_notice)], False, "a payment card needs one PaymentNotice")
received("two surfaces in one card", plain_req, sent(plain_req, "r21") + sent(plain_req, "r22"), False,
         "an approval card is one surface")
received("a card without its root", plain_req,
         sent(plain_req, "r23", lambda c, b: c.pop(idx(c, "root"))), False, "the card never became complete")
received("a2ui that is not an array", plain_req, {"version": V}, False, "a2ui is an array of messages")

surfaces = {"version": V, "cases": cases, "approvalCases": approval_cases, "receivedCases": received_cases}

# ---------------------------------------------------------------- replies
TS = "2026-11-22T10:15:00Z"


def action(name="approve", sid="appr_tsk_42_1", source="allow", context=None, ts=TS):
    return {"version": V, "action": {"name": name, "surfaceId": sid, "sourceComponentId": source, "timestamp": ts, "context": context or {}}}


reply_cases = [
    {"name": "an approve tap", "ok": True, "message": action()},
    {"name": "a choice tap", "ok": True, "message": action("choose", "ask_turn_7_1", "option1", {"value": "Work"})},
    {"name": "a validation error", "ok": True, "message": {"version": V, "error": {"code": "VALIDATION_FAILED", "surfaceId": "s1", "path": "/createSurface/components/3/child", "message": "child missing does not exist"}}},
    {"name": "a generic error", "ok": True, "message": {"version": V, "error": {"code": "UNSUPPORTED", "surfaceId": "s1", "message": "version v1.0 is not supported"}}},
]
m = action(); m["version"] = "v1.0"
reply_cases.append({"name": "a reply with the wrong version", "ok": False, "schema": False, "error": {"code": "UNSUPPORTED", "path": "/version"}, "message": m})
m = action(); del m["action"]["timestamp"]
reply_cases.append({"name": "an action without a timestamp", "ok": False, "schema": False, "error": {"code": "VALIDATION_FAILED", "path": "/action/timestamp"}, "message": m})
m = action(); m["action"]["userMessage"] = "I approve"
reply_cases.append({"name": "an action with a userMessage", "ok": False, "schema": False, "error": {"code": "UNSUPPORTED", "path": "/action/userMessage"}, "message": m})
reply_cases.append({"name": "an action with a timestamp that is not a time", "ok": False, "schema": True, "error": {"code": "VALIDATION_FAILED", "path": "/action/timestamp"}, "message": action(ts="yesterday")})
m = action(); m["action"]["context"] = {"value": 3}
reply_cases.append({"name": "an action with a non-string context value", "ok": False, "schema": False, "error": {"code": "VALIDATION_FAILED", "path": "/action/context/value"}, "message": m})

agreement = [
    {"name": "allow with a matching approve tap", "allow": True, "surfaceId": "appr_tsk_42_1", "action": action()["action"], "allowed": True},
    {"name": "allow from a client without A2UI", "allow": True, "surfaceId": "appr_tsk_42_1", "action": None, "allowed": True},
    {"name": "deny stays deny", "allow": False, "surfaceId": "appr_tsk_42_1", "action": action("deny", source="deny")["action"], "allowed": False},
    {"name": "allow with a deny tap is deny", "allow": True, "surfaceId": "appr_tsk_42_1", "action": action("deny", source="deny")["action"], "allowed": False},
    {"name": "deny with an approve tap is deny", "allow": False, "surfaceId": "appr_tsk_42_1", "action": action()["action"], "allowed": False},
    {"name": "allow for another surface is deny", "allow": True, "surfaceId": "appr_tsk_42_2", "action": action()["action"], "allowed": False},
    {"name": "allow from another button is deny", "allow": True, "surfaceId": "appr_tsk_42_1", "action": action(source="deny")["action"], "allowed": False},
]
chosen = [
    {"name": "a listed option", "surfaceId": "ask_turn_7_1", "options": ["Personal", "Work", "Family"], "action": action("choose", "ask_turn_7_1", "option1", {"value": "Work"})["action"], "chosen": "Work"},
    {"name": "an option that was not offered", "surfaceId": "ask_turn_7_1", "options": ["Personal", "Work"], "action": action("choose", "ask_turn_7_1", "option1", {"value": "Admin"})["action"], "chosen": None},
    {"name": "a tap on another surface", "surfaceId": "ask_turn_7_2", "options": ["Personal", "Work"], "action": action("choose", "ask_turn_7_1", "option1", {"value": "Work"})["action"], "chosen": None},
    {"name": "the wrong event name", "surfaceId": "ask_turn_7_1", "options": ["Personal", "Work"], "action": action("approve", "ask_turn_7_1", "option1", {"value": "Work"})["action"], "chosen": None},
    {"name": "a value from the wrong button", "surfaceId": "ask_turn_7_1", "options": ["Personal", "Work"], "action": action("choose", "ask_turn_7_1", "option0", {"value": "Work"})["action"], "chosen": None},
]

# --- answers as task.result carries them: body.approval.allow and the raw body.approval.action (Phase 3)
wire = [
    {"name": "allow with the approve tap", "allow": True, "surfaceId": "appr_tsk_42_1", "action": action(), "allowed": True},
    {"name": "allow without an action (a client without A2UI)", "allow": True, "surfaceId": "appr_tsk_42_1", "action": None, "allowed": True},
    {"name": "deny without an action", "allow": False, "surfaceId": "appr_tsk_42_1", "action": None, "allowed": False},
    {"name": "allow with a deny tap", "allow": True, "surfaceId": "appr_tsk_42_1", "action": action("deny", source="deny"), "allowed": False},
    {"name": "allow with a tap on another surface", "allow": True, "surfaceId": "appr_tsk_42_2", "action": action(), "allowed": False},
    {"name": "allow with an error instead of a tap", "allow": True, "surfaceId": "appr_tsk_42_1", "action": {"version": V, "error": {"code": "UNSUPPORTED", "surfaceId": "appr_tsk_42_1", "message": "no"}}, "allowed": False},
    {"name": "allow with a malformed action", "allow": True, "surfaceId": "appr_tsk_42_1", "action": {"version": V, "action": {"name": "approve"}}, "allowed": False},
    {"name": "allow with an action that is not an object", "allow": True, "surfaceId": "appr_tsk_42_1", "action": "approve", "allowed": False},
]

replies = {"version": V, "cases": reply_cases, "approvalAnswers": agreement, "wireAnswers": wire, "choiceAnswers": chosen}



def main() -> int:
    outputs = {
        OUT / name: json.dumps(doc, indent=2, ensure_ascii=False) + "\n"
        for name, doc in (("surfaces.json", surfaces), ("replies.json", replies), ("builders.json", builders))
    }
    if "--check" in sys.argv:
        stale = [path for path, text in outputs.items() if not path.exists() or path.read_text(encoding="utf-8") != text]
        for path in stale:
            print(f"{path.relative_to(ROOT)} is stale; run tools/scripts/build-a2ui-fixtures.py", file=sys.stderr)
        if stale:
            return 1
        print(f"A2UI fixtures are current ({len(cases)} surface cases, {len(received_cases)} received cards)")
        return 0
    OUT.mkdir(parents=True, exist_ok=True)
    for path, text in outputs.items():
        path.write_text(text, encoding="utf-8", newline="\n")
        print(f"wrote {path.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
