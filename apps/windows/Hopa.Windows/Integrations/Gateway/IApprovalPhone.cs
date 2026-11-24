using System.Text.Json.Nodes;

namespace Hopa.Windows.Integrations.Gateway;

/// <summary>The phone half of approval, as the router needs it.</summary>
public interface IApprovalPhone
{
    bool IsLive { get; }

    string PhoneDeviceId { get; }

    /// <summary>
    /// Asks the phone. <paramref name="a2ui"/> is the sidecar's approval card
    /// (ADR-0027), sent beside the typed approval; null sends none.
    /// </summary>
    Task<JsonObject?> RequestApprovalAsync(
        JsonObject approval,
        string phoneDeviceId,
        TimeSpan timeout,
        JsonArray? a2ui = null);
}
