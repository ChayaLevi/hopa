using System.IO;
using System.Text.Json.Nodes;
using Hopa.Windows.Cards;
using Xunit;

namespace Hopa.Windows.Tests;

/// <summary>
/// Replays <c>spec/fixtures/a2ui/</c>, the cases every HOPA renderer must agree
/// on (ADR-0031). The Kotlin core replays the same files, so a card the phone
/// refuses is refused here with the same code, path and reason.
/// </summary>
public sealed class A2uiFixtureTests
{
    [Fact]
    public void EverySurfaceCaseIsAcceptedOrRefusedAsTheFixtureSays()
    {
        var cases = Fixture("surfaces.json")["cases"]!.AsArray();
        Assert.True(cases.Count >= 30);
        foreach (var item in cases)
        {
            var c = item!.AsObject();
            var name = c["name"]!.GetValue<string>();
            var outcome = new A2uiSession().ApplyAll(c["messages"]!.AsArray().Select(m => m!.AsObject()));
            if (c["ok"]!.GetValue<bool>())
            {
                var expected = c["state"]!.GetValue<string>() switch
                {
                    "ready" => typeof(A2uiOutcome.Ready),
                    "pending" => typeof(A2uiOutcome.Pending),
                    "deleted" => typeof(A2uiOutcome.Deleted),
                    var other => throw new InvalidOperationException($"{name}: unknown state {other}"),
                };
                Assert.True(outcome.GetType() == expected, $"{name}: {outcome}");
                if (c["expect"]?["text"] is JsonObject texts)
                {
                    var surface = ((A2uiOutcome.Ready)outcome).Surface;
                    foreach (var (id, text) in texts)
                    {
                        var component = (A2uiComponent.Text)surface.Component(id)!;
                        Assert.True(text!.GetValue<string>() == surface.Text(component.Value), $"{name}: {id}");
                    }
                }
            }
            else
            {
                var refused = Assert.IsType<A2uiOutcome.Refused>(outcome);
                var error = c["error"]!.AsObject();
                Assert.True(
                    error["code"]!.GetValue<string>() == refused.Problem.Code &&
                    error["path"]!.GetValue<string>() == refused.Problem.Path,
                    $"{name}: expected {error["code"]} at {error["path"]}, got {refused.Problem}");
            }
        }
    }

    [Fact]
    public void ApprovalRulesHoldOnTopOfA2ui()
    {
        foreach (var item in Fixture("surfaces.json")["approvalCases"]!.AsArray())
        {
            var c = item!.AsObject();
            var name = c["name"]!.GetValue<string>();
            var outcome = new A2uiSession().ApplyAll(c["messages"]!.AsArray().Select(m => m!.AsObject()));
            var surface = Assert.IsType<A2uiOutcome.Ready>(outcome).Surface;
            var problem = ApprovalCards.Problem(surface, c["isPayment"]!.GetValue<bool>());
            Assert.True(c["reason"]?.GetValue<string>() == problem, $"{name}: {problem}");
            Assert.True(c["ok"]!.GetValue<bool>() == (problem == null), name);
        }
    }

    [Fact]
    public void AReceivedCardIsShownOnlyWhenItShowsTheRequest()
    {
        var cases = Fixture("surfaces.json")["receivedCases"]!.AsArray();
        Assert.True(cases.Count >= 20);
        foreach (var item in cases)
        {
            var c = item!.AsObject();
            var name = c["name"]!.GetValue<string>();
            var card = ApprovalCards.Receive(c["a2ui"], CardRequest.From(c["request"]!.AsObject()));
            if (c["shown"]!.GetValue<bool>())
            {
                Assert.True(card is ApprovalCard.Shown, $"{name}: {card}");
            }
            else
            {
                var refused = Assert.IsType<ApprovalCard.Refused>(card);
                Assert.True(c["reason"]!.GetValue<string>() == refused.Reason, $"{name}: {refused.Reason}");
            }
        }
    }

    [Fact]
    public void EveryBuiltCardPassesTheApprovalRules()
    {
        foreach (var item in Fixture("builders.json")["approvals"]!.AsArray())
        {
            var c = item!.AsObject();
            var card = ApprovalCards.Receive(c["messages"], CardRequest.From(c["request"]!.AsObject()));
            Assert.True(card is ApprovalCard.Shown, $"{c["name"]}: {card}");
        }
    }

    internal static JsonObject Fixture(string name) =>
        JsonNode.Parse(File.ReadAllText(Path.Combine(SpecDir(), "fixtures", "a2ui", name)))!.AsObject();

    /// <summary>The repo's <c>spec/</c>, found by walking up from the test binaries.</summary>
    private static string SpecDir()
    {
        for (var dir = new DirectoryInfo(AppContext.BaseDirectory); dir != null; dir = dir.Parent)
        {
            var spec = Path.Combine(dir.FullName, "spec", "fixtures", "a2ui");
            if (Directory.Exists(spec))
            {
                return Path.Combine(dir.FullName, "spec");
            }
        }

        throw new DirectoryNotFoundException("spec/fixtures/a2ui not found above " + AppContext.BaseDirectory);
    }
}
