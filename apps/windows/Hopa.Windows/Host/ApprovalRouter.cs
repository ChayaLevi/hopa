using System.Text.Json.Nodes;
using Hopa.Windows.Integrations.Gateway;

namespace Hopa.Windows.Host;

/// <summary>
/// Where one approval goes: the automation override, the paired phone, or the
/// person at this PC. The failure of the phone link is a deny, never an allow.
/// ADR-0027: the sidecar's A2UI card goes to the phone unchanged, and the
/// phone's tap comes back to the sidecar unchanged. The sidecar checks the
/// tap; this router never judges a card.
/// </summary>
public sealed class ApprovalRouter
{
    private static readonly TimeSpan PhoneTimeout = TimeSpan.FromSeconds(100);

    private readonly Func<JsonObject, bool> askUser;
    private readonly Func<IApprovalPhone?> phone;
    private readonly Action<string, bool, string, JsonObject?> reply;
    private readonly Action<string> log;
    private readonly string? autoApproval;

    public ApprovalRouter(
        Func<JsonObject, bool> askUser,
        Func<IApprovalPhone?> phone,
        Action<string, bool, string, JsonObject?> reply,
        Action<string> log,
        string? autoApproval)
    {
        this.askUser = askUser;
        this.phone = phone;
        this.reply = reply;
        this.log = log;
        this.autoApproval = autoApproval;
    }

    public async Task HandleAsync(JsonObject message)
    {
        var id = Text(message, "id");

        if (autoApproval != null)
        {
            var allowed = autoApproval == "allow";
            reply(id, allowed, "pc", null);
            log($"approval: auto-{(allowed ? "allow" : "deny")}");
            return;
        }

        var live = phone();
        if (live is { IsLive: true } && live.PhoneDeviceId.Length > 0)
        {
            await AskPhoneAsync(live, message, id);
            return;
        }

        var allow = askUser(message);
        reply(id, allow, "pc", null);
        log($"approval: {(allow ? "allow" : "deny")}");
    }

    private async Task AskPhoneAsync(
        IApprovalPhone live,
        JsonObject message,
        string id)
    {
        var allow = false;
        var source = "pc";
        JsonObject? tap = null;
        try
        {
            var result = await live.RequestApprovalAsync(
                BuildApproval(message),
                live.PhoneDeviceId,
                PhoneTimeout,
                Card(message));
            allow = ApprovalAllowed(result);
            tap = ApprovalAction(result);
            source = result == null ? "pc" : "phone";
            if (result == null)
            {
                log("approval: the phone did not answer; deny");
            }
        }
        catch (Exception error)
        {
            log($"approval: the phone link failed ({error.Message}); deny");
        }
        finally
        {
            reply(id, allow, source, tap);
            log($"approval: {(allow ? "allow" : "deny")} from {source}");
        }
    }

    /// <summary>The approval body the phone receives: the same fields the dialog shows.</summary>
    public static JsonObject BuildApproval(JsonObject message) => new()
    {
        ["title"] = message["title"]?.GetValue<string>() ?? "Allow this action?",
        ["body"] = message["body"]?.GetValue<string>() ?? "",
        ["amount"] = message["amount"]?.GetValue<string>() ?? "",
        ["merchant"] = message["merchant"]?.GetValue<string>() ?? "",
        ["consequence"] = message["consequence"]?.GetValue<string>() ?? "",
        ["isPayment"] = message["isPayment"]?.GetValue<bool>() ?? false,
        ["action"] = new JsonObject
        {
            ["schemaVersion"] = "1",
            ["kind"] = message["action"]?.GetValue<string>() ?? "ui.tap",
            ["timeoutMs"] = 3000,
        },
    };

    /// <summary>The sidecar's A2UI card for this question, copied as it came; null when it sent none.</summary>
    public static JsonArray? Card(JsonObject message) =>
        message["a2ui"] is JsonArray card ? card.DeepClone().AsArray() : null;

    /// <summary>The phone's A2UI tap, copied as it came, for the sidecar to check; null when it sent none.</summary>
    public static JsonObject? ApprovalAction(JsonObject? result) =>
        (result?["body"] as JsonObject)?["approval"] is JsonObject approval &&
        approval["action"] is JsonObject action
            ? action.DeepClone().AsObject()
            : null;

    public static bool ApprovalAllowed(JsonObject? result) =>
        (result?["body"] as JsonObject)?["approval"] is JsonObject approval &&
        (approval["allow"]?.GetValue<bool>() ?? false);

    private static string Text(JsonObject message, string key) =>
        message[key]?.GetValue<string>() ?? "";
}
