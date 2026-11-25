using System.IO;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Hopa.Windows.Ui.Dialogs;
using Xunit;

namespace Hopa.Windows.Tests;

/// <summary>
/// The PC's approval window is drawn from the sidecar's card (ADR-0027). A card
/// that is missing or doesn't show the request is denied without a window; a
/// payment's Allow waits for the acknowledgement.
/// </summary>
public sealed class ApprovalWindowTests
{
    [Fact]
    public void APaymentWaitsForTheAcknowledgementBeforeAllow() => OnUiThread(() =>
    {
        var window = ApprovalWindow.For(Question("a payment with every detail"), _ => { })!;
        Assert.False(window.CanAllow);
        window.Acknowledge(true);
        Assert.True(window.CanAllow);
        window.Acknowledge(false);
        Assert.False(window.CanAllow);
        Render(window, "approval-payment");
    });

    [Fact]
    public void APlainApprovalCanBeAllowedAtOnce() => OnUiThread(() =>
    {
        var window = ApprovalWindow.For(Question("a non-payment with an amount shows plain lines"), _ => { })!;
        Assert.True(window.CanAllow);
        Render(window, "approval-plain");
    });

    [Fact]
    public void ACardThatShowsAnotherAmountIsDeniedWithoutAWindow() => OnUiThread(() =>
    {
        var message = Question("a payment with every detail");
        message["amount"] = "1240.00";
        var log = new List<string>();
        Assert.Null(ApprovalWindow.For(message, log.Add));
        Assert.Contains("amount", Assert.Single(log));
    });

    [Fact]
    public void AQuestionWithoutACardIsDeniedWithoutAWindow() => OnUiThread(() =>
    {
        var message = Question("a plain approval");
        message.Remove("relay");
        var log = new List<string>();
        Assert.Null(ApprovalWindow.For(message, log.Add));
        Assert.Single(log);
    });

    /// <summary>A golden card from <c>builders.json</c>, as the sidecar sends it: typed fields plus the relay.</summary>
    private static JsonObject Question(string name)
    {
        var golden = A2uiFixtureTests.Fixture("builders.json")["approvals"]!.AsArray()
            .Select(item => item!.AsObject())
            .Single(item => item["name"]!.GetValue<string>() == name);
        var request = golden["request"]!.AsObject();
        return new JsonObject
        {
            ["type"] = "approval.request",
            ["id"] = "ap-1",
            ["title"] = request["title"]!.DeepClone(),
            ["body"] = request["body"]!.DeepClone(),
            ["amount"] = request["amount"]!.DeepClone(),
            ["merchant"] = request["merchant"]!.DeepClone(),
            ["consequence"] = request["consequence"]!.DeepClone(),
            ["isPayment"] = request["isPayment"]!.DeepClone(),
            ["action"] = request["action"]!["kind"]!.DeepClone(),
            ["relay"] = new JsonObject { ["a2ui"] = golden["messages"]!.DeepClone() },
        };
    }

    /// <summary>Set HOPA_RENDER_DIR to look at what the window draws; nothing is written otherwise.</summary>
    private static void Render(Window window, string name)
    {
        var directory = Environment.GetEnvironmentVariable("HOPA_RENDER_DIR");
        if (string.IsNullOrEmpty(directory) || window.Content is not FrameworkElement content)
        {
            return;
        }

        content.Measure(new Size(window.Width, double.PositiveInfinity));
        content.Arrange(new Rect(0, 0, window.Width, content.DesiredSize.Height));
        var bitmap = new RenderTargetBitmap(
            (int)Math.Ceiling(window.Width),
            (int)Math.Ceiling(content.DesiredSize.Height),
            96,
            96,
            PixelFormats.Pbgra32);
        var frame = new DrawingVisual();
        using (var context = frame.RenderOpen())
        {
            context.DrawRectangle(window.Background, null, new Rect(0, 0, bitmap.Width, bitmap.Height));
        }

        bitmap.Render(frame);
        bitmap.Render(content);
        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(bitmap));
        Directory.CreateDirectory(directory);
        using var file = File.Create(Path.Combine(directory, name + ".png"));
        encoder.Save(file);
    }

    private static void OnUiThread(Action test)
    {
        Exception? failure = null;
        var thread = new Thread(() =>
        {
            try
            {
                test();
            }
            catch (Exception error)
            {
                failure = error;
            }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        if (failure != null)
        {
            throw new Xunit.Sdk.XunitException(failure.ToString());
        }
    }
}
