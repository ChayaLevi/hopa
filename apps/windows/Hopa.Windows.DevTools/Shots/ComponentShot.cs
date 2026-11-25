using System.IO;
using System.Text.Json.Nodes;
using System.Windows;
using Hopa.Windows.Ui;
using Hopa.Windows.Ui.Dialogs;
using Hopa.Windows.Ui.Shell;

namespace Hopa.Windows.DevTools.Shots;

/// <summary>W6: the windows that only appear mid-turn, rendered for the parity table.</summary>
internal static class ComponentShot
{
    /// <summary>
    /// A payment approval as the sidecar sends it: the typed fields, and the
    /// card <c>ApprovalSurfaces.build</c> makes from them (ADR-0027).
    /// </summary>
    private static JsonObject SampleApproval()
    {
        const string title = "Pay the internet bill?";
        const string body = "The bank app is open on the payment screen.";
        const string amount = "42.00";
        const string merchant = "CityNet";
        const string consequence = "The money leaves the account and cannot be taken back.";
        var card = JsonNode.Parse("""
            {"version":"1.0","createSurface":{"surfaceId":"appr_sample_1","catalogId":"https://hopa.dev/contracts/a2ui/hopa-catalog.v1.json",
             "components":[{"id":"root","component":"Card","child":"content"},
              {"id":"content","component":"Column","children":["title","body","notice","actionLine","buttons"]},
              {"id":"title","component":"Text","text":{"@path":"/approval/title"}},
              {"id":"body","component":"Text","text":{"@path":"/approval/body"}},
              {"id":"notice","component":"PaymentNotice","amount":{"@path":"/payment/amount"},"merchant":{"@path":"/payment/merchant"},
               "consequence":{"@path":"/payment/consequence"},"acknowledge":"I understand money may leave my account."},
              {"id":"actionLine","component":"Text","text":{"@path":"/approval/actionLine"},"variant":"caption"},
              {"id":"buttons","component":"Row","children":["deny","allow"],"justify":"end"},
              {"id":"deny","component":"Button","child":"denyLabel","variant":"borderless","action":{"event":{"name":"deny"}}},
              {"id":"denyLabel","component":"Text","text":"Deny"},
              {"id":"allow","component":"Button","child":"allowLabel","variant":"primary","action":{"event":{"name":"approve"}}},
              {"id":"allowLabel","component":"Text","text":"Allow"}]}}
            """)!.AsObject();
        card["createSurface"]!["dataModel"] = new JsonObject
        {
            ["approval"] = new JsonObject { ["title"] = title, ["body"] = body, ["actionLine"] = "Action: payment.pay" },
            ["payment"] = new JsonObject { ["amount"] = amount, ["merchant"] = merchant, ["consequence"] = consequence },
        };
        return new JsonObject
        {
            ["type"] = "approval.request",
            ["id"] = "ap-sample",
            ["title"] = title,
            ["body"] = body,
            ["amount"] = amount,
            ["merchant"] = merchant,
            ["consequence"] = consequence,
            ["isPayment"] = true,
            ["action"] = "payment.pay",
            ["relay"] = new JsonObject { ["a2ui"] = new JsonArray(card) },
        };
    }

    public static void Run(MainWindow window, string directory)
    {
        Directory.CreateDirectory(directory);

        var approval = ApprovalWindow.For(SampleApproval(), reason => throw new InvalidOperationException(reason))
            ?? throw new InvalidOperationException("the sample approval card has no approval shape");
        approval.Owner = window;
        approval.Show();
        approval.UpdateLayout();
        WindowScreenshot.Save(approval, Path.Combine(directory, "windows-w6-approval.png"));
        approval.Close();

        var steps = new RunDetailsWindow(new[]
        {
            "1 tap Payments (verified)",
            "2 tap Internet (verified)",
            "3 type 42.00 (verified)",
            "4 tap Confirm, payment, approved by the family",
        })
        {
            Owner = window,
        };
        steps.Show();
        steps.UpdateLayout();
        WindowScreenshot.Save(steps, Path.Combine(directory, "windows-w6-steps.png"));
        steps.Close();

        window.Tools.Shutdown();
    }
}
