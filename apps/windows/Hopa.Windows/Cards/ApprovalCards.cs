using System.Text.Json;
using System.Text.Json.Nodes;

namespace Hopa.Windows.Cards;

/// <summary>The typed approval a card must show: the fields a person consents to.</summary>
internal sealed record CardRequest(
    string Title,
    string Body,
    string Amount,
    string Merchant,
    string Consequence,
    bool IsPayment)
{
    /// <summary>The typed fields of the sidecar's <c>approval.request</c>.</summary>
    public static CardRequest From(JsonObject message) => new(
        Text(message, "title"),
        Text(message, "body"),
        Text(message, "amount"),
        Text(message, "merchant"),
        Text(message, "consequence"),
        message["isPayment"] is JsonValue flag && flag.GetValueKind() == JsonValueKind.True);

    private static string Text(JsonObject message, string key) =>
        message[key] is JsonValue value && value.GetValueKind() == JsonValueKind.String ? value.GetValue<string>() : "";
}

/// <summary>What <see cref="ApprovalCards.Receive"/> made of a card.</summary>
internal abstract record ApprovalCard
{
    /// <summary>Every check passed: draw this surface.</summary>
    public sealed record Shown(A2uiSurface Surface) : ApprovalCard;

    /// <summary>Not drawn; the answer is deny.</summary>
    public sealed record Refused(string Reason) : ApprovalCard;
}

/// <summary>
/// HOPA's approval-card rules on top of A2UI (ADR-0031), a port of the
/// receiving half of <c>ApprovalSurfaces.kt</c>. The reasons are the same
/// strings, so <c>spec/fixtures/a2ui/surfaces.json</c> checks both.
/// </summary>
internal static class ApprovalCards
{
    public const string Approve = "approve";
    public const string Deny = "deny";
    public const string Acknowledge = "I understand money may leave my account.";
    public const string AllowLabel = "Allow";
    public const string DenyLabel = "Deny";

    /// <summary>Only approve and deny, exactly one of each, no context, and a notice on a payment.</summary>
    public static string? Problem(A2uiSurface surface, bool isPayment)
    {
        var buttons = surface.Components.Values.OfType<A2uiComponent.Button>().ToList();
        var notices = surface.Components.Values.OfType<A2uiComponent.PaymentNotice>().Count();
        if (buttons.Any(b => b.Event.Name != Approve && b.Event.Name != Deny))
        {
            return "an approval card only has approve and deny buttons";
        }

        if (buttons.Count(b => b.Event.Name == Approve) != 1)
        {
            return "an approval card needs exactly one approve button";
        }

        if (buttons.Count(b => b.Event.Name == Deny) != 1)
        {
            return "an approval card needs exactly one deny button";
        }

        if (buttons.Any(b => b.Event.Context.Count > 0))
        {
            return "approval buttons carry no context";
        }

        return isPayment && notices != 1 ? "a payment card needs one PaymentNotice" : null;
    }

    /// <summary>
    /// Whether the card shows the typed request: the title and body, the
    /// request's money, and buttons that say what they do. Call it on a surface
    /// that already passed <see cref="Problem"/>.
    /// </summary>
    public static string? Matches(A2uiSurface surface, CardRequest request)
    {
        var parts = surface.Components.Values.ToList();
        var shown = parts.OfType<A2uiComponent.Text>().Select(t => surface.Text(t.Value)).ToList();
        var notices = parts.OfType<A2uiComponent.PaymentNotice>().ToList();
        string? content;
        if (request.Title.Length > 0 && !shown.Contains(request.Title))
        {
            content = "the card does not show the request's title";
        }
        else if (request.Body.Length > 0 && !shown.Contains(request.Body))
        {
            content = "the card does not show the request's body";
        }
        else if (request.IsPayment)
        {
            content = notices.Count == 1 ? Notice(surface, notices[0], request) : "a payment card needs one PaymentNotice";
        }
        else if (notices.Count > 0)
        {
            content = "a card that is not a payment has no PaymentNotice";
        }
        else if (request.Amount.Length > 0 && !shown.Any(s => s.Contains(request.Amount, StringComparison.Ordinal)))
        {
            content = "the card does not show the request's amount";
        }
        else if (request.Merchant.Length > 0 && !shown.Any(s => s.Contains(request.Merchant, StringComparison.Ordinal)))
        {
            content = "the card does not show the request's merchant";
        }
        else
        {
            content = null;
        }

        return content ?? ButtonProblem(surface, parts.OfType<A2uiComponent.Button>().ToList());
    }

    /// <summary>
    /// A card sent with an approval, checked before anything is drawn: the
    /// A2UI rules, then <see cref="Problem"/>, then <see cref="Matches"/>. One
    /// card is one surface.
    /// </summary>
    public static ApprovalCard Receive(JsonNode? a2ui, CardRequest request)
    {
        if (a2ui is not JsonArray array)
        {
            return new ApprovalCard.Refused("a2ui is an array of messages");
        }

        var messages = new List<JsonObject>();
        foreach (var item in array)
        {
            if (item is not JsonObject message)
            {
                return new ApprovalCard.Refused("a card message is an object");
            }

            messages.Add(message);
        }

        var session = new A2uiSession();
        A2uiOutcome outcome = new A2uiOutcome.Pending("");
        var ids = new HashSet<string>();
        foreach (var message in messages)
        {
            outcome = session.Apply(message);
            switch (outcome)
            {
                case A2uiOutcome.Refused refused:
                    return new ApprovalCard.Refused(refused.Problem.Message);
                case A2uiOutcome.Ready ready:
                    ids.Add(ready.Surface.Id);
                    break;
                case A2uiOutcome.Pending pending:
                    ids.Add(pending.SurfaceId);
                    break;
                case A2uiOutcome.Deleted deleted:
                    ids.Add(deleted.SurfaceId);
                    break;
            }
        }

        if (ids.Count > 1)
        {
            return new ApprovalCard.Refused("an approval card is one surface");
        }

        if (outcome is not A2uiOutcome.Ready { Surface: var surface })
        {
            return new ApprovalCard.Refused("the card never became complete");
        }

        var reason = Problem(surface, request.IsPayment) ?? Matches(surface, request);
        return reason == null ? new ApprovalCard.Shown(surface) : new ApprovalCard.Refused(reason);
    }

    private static string? Notice(A2uiSurface surface, A2uiComponent.PaymentNotice notice, CardRequest request)
    {
        string Shows(A2uiText? value) => value == null ? "" : surface.Text(value);

        if (Shows(notice.Amount) != request.Amount)
        {
            return "the PaymentNotice does not show the request's amount";
        }

        if (Shows(notice.Merchant) != request.Merchant)
        {
            return "the PaymentNotice does not show the request's merchant";
        }

        if (Shows(notice.Consequence) != request.Consequence)
        {
            return "the PaymentNotice does not show the request's consequence";
        }

        return Shows(notice.Acknowledge) != Acknowledge ? "the PaymentNotice asks for HOPA's own acknowledgement" : null;
    }

    private static string? ButtonProblem(A2uiSurface surface, List<A2uiComponent.Button> buttons)
    {
        string? Label(A2uiComponent.Button? button) =>
            button != null && surface.Component(button.Child) is A2uiComponent.Text text ? surface.Text(text.Value) : null;

        var approve = buttons.Where(b => b.Event.Name == Approve).ToList();
        var deny = buttons.Where(b => b.Event.Name == Deny).ToList();
        if (Label(approve.Count == 1 ? approve[0] : null) != AllowLabel)
        {
            return $"the approve button reads {AllowLabel}";
        }

        if (Label(deny.Count == 1 ? deny[0] : null) != DenyLabel)
        {
            return $"the deny button reads {DenyLabel}";
        }

        return buttons.Any(b => b.Accessibility != null || surface.Component(b.Child)?.Accessibility != null)
            ? "approval buttons carry no accessibility text of their own"
            : null;
    }
}
