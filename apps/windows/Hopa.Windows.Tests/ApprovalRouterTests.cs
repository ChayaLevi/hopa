using System.Text.Json.Nodes;
using Hopa.Windows.Integrations.Gateway;
using Hopa.Windows.Host;
using Xunit;

namespace Hopa.Windows.Tests;

/// <summary>Approval is a safety path: silence, a failed link and a refusal all deny.</summary>
public sealed class ApprovalRouterTests
{
    [Fact]
    public async Task AutoAllowBypassesThePhoneAndTheDialog()
    {
        var phone = new FakePhone { IsLive = true, PhoneDeviceId = "p1" };
        var replies = new List<(string Id, bool Allow, string Source)>();
        var asked = 0;
        var router = new ApprovalRouter(
            askUser: _ =>
            {
                asked++;
                return false;
            },
            phone: () => phone,
            reply: (id, allow, source, _) => replies.Add((id, allow, source)),
            log: _ => { },
            autoApproval: "allow");

        await router.HandleAsync(Message());

        Assert.Equal(0, asked);
        Assert.Equal(0, phone.Calls);
        var reply = Assert.Single(replies);
        Assert.True(reply.Allow);
        Assert.Equal("pc", reply.Source);
    }

    [Fact]
    public async Task WithoutAPhoneThePersonAtThePcDecides()
    {
        var replies = new List<(string Id, bool Allow, string Source)>();
        var router = new ApprovalRouter(
            askUser: _ => true,
            phone: () => null,
            reply: (id, allow, source, _) => replies.Add((id, allow, source)),
            log: _ => { },
            autoApproval: null);

        await router.HandleAsync(Message());

        var reply = Assert.Single(replies);
        Assert.True(reply.Allow);
        Assert.Equal("pc", reply.Source);
    }

    [Fact]
    public async Task APhoneAllowIsAttributedToThePhone()
    {
        var phone = new FakePhone
        {
            IsLive = true,
            PhoneDeviceId = "p1",
            Result = Approval(allow: true),
        };
        var replies = new List<(string Id, bool Allow, string Source)>();
        var router = new ApprovalRouter(
            askUser: _ => false,
            phone: () => phone,
            reply: (id, allow, source, _) => replies.Add((id, allow, source)),
            log: _ => { },
            autoApproval: null);

        await router.HandleAsync(Message());

        Assert.Equal(1, phone.Calls);
        var reply = Assert.Single(replies);
        Assert.True(reply.Allow);
        Assert.Equal("phone", reply.Source);
    }

    [Fact]
    public async Task PhoneSilenceAndFailureBothDenyOnThePc()
    {
        foreach (var phone in new[]
                 {
                     new FakePhone { IsLive = true, PhoneDeviceId = "p1", Result = null },
                     new FakePhone
                     {
                         IsLive = true,
                         PhoneDeviceId = "p1",
                         Failure = new InvalidOperationException("link down"),
                     },
                 })
        {
            var replies = new List<(string Id, bool Allow, string Source)>();
            var router = new ApprovalRouter(
                askUser: _ => true,
                phone: () => phone,
                reply: (id, allow, source, _) => replies.Add((id, allow, source)),
                log: _ => { },
                autoApproval: null);

            await router.HandleAsync(Message());

            var reply = Assert.Single(replies);
            Assert.False(reply.Allow);
            Assert.Equal("pc", reply.Source);
        }
    }

    [Fact]
    public void ThePhonePayloadKeepsTheFieldsTheDialogShows()
    {
        var payload = ApprovalRouter.BuildApproval(new JsonObject
        {
            ["title"] = "Pay the bill?",
            ["body"] = "The bank app is open.",
            ["amount"] = "42.00",
            ["merchant"] = "CityNet",
            ["consequence"] = "It cannot be taken back.",
            ["isPayment"] = true,
            ["action"] = "ui.tap",
        });

        Assert.Equal("Pay the bill?", payload["title"]!.GetValue<string>());
        Assert.Equal("42.00", payload["amount"]!.GetValue<string>());
        Assert.True(payload["isPayment"]!.GetValue<bool>());
        var action = payload["action"]!.AsObject();
        Assert.Equal("ui.tap", action["kind"]!.GetValue<string>());
        Assert.Equal(3000, action["timeoutMs"]!.GetValue<int>());
    }

    [Fact]
    public async Task TheApprovalTitleNeverEntersTheLog()
    {
        var logs = new List<string>();
        var router = new ApprovalRouter(
            askUser: _ => true,
            phone: () => null,
            reply: (_, _, _, _) => { },
            log: logs.Add,
            autoApproval: null);

        await router.HandleAsync(new JsonObject
        {
            ["id"] = "a1",
            ["title"] = "Pay hunter2 invoice",
            ["action"] = "ui.tap",
        });

        Assert.Contains(logs, line => line.StartsWith("approval:", StringComparison.Ordinal));
        Assert.DoesNotContain(logs, line => line.Contains("hunter2", StringComparison.Ordinal));
    }

    [Fact]
    public async Task TheCardGoesToThePhoneAndTheTapComesBackUnchanged()
    {
        var tap = new JsonObject
        {
            ["version"] = "1.0",
            ["action"] = new JsonObject { ["name"] = "approve", ["surfaceId"] = "appr_ap_1_x_1" },
        };
        var result = Approval(allow: true);
        result["body"]!["approval"]!["action"] = tap.DeepClone();
        var phone = new FakePhone { IsLive = true, PhoneDeviceId = "p1", Result = result };
        var replies = new List<JsonObject?>();
        var router = new ApprovalRouter(
            askUser: _ => false,
            phone: () => phone,
            reply: (_, _, _, action) => replies.Add(action),
            log: _ => { },
            autoApproval: null);
        var message = Message();
        message["a2ui"] = new JsonArray(new JsonObject { ["version"] = "1.0" });

        await router.HandleAsync(message);

        Assert.True(JsonNode.DeepEquals(message["a2ui"], phone.Card), "the card is copied, not rebuilt");
        Assert.True(JsonNode.DeepEquals(tap, Assert.Single(replies)), "the sidecar gets the tap to check");
    }

    [Fact]
    public async Task WithoutACardOrATapNothingExtraTravels()
    {
        var phone = new FakePhone { IsLive = true, PhoneDeviceId = "p1", Result = Approval(allow: true) };
        var replies = new List<JsonObject?>();
        var router = new ApprovalRouter(
            askUser: _ => false,
            phone: () => phone,
            reply: (_, _, _, action) => replies.Add(action),
            log: _ => { },
            autoApproval: null);

        await router.HandleAsync(Message());

        Assert.Null(phone.Card);
        Assert.Null(Assert.Single(replies));
    }

    [Fact]
    public void ApprovalAllowedNeedsAnExplicitTrue()
    {
        Assert.True(ApprovalRouter.ApprovalAllowed(Approval(allow: true)));
        Assert.False(ApprovalRouter.ApprovalAllowed(Approval(allow: false)));
        Assert.False(ApprovalRouter.ApprovalAllowed(null));
    }

    private static JsonObject Message() => new()
    {
        ["id"] = "a1",
        ["title"] = "Pay the bill?",
        ["action"] = "ui.tap",
    };

    private static JsonObject Approval(bool allow) => new()
    {
        ["body"] = new JsonObject
        {
            ["approval"] = new JsonObject { ["allow"] = allow },
        },
    };

    private sealed class FakePhone : IApprovalPhone
    {
        public bool IsLive { get; set; }

        public string PhoneDeviceId { get; set; } = "";

        public JsonObject? Result { get; set; }

        public Exception? Failure { get; set; }

        public int Calls { get; private set; }

        public JsonArray? Card { get; private set; }

        public Task<JsonObject?> RequestApprovalAsync(
            JsonObject approval,
            string phoneDeviceId,
            TimeSpan timeout,
            JsonArray? a2ui = null)
        {
            Calls++;
            Card = a2ui;
            return Failure != null
                ? Task.FromException<JsonObject?>(Failure)
                : Task.FromResult(Result);
        }
    }
}
