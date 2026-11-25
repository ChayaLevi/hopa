using System.Text.Json.Nodes;
using System.Windows;
using Hopa.Windows.Cards;

namespace Hopa.Windows.Ui.Dialogs;

/// <summary>
/// The approval question on this PC, drawn from the sidecar's A2UI card
/// (ADR-0027) after the same checks the phone runs. Allow is only a press of
/// the card's approve button; a payment's Allow stays disabled until its
/// notice is acknowledged; closing the window is deny. A request whose card
/// is missing or fails a check is denied without being shown.
/// </summary>
public partial class ApprovalWindow : Window
{
    private readonly bool payment;

    internal ApprovalWindow(A2uiSurface surface, ApprovalLayout layout)
    {
        InitializeComponent();
        payment = layout.Notice != null;
        TitleLabel.Text = surface.Text(layout.Title.Value);
        foreach (var component in layout.Body)
        {
            BodyPanel.Children.Add(CardView.Component(surface, component));
        }

        DenyButton.Content = Label(surface, layout.Deny);
        AllowButton.Content = Label(surface, layout.Approve);
        if (layout.Notice is { } notice)
        {
            PaymentBadge.Visibility = Visibility.Visible;
            AcknowledgeBox.Content = surface.Text(notice.Acknowledge);
            AcknowledgeBox.Visibility = Visibility.Visible;
        }

        Refresh();
    }

    /// <summary>
    /// Shows the question the sidecar asked, or denies it without showing
    /// anything when its card is missing or doesn't pass. True only for Allow.
    /// </summary>
    public static bool Ask(JsonObject message, Window owner, Action<string> log)
    {
        var window = For(message, log);
        if (window == null)
        {
            return false;
        }

        window.Owner = owner;
        return window.ShowDialog() == true;
    }

    /// <summary>The window for this question, or null when its card is missing or fails a check.</summary>
    internal static ApprovalWindow? For(JsonObject message, Action<string> log)
    {
        var request = CardRequest.From(message);
        switch (ApprovalCards.Receive(message["relay"]?["a2ui"], request))
        {
            case ApprovalCard.Refused refused:
                log($"approval: the card was refused ({refused.Reason}); deny");
                return null;
            case ApprovalCard.Shown shown when ApprovalLayout.Of(shown.Surface) is { } layout:
                return new ApprovalWindow(shown.Surface, layout);
            default:
                log("approval: the card does not have the approval shape; deny");
                return null;
        }
    }

    /// <summary>For tests: whether Allow can be pressed now.</summary>
    internal bool CanAllow => AllowButton.IsEnabled;

    internal void Acknowledge(bool ticked) => AcknowledgeBox.IsChecked = ticked;

    private void Refresh()
    {
        // WPF greys a disabled button out itself, so Allow reads as present but not yet pressable.
        AllowButton.IsEnabled = !payment || AcknowledgeBox.IsChecked == true;
    }

    private void Acknowledge_Changed(object sender, RoutedEventArgs e) => Refresh();

    private void Allow_Click(object sender, RoutedEventArgs e)
    {
        if (AllowButton.IsEnabled)
        {
            DialogResult = true;
        }
    }

    private void Deny_Click(object sender, RoutedEventArgs e) => DialogResult = false;

    private static object Label(A2uiSurface surface, A2uiComponent.Button button) =>
        surface.Component(button.Child) switch
        {
            A2uiComponent.Text text => surface.Text(text.Value),
            { } other => CardView.Component(surface, other),
            null => "",
        };
}
