using System.Windows;
using System.Windows.Automation;
using System.Windows.Controls;
using System.Windows.Media;
using Hopa.Windows.Cards;

namespace Hopa.Windows.Ui.Dialogs;

/// <summary>
/// Draws the HOPA catalog in WPF (ADR-0027). It only ever receives a surface
/// that passed <see cref="ApprovalCards.Receive"/>. Text is plain text, never
/// markup, and an icon is one of a fixed set of Segoe glyphs.
/// </summary>
internal static class CardView
{
    public static readonly Brush TextPrimary = Hex("#1C1B18");
    public static readonly Brush TextSecondary = Hex("#6B665C");
    public static readonly Brush TextHint = Hex("#9A9488");
    public static readonly Brush Warning = Hex("#B26A00");
    public static readonly Brush Danger = Hex("#B3261E");
    public static readonly Brush DangerSoft = Hex("#FBE6E4");
    public static readonly Brush Primary = Hex("#0A59F7");

    private static readonly Dictionary<string, string> Glyphs = new()
    {
        ["check"] = "",
        ["close"] = "",
        ["error"] = "",
        ["help"] = "",
        ["info"] = "",
        ["lock"] = "",
        ["payment"] = "",
        ["send"] = "",
        ["shoppingCart"] = "",
        ["warning"] = "",
    };

    /// <summary>One component and everything it contains.</summary>
    public static UIElement Component(A2uiSurface surface, A2uiComponent component)
    {
        UIElement element = component switch
        {
            A2uiComponent.Text text => Line(
                surface.Text(text.Value),
                text.Variant == "caption" ? 10.5 : 12.5,
                text.Variant == "caption" ? TextHint : TextSecondary),
            A2uiComponent.Row row => Stack(surface, row.Children, Orientation.Horizontal, row.Justify),
            A2uiComponent.Column column => Stack(surface, column.Children, Orientation.Vertical, column.Justify),
            A2uiComponent.Card card => new Border { Child = Component(surface, surface.Components[card.Child]) },
            A2uiComponent.Divider divider => new Separator
            {
                Margin = new Thickness(0, 4, 0, 4),
                LayoutTransform = divider.Axis == "vertical" ? new RotateTransform(90) : Transform.Identity,
            },
            A2uiComponent.Icon icon => new TextBlock
            {
                Text = Glyphs[icon.Name],
                FontFamily = new FontFamily("Segoe MDL2 Assets"),
                FontSize = 14,
                Foreground = TextSecondary,
                VerticalAlignment = VerticalAlignment.Center,
            },
            A2uiComponent.PaymentNotice notice => Notice(surface, notice),
            // Approval buttons are the window's own; a button anywhere else is shown, never pressable.
            A2uiComponent.Button button => Component(surface, surface.Components[button.Child]),
            _ => new TextBlock(),
        };
        Accessibility(surface, component, element);
        return element;
    }

    /// <summary>The payment block: amount, merchant and consequence, like the old window's lines.</summary>
    public static UIElement Notice(A2uiSurface surface, A2uiComponent.PaymentNotice notice)
    {
        var panel = new StackPanel();
        panel.Children.Add(Line("Amount: " + surface.Text(notice.Amount), 12.5, TextPrimary));
        if (notice.Merchant != null && surface.Text(notice.Merchant).Length > 0)
        {
            panel.Children.Add(Line("Merchant: " + surface.Text(notice.Merchant), 12.5, TextSecondary));
        }

        if (notice.Consequence != null && surface.Text(notice.Consequence).Length > 0)
        {
            panel.Children.Add(Line(surface.Text(notice.Consequence), 11.5, Warning));
        }

        return panel;
    }

    public static TextBlock Line(string text, double size, Brush color) => new()
    {
        Text = text,
        FontSize = size,
        Foreground = color,
        TextWrapping = TextWrapping.Wrap,
        Margin = new Thickness(0, 0, 0, 6),
    };

    private static StackPanel Stack(A2uiSurface surface, IEnumerable<string> children, Orientation orientation, string? justify)
    {
        var panel = new StackPanel
        {
            Orientation = orientation,
            HorizontalAlignment = orientation == Orientation.Horizontal
                ? justify switch
                {
                    "end" => HorizontalAlignment.Right,
                    "center" => HorizontalAlignment.Center,
                    _ => HorizontalAlignment.Left,
                }
                : HorizontalAlignment.Stretch,
        };
        foreach (var id in children)
        {
            var child = Component(surface, surface.Components[id]);
            if (orientation == Orientation.Horizontal && child is FrameworkElement framed)
            {
                framed.Margin = new Thickness(0, 0, 8, 0);
            }

            panel.Children.Add(child);
        }

        return panel;
    }

    private static void Accessibility(A2uiSurface surface, A2uiComponent component, UIElement element)
    {
        if (component.Accessibility?.Label is { } label)
        {
            AutomationProperties.SetName(element, surface.Text(label));
        }

        if (component.Accessibility?.Description is { } description)
        {
            AutomationProperties.SetHelpText(element, surface.Text(description));
        }
    }

    private static SolidColorBrush Hex(string hex)
    {
        var brush = (SolidColorBrush)new BrushConverter().ConvertFromString(hex)!;
        brush.Freeze();
        return brush;
    }
}
