using System.Text.Json.Nodes;

namespace Hopa.Windows.Integrations.Gateway;

/// <summary>The phone half of approval, as the router needs it.</summary>
public interface IApprovalPhone
{
    bool IsLive { get; }

    string PhoneDeviceId { get; }

    /// <summary>
    /// Asks the phone. <paramref name="relay"/> holds the sidecar's fields for
    /// the phone (the A2UI card, ADR-0031, and the challenge, ADR-0032); each
    /// is sent beside the typed approval, unchanged. Null sends none.
    /// </summary>
    Task<JsonObject?> RequestApprovalAsync(
        JsonObject approval,
        string phoneDeviceId,
        TimeSpan timeout,
        JsonObject? relay = null);
}
